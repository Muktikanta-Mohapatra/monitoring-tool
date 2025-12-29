use crate::enrichment::{Enricher, EnrichmentError, EnrichmentResult, LruCache};
use serde_json::Value;
use std::collections::HashMap;

pub struct LookupTableEnricher {
    name: String,
    lookup_table: HashMap<String, HashMap<String, Value>>,
    cache: LruCache,
}

impl LookupTableEnricher {
    pub fn new(name: String, cache_size: usize, cache_ttl_secs: u64) -> Self {
        LookupTableEnricher {
            name,
            lookup_table: HashMap::new(),
            cache: LruCache::new(cache_size, cache_ttl_secs),
        }
    }

    pub fn add_entry(&mut self, key: String, value: HashMap<String, Value>) {
        self.lookup_table.insert(key, value);
    }

    pub fn load_from_map(&mut self, data: HashMap<String, HashMap<String, Value>>) {
        self.lookup_table = data;
    }

    pub fn lookup(&self, key: &str) -> Option<HashMap<String, Value>> {
        if let Some(cached) = self.cache.get(key) {
            return Some(cached);
        }

        if let Some(result) = self.lookup_table.get(key) {
            self.cache.put(key.to_string(), result.clone());
            Some(result.clone())
        } else {
            None
        }
    }
}

#[async_trait::async_trait]
impl Enricher for LookupTableEnricher {
    fn name(&self) -> &str {
        &self.name
    }

    async fn enrich(&self, input_value: &str, _context: &HashMap<String, Value>) -> EnrichmentResult<HashMap<String, Value>> {
        self.lookup(input_value)
            .ok_or_else(|| EnrichmentError::FieldNotFound(format!(
                "No lookup found for: {}",
                input_value
            )))
    }

    fn supports_caching(&self) -> bool {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_lookup_table_enricher() {
        let mut enricher = LookupTableEnricher::new("test_lookup".to_string(), 100, 3600);
        
        let mut entry = HashMap::new();
        entry.insert("owner".to_string(), json!("team_a"));
        entry.insert("environment".to_string(), json!("production"));
        
        enricher.add_entry("service_1".to_string(), entry);
        
        let result = enricher.lookup("service_1");
        assert!(result.is_some());
        assert_eq!(result.unwrap().get("owner").and_then(|v| v.as_str()), Some("team_a"));
    }

    #[tokio::test]
    async fn test_lookup_table_enricher_async() {
        let mut enricher = LookupTableEnricher::new("test_lookup".to_string(), 100, 3600);
        
        let mut entry = HashMap::new();
        entry.insert("department".to_string(), json!("engineering"));
        enricher.add_entry("user_123".to_string(), entry);
        
        let result = enricher.enrich("user_123", &HashMap::new()).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_lookup_table_enricher_not_found() {
        let enricher = LookupTableEnricher::new("test_lookup".to_string(), 100, 3600);
        let result = enricher.enrich("nonexistent", &HashMap::new()).await;
        
        assert!(result.is_err());
    }

    #[test]
    fn test_lookup_table_caching() {
        let mut enricher = LookupTableEnricher::new("test_lookup".to_string(), 100, 3600);
        
        let mut entry = HashMap::new();
        entry.insert("value".to_string(), json!("test"));
        enricher.add_entry("key1".to_string(), entry);
        
        let _ = enricher.lookup("key1");
        let _ = enricher.lookup("key1");
        
        assert_eq!(enricher.cache.size(), 1);
    }
}
