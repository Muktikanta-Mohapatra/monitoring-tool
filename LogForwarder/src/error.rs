use thiserror::Error;
use std::io;

#[derive(Error, Debug)]
pub enum ForwarderError {
    #[error("IO error: {0}")]
    Io(#[from] io::Error),

    #[error("Configuration error: {0}")]
    Config(String),

    #[error("Parse error: input={input}, details={details}")]
    Parse { 
        input: String, 
        details: String 
    },

    #[error("Network error: {0}")]
    Network(String),

    #[error("Output error: cause={cause}, retries={retry_count}")]
    Output { 
        cause: String, 
        retry_count: u32 
    },

    #[error("Checkpoint error: {0}")]
    Checkpoint(String),

    #[error("Invalid encoding")]
    InvalidEncoding,

    #[error("File watcher error: {0}")]
    FileWatcher(String),

    #[error("Serialization error: {0}")]
    Serialization(String),

    #[error("gRPC error: {0}")]
    GrpcError(String),

    #[error("Validation error: {0}")]
    Validation(String),

    #[error("Timeout: {0}")]
    Timeout(String),

    #[error("Internal error: {0}")]
    Internal(String),
}

impl ForwarderError {
    pub fn is_retryable(&self) -> bool {
        matches!(
            self,
            ForwarderError::Network(_)
                | ForwarderError::Timeout(_)
                | ForwarderError::Output { .. }
        )
    }

    pub fn is_critical(&self) -> bool {
        matches!(
            self,
            ForwarderError::Config(_) | ForwarderError::Internal(_)
        )
    }
}

pub type ForwarderResult<T> = Result<T, ForwarderError>;
