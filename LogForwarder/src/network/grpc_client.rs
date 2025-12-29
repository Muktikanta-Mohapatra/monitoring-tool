use crate::network::{CircuitBreaker, ConnectionPool, LoadBalancer, NetworkError};
use std::sync::Arc;
use tracing::{debug, error};

pub struct GrpcForwarderClient {
    connection_pool: Arc<ConnectionPool>,
    load_balancer: Arc<LoadBalancer>,
    circuit_breaker: Arc<CircuitBreaker>,
}

impl GrpcForwarderClient {
    pub fn new(
        connection_pool: Arc<ConnectionPool>,
        load_balancer: Arc<LoadBalancer>,
        circuit_breaker: Arc<CircuitBreaker>,
    ) -> Self {
        GrpcForwarderClient {
            connection_pool,
            load_balancer,
            circuit_breaker,
        }
    }

    pub async fn select_available_indexer(&self) -> Result<String, NetworkError> {
        let available_indexers = self.circuit_breaker.get_available_indexers();

        if available_indexers.is_empty() {
            return Err(NetworkError::AllIndexersUnavailable);
        }

        let selected = self.load_balancer.select_indexer()?;

        if !available_indexers.contains(&selected.id) {
            let fallback = available_indexers
                .first()
                .ok_or(NetworkError::AllIndexersUnavailable)?;
            return Ok(fallback.clone());
        }

        Ok(selected.id)
    }

    pub async fn send_events(
        &self,
        indexer_id: &str,
        batch_data: Vec<u8>,
    ) -> Result<(), NetworkError> {
        match self.connection_pool.get_connection(indexer_id).await {
            Ok(conn) => {
                if let Some(_channel) = conn.get_channel() {
                    match tokio::time::timeout(std::time::Duration::from_secs(30), async {
                        debug!("Sending {} bytes to {}", batch_data.len(), indexer_id);
                        Ok::<(), NetworkError>(())
                    })
                    .await
                    {
                        Ok(result) => {
                            result?;
                            self.circuit_breaker.record_success(indexer_id);
                            Ok(())
                        }
                        Err(_) => {
                            error!("Timeout sending to {}", indexer_id);
                            self.circuit_breaker.record_failure(indexer_id);
                            Err(NetworkError::Timeout)
                        }
                    }
                } else {
                    self.circuit_breaker.record_failure(indexer_id);
                    Err(NetworkError::ConnectionError(
                        "No active channel".to_string(),
                    ))
                }
            }
            Err(e) => {
                self.circuit_breaker.record_failure(indexer_id);
                Err(e)
            }
        }
    }

    pub async fn health_check(&self, indexer_id: &str) -> Result<bool, NetworkError> {
        self.connection_pool.health_check(indexer_id).await
    }

    pub fn get_connection_pool(&self) -> Arc<ConnectionPool> {
        Arc::clone(&self.connection_pool)
    }

    pub fn get_load_balancer(&self) -> Arc<LoadBalancer> {
        Arc::clone(&self.load_balancer)
    }

    pub fn get_circuit_breaker(&self) -> Arc<CircuitBreaker> {
        Arc::clone(&self.circuit_breaker)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_grpc_client_creation() {
        let indexers = vec![crate::network::IndexerConfig {
            id: "indexer1".to_string(),
            host: "localhost".to_string(),
            port: 8088,
            weight: 100,
        }];
        let pool_config = crate::network::ConnectionPoolConfig::default();

        let connection_pool =
            match ConnectionPool::new(indexers.clone(), pool_config.clone(), None).await {
                Ok(pool) => Arc::new(pool),
                Err(e) => panic!("Failed to create connection pool: {}", e),
            };

        let load_balancer = Arc::new(crate::network::LoadBalancer::new(
            indexers.clone(),
            crate::network::LoadBalancingStrategy::RoundRobin,
        ));

        let circuit_breaker = Arc::new(CircuitBreaker::new(
            indexers.iter().map(|i| i.id.clone()).collect(),
        ));

        let client = GrpcForwarderClient::new(connection_pool, load_balancer, circuit_breaker);

        assert!(!client
            .get_connection_pool()
            .get_all_indexer_ids()
            .await
            .is_empty());
    }
}
