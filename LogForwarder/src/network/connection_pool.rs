use crate::network::{IndexerConfig, NetworkError, TlsConfig};
use dashmap::DashMap;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use tracing::{debug, warn};

#[derive(Clone, Debug)]
pub struct ConnectionPoolConfig {
    pub min_connections_per_indexer: usize,
    pub max_connections_per_indexer: usize,
    pub connection_timeout_ms: u64,
    pub keepalive_interval_ms: u64,
    pub idle_timeout_ms: u64,
}

impl Default for ConnectionPoolConfig {
    fn default() -> Self {
        ConnectionPoolConfig {
            min_connections_per_indexer: 2,
            max_connections_per_indexer: 20,
            connection_timeout_ms: 10000,
            keepalive_interval_ms: 30000,
            idle_timeout_ms: 300000,
        }
    }
}

#[allow(dead_code)]
pub struct Connection {
    id: String,
    indexer_id: String,
    channel: Option<tonic::transport::Channel>,
    created_at: std::time::Instant,
    last_used_at: Arc<parking_lot::Mutex<std::time::Instant>>,
    is_healthy: Arc<std::sync::atomic::AtomicBool>,
}

impl Connection {
    async fn new(
        id: String,
        indexer_id: String,
        config: &IndexerConfig,
        tls_config: Option<&TlsConfig>,
    ) -> Result<Self, NetworkError> {
        let endpoint = format!("http://{}:{}", config.host, config.port);

        let channel = if let Some(tls) = tls_config {
            if tls.enabled {
                let endpoint = tonic::transport::Endpoint::try_from(endpoint)
                    .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
                    .tcp_nodelay(true)
                    .http2_keep_alive_interval(std::time::Duration::from_secs(30));

                endpoint
                    .connect()
                    .await
                    .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
            } else {
                tonic::transport::Endpoint::try_from(endpoint)
                    .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
                    .tcp_nodelay(true)
                    .http2_keep_alive_interval(std::time::Duration::from_secs(30))
                    .connect()
                    .await
                    .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
            }
        } else {
            tonic::transport::Endpoint::try_from(endpoint)
                .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
                .tcp_nodelay(true)
                .http2_keep_alive_interval(std::time::Duration::from_secs(30))
                .connect()
                .await
                .map_err(|e| NetworkError::ConnectionError(e.to_string()))?
        };

        let now = std::time::Instant::now();
        Ok(Connection {
            id,
            indexer_id,
            channel: Some(channel),
            created_at: now,
            last_used_at: Arc::new(parking_lot::Mutex::new(now)),
            is_healthy: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        })
    }

    pub fn is_healthy(&self) -> bool {
        self.is_healthy.load(std::sync::atomic::Ordering::Relaxed)
    }

    pub fn mark_healthy(&self) {
        self.is_healthy
            .store(true, std::sync::atomic::Ordering::Release);
    }

    pub fn mark_unhealthy(&self) {
        self.is_healthy
            .store(false, std::sync::atomic::Ordering::Release);
    }

    pub fn get_channel(&self) -> Option<&tonic::transport::Channel> {
        self.channel.as_ref()
    }

    pub fn update_last_used(&self) {
        *self.last_used_at.lock() = std::time::Instant::now();
    }
}

pub struct ConnectionPool {
    indexers: Vec<IndexerConfig>,
    config: ConnectionPoolConfig,
    tls_config: Option<TlsConfig>,
    connections: Arc<DashMap<String, Arc<Connection>>>,
    connection_counter: AtomicUsize,
}

impl ConnectionPool {
    pub async fn new(
        indexers: Vec<IndexerConfig>,
        config: ConnectionPoolConfig,
        tls_config: Option<TlsConfig>,
    ) -> Result<Self, NetworkError> {
        debug!("Creating connection pool with {} indexers", indexers.len());

        if indexers.is_empty() {
            return Err(NetworkError::InvalidConfig(
                "No indexers configured".to_string(),
            ));
        }

        let pool = ConnectionPool {
            indexers,
            config,
            tls_config,
            connections: Arc::new(DashMap::new()),
            connection_counter: AtomicUsize::new(0),
        };

        pool.initialize().await?;

        Ok(pool)
    }

    async fn initialize(&self) -> Result<(), NetworkError> {
        debug!("Initializing connection pool");

        for indexer in &self.indexers {
            for i in 0..self.config.min_connections_per_indexer {
                let conn_id = format!("{}-{}", indexer.id, i);
                match Connection::new(
                    conn_id.clone(),
                    indexer.id.clone(),
                    indexer,
                    self.tls_config.as_ref(),
                )
                .await
                {
                    Ok(conn) => {
                        self.connections.insert(conn_id, Arc::new(conn));
                        debug!("Created initial connection: {}", indexer.id);
                    }
                    Err(e) => {
                        warn!(
                            "Failed to create initial connection for {}: {}",
                            indexer.id, e
                        );
                    }
                }
            }
        }

        Ok(())
    }

    pub async fn get_connection(&self, indexer_id: &str) -> Result<Arc<Connection>, NetworkError> {
        for indexer in &self.indexers {
            if indexer.id == indexer_id {
                let mut attempts = 0;
                loop {
                    let key = format!("{}-{}", indexer_id, attempts);
                    if let Some(conn) = self.connections.get(&key) {
                        if conn.is_healthy() {
                            conn.update_last_used();
                            return Ok(Arc::clone(conn.value()));
                        }
                    }

                    attempts += 1;
                    if attempts >= self.config.max_connections_per_indexer {
                        break;
                    }
                }

                let new_conn_id = format!(
                    "{}-{}",
                    indexer_id,
                    self.connection_counter.fetch_add(1, Ordering::Relaxed)
                );
                let conn = Connection::new(
                    new_conn_id.clone(),
                    indexer_id.to_string(),
                    indexer,
                    self.tls_config.as_ref(),
                )
                .await?;

                let arc_conn = Arc::new(conn);
                self.connections.insert(new_conn_id, Arc::clone(&arc_conn));
                return Ok(arc_conn);
            }
        }

        Err(NetworkError::ConnectionError(format!(
            "Indexer {} not found",
            indexer_id
        )))
    }

    pub async fn health_check(&self, indexer_id: &str) -> Result<bool, NetworkError> {
        match self.get_connection(indexer_id).await {
            Ok(conn) => {
                if conn.get_channel().is_some() {
                    conn.mark_healthy();
                    Ok(true)
                } else {
                    conn.mark_unhealthy();
                    Ok(false)
                }
            }
            Err(e) => Err(e),
        }
    }

    pub async fn get_all_indexer_ids(&self) -> Vec<String> {
        self.indexers.iter().map(|i| i.id.clone()).collect()
    }

    pub fn get_connection_stats(&self) -> ConnectionPoolStats {
        let mut stats = ConnectionPoolStats {
            total_connections: self.connections.len(),
            healthy_connections: 0,
            unhealthy_connections: 0,
            connections_per_indexer: Default::default(),
        };

        for entry in self.connections.iter() {
            let conn = entry.value();
            if conn.is_healthy() {
                stats.healthy_connections += 1;
            } else {
                stats.unhealthy_connections += 1;
            }

            let indexer_id = &conn.indexer_id;
            *stats
                .connections_per_indexer
                .entry(indexer_id.clone())
                .or_insert(0) += 1;
        }

        stats
    }
}

#[derive(Debug, Clone)]
pub struct ConnectionPoolStats {
    pub total_connections: usize,
    pub healthy_connections: usize,
    pub unhealthy_connections: usize,
    pub connections_per_indexer: std::collections::HashMap<String, usize>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_connection_pool_creation() {
        let indexers = vec![IndexerConfig {
            id: "indexer1".to_string(),
            host: "localhost".to_string(),
            port: 50051,
            weight: 100,
        }];

        let config = ConnectionPoolConfig::default();
        let pool = ConnectionPool::new(indexers, config, None).await;

        assert!(pool.is_ok());
    }

    #[tokio::test]
    async fn test_connection_pool_stats() {
        let indexers = vec![];
        let config = ConnectionPoolConfig::default();
        let pool = ConnectionPool::new(indexers, config, None)
            .await
            .unwrap_or_else(|_| ConnectionPool {
                indexers: vec![],
                config: ConnectionPoolConfig::default(),
                tls_config: None,
                connections: Arc::new(DashMap::new()),
                connection_counter: AtomicUsize::new(0),
            });

        let stats = pool.get_connection_stats();
        assert_eq!(stats.total_connections, 0);
    }
}
