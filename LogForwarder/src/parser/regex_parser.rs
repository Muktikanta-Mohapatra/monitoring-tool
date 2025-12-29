use crate::parser::{LogParser, ParsedEvent, ParseError, ParseResult};
use chrono::Utc;
use regex::Regex;
use serde_json::json;
use std::collections::HashMap;
use std::time::Instant;

pub struct RegexParser {
    name: String,
    pattern: Regex,
    field_names: Vec<String>,
    timestamp_field: Option<String>,
}

impl RegexParser {
    pub fn new(
        name: String,
        pattern: String,
        field_names: Vec<String>,
        timestamp_field: Option<String>,
    ) -> Result<Self, ParseError> {
        let regex = Regex::new(&pattern)
            .map_err(|e| ParseError::InvalidFormat(format!("Invalid regex pattern: {}", e)))?;

        let capture_count = regex.captures_len();
        if capture_count - 1 != field_names.len() {
            return Err(ParseError::InvalidFormat(format!(
                "Pattern has {} capture groups but {} field names provided",
                capture_count - 1,
                field_names.len()
            )));
        }

        Ok(RegexParser {
            name,
            pattern: regex,
            field_names,
            timestamp_field,
        })
    }

    pub fn detect_regex_confidence(line: &str, pattern: &Regex) -> f64 {
        if pattern.is_match(line) {
            0.85
        } else {
            0.0
        }
    }
}

#[async_trait::async_trait]
impl LogParser for RegexParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, sample: &str) -> f64 {
        Self::detect_regex_confidence(sample, &self.pattern)
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();
        let trimmed = line.trim();

        let captures = self
            .pattern
            .captures(trimmed)
            .ok_or_else(|| ParseError::ParseFailed("No regex match".to_string()))?;

        let mut fields = HashMap::new();

        for (idx, field_name) in self.field_names.iter().enumerate() {
            if let Some(cap) = captures.get(idx + 1) {
                let value = cap.as_str();
                fields.insert(
                    field_name.clone(),
                    json!(value),
                );
            }
        }

        let timestamp = if let Some(ts_field) = &self.timestamp_field {
            fields
                .get(ts_field)
                .and_then(|v| v.as_str())
                .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
                .map(|dt| dt.with_timezone(&Utc))
                .unwrap_or_else(|| Utc::now())
        } else {
            Utc::now()
        };

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
    async fn test_regex_parser_basic() {
        let pattern = r#"(\d{4}-\d{2}-\d{2}) \[(\w+)\] (.*)"#.to_string();
        let fields = vec!["date".to_string(), "level".to_string(), "message".to_string()];
        let parser = RegexParser::new("test_regex".to_string(), pattern, fields, None).unwrap();

        let line = "2025-01-01 [INFO] test message";
        let result = parser.parse(line).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert_eq!(event.fields.get("date").and_then(|v| v.as_str()), Some("2025-01-01"));
        assert_eq!(event.fields.get("level").and_then(|v| v.as_str()), Some("INFO"));
        assert_eq!(event.fields.get("message").and_then(|v| v.as_str()), Some("test message"));
    }

    #[test]
    fn test_regex_parser_creation_mismatch() {
        let pattern = r#"(\d{4}) (\w+)"#.to_string();
        let fields = vec!["year".to_string()];
        let result = RegexParser::new("test".to_string(), pattern, fields, None);

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_regex_no_match() {
        let pattern = r#"(\d{4}-\d{2}-\d{2})"#.to_string();
        let fields = vec!["date".to_string()];
        let parser = RegexParser::new("test_regex".to_string(), pattern, fields, None).unwrap();

        let line = "no date here";
        let result = parser.parse(line).await;

        assert!(result.is_err());
    }
}
