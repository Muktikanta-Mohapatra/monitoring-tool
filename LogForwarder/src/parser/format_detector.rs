use crate::parser::LogParser;
use lru::LruCache;
use parking_lot::RwLock;
use std::num::NonZeroUsize;
use std::sync::Arc;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};

#[derive(Debug, Clone)]
pub struct FormatStats {
    pub total_lines_processed: u64,
    pub lines_since_last_eval: u64,
}

pub struct FormatDetector {
    parsers: Vec<Arc<dyn LogParser>>,
    cache: Arc<RwLock<LruCache<u64, usize>>>,
    stats: Arc<RwLock<FormatStats>>,
    confidence_threshold: f64,
    re_evaluation_interval: u64,
}

impl FormatDetector {
    pub fn new(
        parsers: Vec<Arc<dyn LogParser>>,
        cache_size: usize,
        confidence_threshold: f64,
        re_evaluation_interval: u64,
    ) -> Self {
        let cache_capacity = NonZeroUsize::new(cache_size.max(1)).unwrap();
        FormatDetector {
            parsers,
            cache: Arc::new(RwLock::new(LruCache::new(cache_capacity))),
            stats: Arc::new(RwLock::new(FormatStats {
                total_lines_processed: 0,
                lines_since_last_eval: 0,
            })),
            confidence_threshold,
            re_evaluation_interval,
        }
    }

    pub fn detect(&self, line: &str) -> Option<usize> {
        let hash = self.hash_line(line);

        let mut cache = self.cache.write();
        if let Some(&parser_idx) = cache.get(&hash) {
            drop(cache);
            
            let mut stats = self.stats.write();
            stats.total_lines_processed += 1;
            stats.lines_since_last_eval += 1;

            if stats.lines_since_last_eval >= self.re_evaluation_interval {
                drop(stats);
                self.cache.write().clear();
                let mut stats = self.stats.write();
                stats.lines_since_last_eval = 0;
            }
            
            return Some(parser_idx);
        }
        drop(cache);

        let mut scores: Vec<_> = self.parsers
            .iter()
            .enumerate()
            .map(|(i, p)| (i, p.can_parse(line)))
            .collect();

        scores.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));

        let result = if !scores.is_empty() && scores[0].1 > self.confidence_threshold {
            let parser_idx = scores[0].0;
            self.cache.write().put(hash, parser_idx);
            Some(parser_idx)
        } else {
            None
        };

        let mut stats = self.stats.write();
        stats.total_lines_processed += 1;
        stats.lines_since_last_eval += 1;

        if stats.lines_since_last_eval >= self.re_evaluation_interval {
            drop(stats);
            self.cache.write().clear();
            let mut stats = self.stats.write();
            stats.lines_since_last_eval = 0;
        }

        result
    }

    pub fn get_parser(&self, idx: usize) -> Option<Arc<dyn LogParser>> {
        self.parsers.get(idx).cloned()
    }

    pub fn parsers(&self) -> &[Arc<dyn LogParser>] {
        &self.parsers
    }

    pub fn stats(&self) -> FormatStats {
        self.stats.read().clone()
    }

    fn hash_line(&self, line: &str) -> u64 {
        let mut hasher = DefaultHasher::new();
        line.hash(&mut hasher);
        hasher.finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::JsonParser;
    use std::sync::Arc;

    #[test]
    fn test_format_detector_json() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

        let json_line = r#"{"level":"INFO","message":"test"}"#;
        let result = detector.detect(json_line);
        assert!(result.is_some());
        assert_eq!(result.unwrap(), 0);
    }

    #[test]
    fn test_format_detector_cache() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

        let json_line = r#"{"level":"INFO","message":"test"}"#;
        let first = detector.detect(json_line);
        let second = detector.detect(json_line);

        assert_eq!(first, second);
        assert!(first.is_some());

        let stats = detector.stats();
        assert_eq!(stats.total_lines_processed, 2);
    }

    #[test]
    fn test_format_detector_no_match() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

        let invalid_line = "this is not json or any known format";
        let result = detector.detect(invalid_line);
        assert!(result.is_none());
    }

    #[test]
    fn test_format_detector_re_evaluation() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = FormatDetector::new(vec![json_parser], 10, 0.8, 5);

        let json_line = r#"{"level":"INFO","message":"test"}"#;

        for _ in 0..5 {
            detector.detect(json_line);
        }

        let stats = detector.stats();
        assert_eq!(stats.total_lines_processed, 5);
        assert_eq!(stats.lines_since_last_eval, 0);
    }
}
