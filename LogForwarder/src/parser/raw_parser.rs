use crate::parser::{LogParser, ParsedEvent, ParseResult};
use chrono::Utc;
use serde_json::json;
use std::collections::HashMap;
use std::time::Instant;

pub struct RawParser {
    name: String,
}

impl RawParser {
    pub fn new(name: String) -> Self {
        RawParser { name }
    }
}

#[async_trait::async_trait]
impl LogParser for RawParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, _sample: &str) -> f64 {
        0.1
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();

        let mut fields = HashMap::new();
        fields.insert("message".to_string(), json!(line));

        let parse_duration_us = start.elapsed().as_micros() as u64;

        Ok(ParsedEvent::new(
            Utc::now(),
            fields,
            line.to_string(),
            self.name().to_string(),
            parse_duration_us,
        ))
    }

    fn supports_streaming(&self) -> bool {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_raw_parser() {
        let parser = RawParser::new("raw".to_string());
        let line = "This is a raw message without any specific format";

        let result = parser.parse(line).await;
        assert!(result.is_ok());

        let event = result.unwrap();
        assert_eq!(event.parser_id, "raw");
        assert_eq!(event.fields.get("message").and_then(|v| v.as_str()), Some(line));
    }

    #[test]
    fn test_raw_parser_confidence() {
        let parser = RawParser::new("raw".to_string());
        assert_eq!(parser.can_parse("any message"), 0.1);
    }
}
