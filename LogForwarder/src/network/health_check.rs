use crate::network::{CircuitBreaker, ConnectionPool};
use std::sync::Arc;
use std::time::Duration;
use tokio::time::interval;
use tracing::{debug, warn};

pub struct HealthChecker {
    connection_pool: Arc<ConnectionPool>,
    circuit_breaker: Arc<CircuitBreaker>,
    check_interval: Duration,
}

impl HealthChecker {
    pub fn new(connection_pool: Arc<ConnectionPool>, circuit_breaker: Arc<CircuitBreaker>) -> Self {
        HealthChecker {
            connection_pool,
            circuit_breaker,
            check_interval: Duration::from_secs(30),
        }
    }

    pub fn with_interval(mut self, interval: Duration) -> Self {
        self.check_interval = interval;
        self
    }

    pub async fn start(&self) {
        let mut ticker = interval(self.check_interval);
        let connection_pool = Arc::clone(&self.connection_pool);
        let circuit_breaker = Arc::clone(&self.circuit_breaker);

        loop {
            ticker.tick().await;
            self.run_health_check(&connection_pool, &circuit_breaker)
                .await;
        }
    }

    async fn run_health_check(
        &self,
        connection_pool: &Arc<ConnectionPool>,
        circuit_breaker: &Arc<CircuitBreaker>,
    ) {
        debug!("Running health checks");

        let indexers = connection_pool.get_all_indexer_ids().await;

        for indexer_id in indexers {
            match connection_pool.health_check(&indexer_id).await {
                Ok(healthy) => {
                    if healthy {
                        debug!("Health check passed for {}", indexer_id);
                        circuit_breaker.record_success(&indexer_id);
                    } else {
                        warn!("Health check failed for {}", indexer_id);
                        circuit_breaker.record_failure(&indexer_id);
                    }
                }
                Err(e) => {
                    warn!("Health check error for {}: {}", indexer_id, e);
                    circuit_breaker.record_failure(&indexer_id);
                }
            }
        }
    }

    pub async fn check_single(&self, indexer_id: &str) -> bool {
        match self.connection_pool.health_check(indexer_id).await {
            Ok(healthy) => {
                if healthy {
                    self.circuit_breaker.record_success(indexer_id);
                } else {
                    self.circuit_breaker.record_failure(indexer_id);
                }
                healthy
            }
            Err(e) => {
                warn!("Health check error for {}: {}", indexer_id, e);
                self.circuit_breaker.record_failure(indexer_id);
                false
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_health_checker_creation() {
        let indexers = vec![crate::network::IndexerConfig {
            id: "test-indexer".to_string(),
            host: "127.0.0.1".to_string(),
            port: 50051,
            weight: 100,
        }];
        let pool_config = crate::network::ConnectionPoolConfig::default();
        let connection_pool = ConnectionPool::new(indexers, pool_config, None).await;

        if let Ok(pool) = connection_pool {
            let connection_pool = Arc::new(pool);
            let circuit_breaker = Arc::new(CircuitBreaker::new(vec![]));

            let health_checker = HealthChecker::new(connection_pool, circuit_breaker);
            assert_eq!(health_checker.check_interval, Duration::from_secs(30));
        }
    }
}
