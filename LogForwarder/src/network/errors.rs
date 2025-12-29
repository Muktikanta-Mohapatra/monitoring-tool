use thiserror::Error;

#[derive(Error, Debug)]
pub enum NetworkError {
    #[error("Connection error: {0}")]
    ConnectionError(String),

    #[error("TLS error: {0}")]
    TlsError(String),

    #[error("gRPC error: {0}")]
    GrpcError(String),

    #[error("Circuit breaker open for {indexer_id}")]
    CircuitBreakerOpen { indexer_id: String },

    #[error("All indexers are unavailable")]
    AllIndexersUnavailable,

    #[error("Connection pool exhausted")]
    PoolExhausted,

    #[error("Invalid configuration: {0}")]
    InvalidConfig(String),

    #[error("Health check failed: {0}")]
    HealthCheckFailed(String),

    #[error("Backpressure: {0}")]
    Backpressure(String),

    #[error("Serialization error: {0}")]
    SerializationError(String),

    #[error("Timeout")]
    Timeout,

    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),
}
