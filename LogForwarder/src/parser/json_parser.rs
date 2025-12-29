use crate::parser::{LogParser, ParsedEvent, ParseError, ParseResult};
use chrono::Utc;
use serde_json::Value;
use std::collections::HashMap;
use std::time::Instant;

pub struct JsonParser {
    name: String,
    field_mapping: HashMap<String, String>,
}

impl JsonParser {
    pub fn new(name: String, field_mapping: Option<HashMap<String, String>>) -> Self {
        JsonParser {
            name,
            field_mapping: field_mapping.unwrap_or_default(),
        }
    }

    pub fn detect_json_confidence(sample: &str) -> f64 {
        let trimmed = sample.trim();
        if (trimmed.starts_with('{') || trimmed.starts_with('[')) && (trimmed.ends_with('}') || trimmed.ends_with(']')) {
            match serde_json::from_str::<Value>(trimmed) {
                Ok(_) => 0.95,
                Err(_) => 0.0,
            }
        } else {
            0.0
        }
    }

    fn extract_value_by_path(&self, obj: &Value, path: &str) -> Option<Value> {
        let parts: Vec<&str> = path.trim_start_matches('$').trim_start_matches('.').split('.').collect();
        let mut current = obj;

        for part in parts {
            if let Some(idx) = part.find('[') {
                let field = &part[..idx];
                let arr_idx: usize = part[idx + 1..part.len() - 1].parse().ok()?;
                current = &current[field];
                current = current.get(arr_idx)?;
            } else {
                current = &current[part];
                if current.is_null() {
                    return None;
                }
            }
        }

        Some(current.clone())
    }
}

#[async_trait::async_trait]
impl LogParser for JsonParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, sample: &str) -> f64 {
        Self::detect_json_confidence(sample)
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();
        let trimmed = line.trim();

        let json_value: Value = serde_json::from_str(trimmed)
            .map_err(|e| ParseError::InvalidFormat(format!("JSON parse error: {}", e)))?;

        let mut fields = HashMap::new();

        if !self.field_mapping.is_empty() {
            for (json_path, field_name) in &self.field_mapping {
                if let Some(value) = self.extract_value_by_path(&json_value, json_path) {
                    fields.insert(field_name.clone(), value);
                }
            }
        } else {
            if let Value::Object(obj) = json_value {
                for (k, v) in obj {
                    fields.insert(k, v);
                }
            }
        }

        let timestamp = fields
            .get("timestamp")
            .and_then(|v| v.as_str())
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
            .map(|dt| dt.with_timezone(&Utc))
            .unwrap_or_else(|| Utc::now());

        let parse_duration_us = start.elapsed().as_micros() as u64;

        Ok(ParsedEvent::new(
            timestamp,
            fields,
            trimmed.to_string(),
            self.name().to_string(),
            parse_duration_us,
        ))
    }

    fn supports_streaming(&self) -> bool {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_json_parser_simple() {
        let parser = JsonParser::new("test_json".to_string(), None);
        let json_line = r#"{"level":"INFO","message":"test message","timestamp":"2025-01-01T00:00:00Z"}"#;

        let result = parser.parse(json_line).await;
        assert!(result.is_ok());

        let event = result.unwrap();
        assert_eq!(event.parser_id, "test_json");
        assert_eq!(event.fields.get("level").and_then(|v| v.as_str()), Some("INFO"));
    }

    #[tokio::test]
    async fn test_json_parser_field_mapping() {
        let mut mapping = HashMap::new();
        mapping.insert("$.msg".to_string(), "message".to_string());
        mapping.insert("$.lvl".to_string(), "level".to_string());

        let parser = JsonParser::new("test_json".to_string(), Some(mapping));
        let json_line = r#"{"msg":"hello","lvl":"WARN"}"#;

        let result = parser.parse(json_line).await;
        assert!(result.is_ok());

        let event = result.unwrap();
        assert_eq!(event.fields.get("message").and_then(|v| v.as_str()), Some("hello"));
        assert_eq!(event.fields.get("level").and_then(|v| v.as_str()), Some("WARN"));
    }

    #[test]
    fn test_json_confidence_detection() {
        let valid_json = r#"{"test": "value"}"#;
        let invalid_json = "not json";
        let array_json = r#"[{"item": "value"}]"#;

        assert_eq!(JsonParser::detect_json_confidence(valid_json), 0.95);
        assert_eq!(JsonParser::detect_json_confidence(invalid_json), 0.0);
        assert_eq!(JsonParser::detect_json_confidence(array_json), 0.95);
    }
}
