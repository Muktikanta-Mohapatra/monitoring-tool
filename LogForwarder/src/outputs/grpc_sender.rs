use crate::config::{IndexerConfig, OutputConfig};
use crate::event::Event;
use crate::network::GrpcForwarderClient;
use crate::outputs::{EventBatch, OutputError, OutputSenderTrait, retry_policy::RetryPolicy};
use async_trait::async_trait;
use std::sync::Arc;
use thiserror::Error;
use tokio::sync::Mutex;
use tracing::{debug, warn};

#[derive(Error, Debug)]
pub enum GrpcOutputError {
    #[error("Network error: {0}")]
    Network(String),

    #[error("Serialization error: {0}")]
    Serialization(String),

    #[error("All indexers unavailable")]
    NoAvailableIndexers,
}

pub struct GrpcOutputSender {
    config: OutputConfig,
    client: Mutex<Option<Arc<GrpcForwarderClient>>>,
    indexers: Vec<IndexerConfig>,
    retry_policy: RetryPolicy,
}

#[async_trait]
impl OutputSenderTrait for GrpcOutputSender {
    async fn send_batch(&self, batch: EventBatch) -> Result<(), OutputError> {
        if batch.is_empty() {
            return Ok(());
        }

        debug!(
            "Sending {} events via gRPC to {}",
            batch.len(),
            self.config.name
        );

        let mut attempt = 0;
        loop {
            let client = self.get_or_create_client().await?;

            let indexer_id = match client.select_available_indexer().await {
                Ok(id) => id,
                Err(e) => {
                    let error_msg = e.to_string();
                    if self.retry_policy.should_retry_on_error(&error_msg) && self.retry_policy.can_retry(attempt) {
                        attempt += 1;
                        let backoff = self.retry_policy.calculate_backoff(attempt);
                        warn!("Failed to select indexer (attempt {}/{}), retrying in {:?}: {}", attempt, self.retry_policy.max_attempts, backoff, error_msg);
                        tokio::time::sleep(backoff).await;
                        continue;
                    } else {
                        return Err(OutputError::GrpcError(error_msg));
                    }
                }
            };

            let mut batch_data = Vec::new();
            for event in &batch.events {
                batch_data.extend_from_slice(event.raw_data());
            }

            match client.send_events(&indexer_id, batch_data).await {
                Ok(_) => {
                    debug!("Successfully sent {} events to indexer {}", batch.len(), indexer_id);
                    return Ok(());
                }
                Err(e) => {
                    let error_msg = e.to_string();
                    if self.retry_policy.should_retry_on_error(&error_msg) && self.retry_policy.can_retry(attempt) {
                        attempt += 1;
                        let backoff = self.retry_policy.calculate_backoff(attempt);
                        warn!("Failed to send batch (attempt {}/{}), retrying in {:?}: {}", attempt, self.retry_policy.max_attempts, backoff, error_msg);
                        tokio::time::sleep(backoff).await;
                    } else {
                        warn!("Failed to send batch after {} attempts: {}", attempt, error_msg);
                        return Err(OutputError::GrpcError(error_msg));
                    }
                }
            }
        }
    }
}

impl GrpcOutputSender {
    pub fn new(config: OutputConfig, client: Arc<GrpcForwarderClient>) -> Self {
        GrpcOutputSender {
            config,
            client: Mutex::new(Some(client)),
            indexers: vec![],
            retry_policy: RetryPolicy::default(),
        }
    }

    pub fn new_from_config(config: OutputConfig, indexers: Vec<IndexerConfig>) -> Self {
        GrpcOutputSender {
            config,
            client: Mutex::new(None),
            indexers,
            retry_policy: RetryPolicy::default(),
        }
    }

    pub fn with_retry_policy(mut self, retry_policy: RetryPolicy) -> Self {
        self.retry_policy = retry_policy;
        self
    }

    async fn get_or_create_client(&self) -> Result<Arc<GrpcForwarderClient>, OutputError> {
        let mut client_lock = self.client.lock().await;

        if let Some(client) = client_lock.as_ref() {
            return Ok(Arc::clone(client));
        }

        let network_indexers: Vec<crate::network::IndexerConfig> = self
            .indexers
            .iter()
            .map(|i| crate::network::IndexerConfig {
                id: i.id.clone(),
                host: i.host.clone(),
                port: i.port,
                weight: i.weight,
            })
            .collect();

        if network_indexers.is_empty() {
            return Err(OutputError::GrpcError(
                "No indexers configured for gRPC sender".to_string(),
            ));
        }

        let pool_config = crate::network::ConnectionPoolConfig::default();

        let connection_pool = Arc::new(
            crate::network::ConnectionPool::new(network_indexers.clone(), pool_config, None)
                .await
                .map_err(|e| OutputError::GrpcError(e.to_string()))?,
        );

        let load_balancer = Arc::new(crate::network::LoadBalancer::new(
            network_indexers.clone(),
            crate::network::LoadBalancingStrategy::RoundRobin,
        ));

        let circuit_breaker = Arc::new(crate::network::CircuitBreaker::new(
            network_indexers.iter().map(|i| i.id.clone()).collect(),
        ));

        let new_client = Arc::new(crate::network::GrpcForwarderClient::new(
            connection_pool,
            load_balancer,
            circuit_breaker,
        ));

        *client_lock = Some(Arc::clone(&new_client));
        Ok(new_client)
    }

    pub async fn send_batch_legacy(&self, events: Vec<Event>) -> Result<(), GrpcOutputError> {
        if events.is_empty() {
            return Ok(());
        }

        let client = self
            .get_or_create_client()
            .await
            .map_err(|e| GrpcOutputError::Network(e.to_string()))?;

        debug!(
            "Sending {} events via gRPC to {}",
            events.len(),
            self.config.name
        );

        let indexer_id = client
            .select_available_indexer()
            .await
            .map_err(|e| GrpcOutputError::Network(e.to_string()))?;

        let mut batch_data = Vec::new();
        for event in events {
            batch_data.extend_from_slice(event.raw_data());
        }

        client
            .send_events(&indexer_id, batch_data)
            .await
            .map_err(|e| GrpcOutputError::Network(e.to_string()))?;

        Ok(())
    }

    pub async fn health_check(&self, indexer_id: &str) -> Result<bool, GrpcOutputError> {
        let client = self
            .get_or_create_client()
            .await
            .map_err(|e| GrpcOutputError::Network(e.to_string()))?;

        client
            .health_check(indexer_id)
            .await
            .map_err(|e| GrpcOutputError::Network(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_grpc_sender_creation_from_config() {
        let config = OutputConfig {
            name: "test_grpc".to_string(),
            url: "grpc://localhost:50051".to_string(),
            token: "test-token".to_string(),
            api_key: "".to_string(),
            forwarder_id: "test-forwarder".to_string(),
            tls_verify: false,
            batch_size: 100,
            flush_interval_ms: 1000,
            retry_count: 3,
            compression_enabled: false,
            compression_level: 3,
            compression_adaptive: true,
            compression_use_dict: false,
            protocol: "grpc".to_string(),
        };

        let indexers = vec![];
        let sender = GrpcOutputSender::new_from_config(config, indexers);
        assert_eq!(sender.config.name, "test_grpc");
    }
}
