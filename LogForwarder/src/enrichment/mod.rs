use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;
use thiserror::Error;
use async_trait::async_trait;

mod cache;
mod geoip;
mod lookup_table;

pub use cache::LruCache;
pub use geoip::GeoIpEnricher;
pub use lookup_table::LookupTableEnricher;

#[derive(Error, Debug)]
pub enum EnrichmentError {
    #[error("Enrichment service unavailable: {0}")]
    ServiceUnavailable(String),
    #[error("Field not found: {0}")]
    FieldNotFound(String),
    #[error("Enrichment failed: {0}")]
    EnrichmentFailed(String),
    #[error("Cache error: {0}")]
    CacheError(String),
}

pub type EnrichmentResult<T> = Result<T, EnrichmentError>;

#[async_trait]
pub trait Enricher: Send + Sync {
    fn name(&self) -> &str;
    async fn enrich(&self, input_value: &str, context: &HashMap<String, Value>) -> EnrichmentResult<HashMap<String, Value>>;
    fn supports_caching(&self) -> bool;
}

pub struct EnrichmentConfig {
    pub enricher_type: String,
    pub input_field: String,
    pub output_prefix: String,
    pub cache_enabled: bool,
    pub cache_ttl_secs: u64,
}

impl EnrichmentConfig {
    pub fn new(enricher_type: String, input_field: String, output_prefix: String) -> Self {
        EnrichmentConfig {
            enricher_type,
            input_field,
            output_prefix,
            cache_enabled: true,
            cache_ttl_secs: 3600,
        }
    }
}

pub struct EnrichmentPipeline {
    enrichers: Vec<Arc<dyn Enricher>>,
}

impl EnrichmentPipeline {
    pub fn new() -> Self {
        EnrichmentPipeline {
            enrichers: Vec::new(),
        }
    }

    pub fn add_enricher(&mut self, enricher: Arc<dyn Enricher>) {
        self.enrichers.push(enricher);
    }

    pub async fn enrich(&self, mut fields: HashMap<String, Value>) -> EnrichmentResult<HashMap<String, Value>> {
        for enricher in &self.enrichers {
            for (key, value) in fields.clone() {
                if let Some(val_str) = value.as_str() {
                    match enricher.enrich(val_str, &fields).await {
                        Ok(enriched) => {
                            for (ek, ev) in enriched {
                                fields.insert(ek, ev);
                            }
                        }
                        Err(e) => {
                            tracing::warn!("Enrichment failed for {}: {}", key, e);
                        }
                    }
                }
            }
        }
        Ok(fields)
    }
}

impl Default for EnrichmentPipeline {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_enrichment_config() {
        let config = EnrichmentConfig::new(
            "geoip".to_string(),
            "client_ip".to_string(),
            "geo_".to_string(),
        );
        assert_eq!(config.enricher_type, "geoip");
        assert_eq!(config.input_field, "client_ip");
        assert!(config.cache_enabled);
    }
}
