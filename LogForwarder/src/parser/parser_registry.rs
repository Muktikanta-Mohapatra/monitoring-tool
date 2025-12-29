use crate::parser::LogParser;
use std::collections::HashMap;
use std::sync::Arc;
use parking_lot::RwLock;

pub struct ParserRegistry {
    parsers: Arc<RwLock<HashMap<String, Arc<dyn LogParser>>>>,
    priority_order: Arc<RwLock<Vec<String>>>,
}

impl ParserRegistry {
    pub fn new() -> Self {
        ParserRegistry {
            parsers: Arc::new(RwLock::new(HashMap::new())),
            priority_order: Arc::new(RwLock::new(Vec::new())),
        }
    }

    pub fn register(&self, id: String, parser: Arc<dyn LogParser>) {
        let mut registry = self.parsers.write();
        registry.insert(id, parser);
    }

    pub fn get_parser(&self, id: &str) -> Option<Arc<dyn LogParser>> {
        let registry = self.parsers.read();
        registry.get(id).cloned()
    }

    pub fn detect_parser(&self, sample: &str) -> Option<Arc<dyn LogParser>> {
        let priority = self.priority_order.read();
        let registry = self.parsers.read();

        if !priority.is_empty() {
            for parser_id in priority.iter() {
                if let Some(parser) = registry.get(parser_id) {
                    if parser.can_parse(sample) > 0.8 {
                        return Some(parser.clone());
                    }
                }
            }
        }

        let mut best_parser: Option<Arc<dyn LogParser>> = None;
        let mut best_confidence = 0.0;

        for parser in registry.values() {
            let confidence = parser.can_parse(sample);
            if confidence > best_confidence {
                best_confidence = confidence;
                best_parser = Some(parser.clone());
            }
        }

        best_parser
    }

    pub fn set_priority_order(&self, order: Vec<String>) {
        let mut priority = self.priority_order.write();
        *priority = order;
    }

    pub fn get_priority_order(&self) -> Vec<String> {
        self.priority_order.read().clone()
    }

    pub fn list_parsers(&self) -> Vec<String> {
        let registry = self.parsers.read();
        registry.keys().cloned().collect()
    }

    pub fn parser_count(&self) -> usize {
        let registry = self.parsers.read();
        registry.len()
    }
}

impl Clone for ParserRegistry {
    fn clone(&self) -> Self {
        ParserRegistry {
            parsers: Arc::clone(&self.parsers),
            priority_order: Arc::clone(&self.priority_order),
        }
    }
}

impl Default for ParserRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::JsonParser;

    #[test]
    fn test_parser_registry_register() {
        let registry = ParserRegistry::new();
        let parser = Arc::new(JsonParser::new("test_json".to_string(), None));
        registry.register("test".to_string(), parser);

        assert_eq!(registry.parser_count(), 1);
        assert!(registry.get_parser("test").is_some());
    }

    #[test]
    fn test_parser_registry_detect() {
        let registry = ParserRegistry::new();
        let json_parser = Arc::new(JsonParser::new("test_json".to_string(), None));
        registry.register("json".to_string(), json_parser);

        let detected = registry.detect_parser(r#"{"test": "value"}"#);
        assert!(detected.is_some());
        assert_eq!(detected.unwrap().name(), "test_json");
    }

    #[test]
    fn test_parser_registry_list() {
        let registry = ParserRegistry::new();
        let parser1 = Arc::new(JsonParser::new("parser1".to_string(), None));
        let parser2 = Arc::new(JsonParser::new("parser2".to_string(), None));

        registry.register("json1".to_string(), parser1);
        registry.register("json2".to_string(), parser2);

        let list = registry.list_parsers();
        assert_eq!(list.len(), 2);
    }
}
