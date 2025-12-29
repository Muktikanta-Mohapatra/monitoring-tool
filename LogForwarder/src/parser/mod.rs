use chrono::{DateTime, Utc};
use serde_json::json;
use std::collections::HashMap;
use thiserror::Error;

mod grok_parser;
mod json_parser;
mod regex_parser;
mod csv_parser;
mod syslog_parser;
mod parser_registry;
mod format_detector;
mod log_pipeline;
mod raw_parser;

pub use grok_parser::GrokParser;
pub use json_parser::JsonParser;
pub use regex_parser::RegexParser;
pub use csv_parser::CsvParser;
pub use syslog_parser::{SyslogParser, SyslogFormat};
pub use parser_registry::ParserRegistry;
pub use format_detector::{FormatDetector, FormatStats};
pub use log_pipeline::{LogPipeline, EnrichedEvent, PipelineError, PipelineResult};
pub use raw_parser::RawParser;

#[derive(Error, Debug)]
pub enum ParseError {
    #[error("Parse failed: {0}")]
    ParseFailed(String),
    #[error("Invalid format: {0}")]
    InvalidFormat(String),
    #[error("Field extraction error: {0}")]
    FieldExtraction(String),
    #[error("Timeout")]
    Timeout,
    #[error("Unknown error: {0}")]
    Unknown(String),
}

pub type ParseResult<T> = Result<T, ParseError>;

#[derive(Debug, Clone)]
pub struct ParsedEvent {
    pub timestamp: DateTime<Utc>,
    pub fields: HashMap<String, serde_json::Value>,
    pub raw_message: String,
    pub parser_id: String,
    pub parse_duration_us: u64,
}

impl ParsedEvent {
    pub fn new(
        timestamp: DateTime<Utc>,
        fields: HashMap<String, serde_json::Value>,
        raw_message: String,
        parser_id: String,
        parse_duration_us: u64,
    ) -> Self {
        ParsedEvent {
            timestamp,
            fields,
            raw_message,
            parser_id,
            parse_duration_us,
        }
    }

    pub fn to_json(&self) -> serde_json::Value {
        let mut obj = json!({
            "timestamp": self.timestamp.to_rfc3339(),
            "parser_id": self.parser_id,
            "parse_duration_us": self.parse_duration_us,
            "raw_message": self.raw_message,
        });

        if let serde_json::Value::Object(ref mut map) = obj {
            for (k, v) in &self.fields {
                map.insert(k.clone(), v.clone());
            }
        }

        obj
    }
}

#[async_trait::async_trait]
pub trait LogParser: Send + Sync {
    fn name(&self) -> &str;
    fn can_parse(&self, sample: &str) -> f64;
    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent>;
    fn supports_streaming(&self) -> bool;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parsed_event_to_json() {
        let mut fields = HashMap::new();
        fields.insert("level".to_string(), json!("INFO"));
        fields.insert("message".to_string(), json!("test message"));

        let event = ParsedEvent::new(
            Utc::now(),
            fields,
            "raw message".to_string(),
            "test_parser".to_string(),
            100,
        );

        let json = event.to_json();
        assert!(json.get("timestamp").is_some());
        assert_eq!(json.get("parser_id").and_then(|v| v.as_str()), Some("test_parser"));
        assert_eq!(json.get("level").and_then(|v| v.as_str()), Some("INFO"));
    }
}
