use std::env;
use std::path::Path;
use tracing::{info, warn, debug};

/// Load environment variables from .env file and system environment
/// Returns the API key if found
pub fn load_env_config() -> Result<Option<String>, std::io::Error> {
    // Try to load from .env file first
    if Path::new(".env").exists() {
        match dotenvy::dotenv() {
            Ok(path) => {
                info!("Loaded environment variables from: {:?}", path);
            }
            Err(e) => {
                warn!("Failed to load .env file: {}", e);
            }
        }
    } else {
        debug!("No .env file found, using system environment variables only");
    }

    // Get API key from environment
    let api_key = env::var("FORWARDER_API_KEY").ok();

    if api_key.is_some() {
        info!("API key loaded from environment variable");
    } else {
        warn!("FORWARDER_API_KEY not found in environment");
    }

    Ok(api_key)
}

/// Get forwarder ID from environment or use default
pub fn get_forwarder_id(default: Option<String>) -> String {
    match env::var("FORWARDER_ID") {
        Ok(id) if !id.is_empty() => {
            info!("Using forwarder ID from environment: {}", id);
            id
        }
        _ => {
            let id = default.unwrap_or_else(|| format!("forwarder-{}", uuid::Uuid::new_v4()));
            debug!("Using default forwarder ID: {}", id);
            id
        }
    }
}

/// Get middleware URL from environment or use default
pub fn get_middleware_url(default: Option<String>) -> Option<String> {
    match env::var("MIDDLEWARE_URL") {
        Ok(url) if !url.is_empty() => {
            info!("Using middleware URL from environment: {}", url);
            Some(url)
        }
        _ => {
            debug!("Using middleware URL from configuration");
            default
        }
    }
}

/// Apply environment variable overrides to output configuration
pub fn apply_env_overrides(mut outputs: Vec<crate::config::OutputConfig>) -> Vec<crate::config::OutputConfig> {
    let api_key = env::var("FORWARDER_API_KEY").ok();
    let forwarder_id = env::var("FORWARDER_ID").ok();
    let middleware_url = env::var("MIDDLEWARE_URL").ok();

    for output in &mut outputs {
        // Override API key if provided in environment
        if let Some(ref key) = api_key {
            if output.token.is_empty() || output.token == "your-jwt-token-here" {
                debug!("Overriding token for output '{}' with environment API key", output.name);
                output.token = key.clone();
            }
        }

        // Override forwarder ID if provided in environment
        if let Some(ref id) = forwarder_id {
            debug!("Overriding forwarder_id for output '{}' to: {}", output.name, id);
            output.forwarder_id = id.clone();
        }

        // Override URL if provided in environment
        if let Some(ref url) = middleware_url {
            if output.url.contains("localhost") || output.url.contains("middleware") {
                debug!("Overriding URL for output '{}' to: {}", output.name, url);
                output.url = url.clone();
            }
        }
    }

    outputs
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_get_forwarder_id_from_env() {
        env::set_var("FORWARDER_ID", "test-forwarder-123");
        let id = get_forwarder_id(None);
        assert_eq!(id, "test-forwarder-123");
        env::remove_var("FORWARDER_ID");
    }

    #[test]
    fn test_get_forwarder_id_default() {
        env::remove_var("FORWARDER_ID");
        let id = get_forwarder_id(Some("default-id".to_string()));
        assert_eq!(id, "default-id");
    }

    #[test]
    fn test_get_middleware_url() {
        env::set_var("MIDDLEWARE_URL", "http://custom:8080");
        let url = get_middleware_url(None);
        assert_eq!(url, Some("http://custom:8080".to_string()));
        env::remove_var("MIDDLEWARE_URL");
    }
}
