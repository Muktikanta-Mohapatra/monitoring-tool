use serde_json::Value;
use std::collections::HashMap;
use std::time::{Duration, Instant};
use parking_lot::RwLock;
use std::sync::Arc;

pub struct CacheEntry {
    value: HashMap<String, Value>,
    inserted_at: Instant,
}

pub struct LruCache {
    cache: Arc<RwLock<HashMap<String, CacheEntry>>>,
    max_size: usize,
    ttl: Duration,
}

impl LruCache {
    pub fn new(max_size: usize, ttl_secs: u64) -> Self {
        LruCache {
            cache: Arc::new(RwLock::new(HashMap::new())),
            max_size,
            ttl: Duration::from_secs(ttl_secs),
        }
    }

    pub fn get(&self, key: &str) -> Option<HashMap<String, Value>> {
        let mut cache = self.cache.write();
        
        if let Some(entry) = cache.get(key) {
            if entry.inserted_at.elapsed() < self.ttl {
                return Some(entry.value.clone());
            } else {
                cache.remove(key);
            }
        }
        
        None
    }

    pub fn put(&self, key: String, value: HashMap<String, Value>) {
        let mut cache = self.cache.write();

        if cache.len() >= self.max_size {
            if let Some(oldest_key) = cache.keys().next().cloned() {
                cache.remove(&oldest_key);
            }
        }

        cache.insert(
            key,
            CacheEntry {
                value,
                inserted_at: Instant::now(),
            },
        );
    }

    pub fn clear(&self) {
        let mut cache = self.cache.write();
        cache.clear();
    }

    pub fn size(&self) -> usize {
        let cache = self.cache.read();
        cache.len()
    }

    pub fn cleanup_expired(&self) {
        let mut cache = self.cache.write();
        cache.retain(|_, entry| entry.inserted_at.elapsed() < self.ttl);
    }
}

impl Clone for LruCache {
    fn clone(&self) -> Self {
        LruCache {
            cache: Arc::clone(&self.cache),
            max_size: self.max_size,
            ttl: self.ttl,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_lru_cache_put_get() {
        let cache = LruCache::new(10, 3600);
        let mut value = HashMap::new();
        value.insert("country".to_string(), Value::String("US".to_string()));

        cache.put("192.168.1.1".to_string(), value.clone());
        let result = cache.get("192.168.1.1");

        assert!(result.is_some());
        assert_eq!(result.unwrap().get("country").and_then(|v| v.as_str()), Some("US"));
    }

    #[test]
    fn test_lru_cache_expiration() {
        let cache = LruCache::new(10, 1);
        let mut value = HashMap::new();
        value.insert("key".to_string(), Value::String("value".to_string()));

        cache.put("test_key".to_string(), value);
        assert!(cache.get("test_key").is_some());

        std::thread::sleep(Duration::from_secs(2));
        assert!(cache.get("test_key").is_none());
    }

    #[test]
    fn test_lru_cache_size_limit() {
        let cache = LruCache::new(3, 3600);
        let value = HashMap::new();

        cache.put("key1".to_string(), value.clone());
        cache.put("key2".to_string(), value.clone());
        cache.put("key3".to_string(), value.clone());
        cache.put("key4".to_string(), value.clone());

        assert!(cache.size() <= 3);
    }

    #[test]
    fn test_lru_cache_clear() {
        let cache = LruCache::new(10, 3600);
        let value = HashMap::new();

        cache.put("key1".to_string(), value.clone());
        cache.put("key2".to_string(), value);
        assert!(cache.size() > 0);

        cache.clear();
        assert_eq!(cache.size(), 0);
    }
}
