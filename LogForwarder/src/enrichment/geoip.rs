use crate::enrichment::{Enricher, EnrichmentError, EnrichmentResult, LruCache};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::net::IpAddr;

pub struct GeoIpEnricher {
    name: String,
    cache: LruCache,
}

impl GeoIpEnricher {
    pub fn new(name: String, cache_size: usize, cache_ttl_secs: u64) -> Self {
        GeoIpEnricher {
            name,
            cache: LruCache::new(cache_size, cache_ttl_secs),
        }
    }

    fn is_valid_ip(&self, ip: &str) -> bool {
        ip.parse::<IpAddr>().is_ok()
    }

    fn mock_geoip_lookup(&self, ip: &str) -> HashMap<String, Value> {
        let mut result = HashMap::new();
        
        match ip {
            "8.8.8.8" => {
                result.insert("country".to_string(), json!("US"));
                result.insert("city".to_string(), json!("Mountain View"));
                result.insert("latitude".to_string(), json!(37.3860));
                result.insert("longitude".to_string(), json!(-122.0838));
                result.insert("asn".to_string(), json!(15169));
                result.insert("org".to_string(), json!("Google LLC"));
            }
            "1.1.1.1" => {
                result.insert("country".to_string(), json!("AU"));
                result.insert("city".to_string(), json!("Sydney"));
                result.insert("latitude".to_string(), json!(-33.8688));
                result.insert("longitude".to_string(), json!(151.2093));
                result.insert("asn".to_string(), json!(13335));
                result.insert("org".to_string(), json!("Cloudflare Inc"));
            }
            _ => {
                result.insert("country".to_string(), json!("UNKNOWN"));
                result.insert("city".to_string(), json!("UNKNOWN"));
            }
        }

        result
    }
}

#[async_trait::async_trait]
impl Enricher for GeoIpEnricher {
    fn name(&self) -> &str {
        &self.name
    }

    async fn enrich(&self, input_value: &str, _context: &HashMap<String, Value>) -> EnrichmentResult<HashMap<String, Value>> {
        if !self.is_valid_ip(input_value) {
            return Err(EnrichmentError::EnrichmentFailed(format!(
                "Invalid IP address: {}",
                input_value
            )));
        }

        if let Some(cached) = self.cache.get(input_value) {
            return Ok(cached);
        }

        let result = self.mock_geoip_lookup(input_value);
        
        if result.get("country").and_then(|v| v.as_str()) != Some("UNKNOWN") {
            self.cache.put(input_value.to_string(), result.clone());
        }

        Ok(result)
    }

    fn supports_caching(&self) -> bool {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_geoip_enricher_valid_ip() {
        let enricher = GeoIpEnricher::new("test_geoip".to_string(), 100, 3600);
        let result = enricher.enrich("8.8.8.8", &HashMap::new()).await;

        assert!(result.is_ok());
        let enriched = result.unwrap();
        assert_eq!(enriched.get("country").and_then(|v| v.as_str()), Some("US"));
    }

    #[tokio::test]
    async fn test_geoip_enricher_invalid_ip() {
        let enricher = GeoIpEnricher::new("test_geoip".to_string(), 100, 3600);
        let result = enricher.enrich("not.an.ip", &HashMap::new()).await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_geoip_enricher_caching() {
        let enricher = GeoIpEnricher::new("test_geoip".to_string(), 100, 3600);
        
        let result1 = enricher.enrich("8.8.8.8", &HashMap::new()).await;
        assert!(result1.is_ok());

        let result2 = enricher.enrich("8.8.8.8", &HashMap::new()).await;
        assert!(result2.is_ok());
        
        assert_eq!(enricher.cache.size(), 1);
    }
}
