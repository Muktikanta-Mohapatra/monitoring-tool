pub mod circuit_breaker;
pub mod connection_pool;
pub mod dead_letter_queue;
pub mod errors;
pub mod grpc_client;
pub mod health_check;
pub mod load_balancer;
pub mod proto;
pub mod tls_config;

pub use circuit_breaker::CircuitBreaker;
pub use connection_pool::{ConnectionPool, ConnectionPoolConfig};
pub use dead_letter_queue::{DeadLetterQueue, DeadLetterEntry};
pub use errors::NetworkError;
pub use grpc_client::GrpcForwarderClient;
pub use health_check::HealthChecker;
pub use load_balancer::{LoadBalancer, LoadBalancingStrategy};
pub use tls_config::{TlsConfig, TlsConfigBuilder};

use std::sync::Arc;
use tracing::debug;

#[derive(Clone, Debug)]
pub struct IndexerConfig {
    pub id: String,
    pub host: String,
    pub port: u16,
    pub weight: u32,
}

pub struct NetworkClient {
    connection_pool: Arc<ConnectionPool>,
    load_balancer: Arc<LoadBalancer>,
    circuit_breaker: Arc<CircuitBreaker>,
    health_checker: Arc<HealthChecker>,
}

impl NetworkClient {
    pub async fn new(
        indexers: Vec<IndexerConfig>,
        pool_config: ConnectionPoolConfig,
        tls_config: Option<TlsConfig>,
    ) -> Result<Self, NetworkError> {
        debug!("Creating NetworkClient with {} indexers", indexers.len());

        let connection_pool =
            Arc::new(ConnectionPool::new(indexers.clone(), pool_config, tls_config.clone()).await?);

        let load_balancer = Arc::new(LoadBalancer::new(
            indexers.clone(),
            LoadBalancingStrategy::LeastConnections,
        ));

        let circuit_breaker = Arc::new(CircuitBreaker::new(
            indexers.iter().map(|i| i.id.clone()).collect(),
        ));

        let health_checker = Arc::new(HealthChecker::new(
            connection_pool.clone(),
            circuit_breaker.clone(),
        ));

        Ok(NetworkClient {
            connection_pool,
            load_balancer,
            circuit_breaker,
            health_checker,
        })
    }

    pub fn connection_pool(&self) -> Arc<ConnectionPool> {
        Arc::clone(&self.connection_pool)
    }

    pub fn load_balancer(&self) -> Arc<LoadBalancer> {
        Arc::clone(&self.load_balancer)
    }

    pub fn circuit_breaker(&self) -> Arc<CircuitBreaker> {
        Arc::clone(&self.circuit_breaker)
    }

    pub fn health_checker(&self) -> Arc<HealthChecker> {
        Arc::clone(&self.health_checker)
    }

    pub async fn start_health_checks(&self) {
        self.health_checker.start().await;
    }
}
