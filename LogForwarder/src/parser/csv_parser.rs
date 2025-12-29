use crate::parser::{LogParser, ParsedEvent, ParseError, ParseResult};
use chrono::Utc;
use serde_json::json;
use std::collections::HashMap;
use std::time::Instant;

pub struct CsvParser {
    name: String,
    headers: Vec<String>,
    delimiter: char,
    timestamp_field: Option<String>,
}

impl CsvParser {
    pub fn new(
        name: String,
        headers: Vec<String>,
        delimiter: char,
        timestamp_field: Option<String>,
    ) -> Self {
        CsvParser {
            name,
            headers,
            delimiter,
            timestamp_field,
        }
    }

    fn parse_csv_line(&self, line: &str) -> Vec<String> {
        let mut values = Vec::new();
        let mut current = String::new();
        let mut in_quotes = false;
        let mut chars = line.chars().peekable();

        while let Some(ch) = chars.next() {
            match ch {
                '"' => {
                    if in_quotes && chars.peek() == Some(&'"') {
                        current.push('"');
                        chars.next();
                    } else {
                        in_quotes = !in_quotes;
                    }
                }
                c if c == self.delimiter && !in_quotes => {
                    values.push(current.trim().to_string());
                    current.clear();
                }
                _ => current.push(ch),
            }
        }

        values.push(current.trim().to_string());
        values
    }

    fn detect_csv_structure(sample: &str, headers: &[String]) -> bool {
        let parts: Vec<&str> = sample.split(',').collect();
        parts.len() >= headers.len() / 2
    }
}

#[async_trait::async_trait]
impl LogParser for CsvParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, sample: &str) -> f64 {
        if Self::detect_csv_structure(sample, &self.headers) {
            0.80
        } else {
            0.0
        }
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();
        let trimmed = line.trim();

        if trimmed.is_empty() {
            return Err(ParseError::ParseFailed("Empty line".to_string()));
        }

        let values = self.parse_csv_line(trimmed);

        if values.len() != self.headers.len() {
            return Err(ParseError::FieldExtraction(format!(
                "Expected {} fields, got {}",
                self.headers.len(),
                values.len()
            )));
        }

        let mut fields = HashMap::new();

        for (header, value) in self.headers.iter().zip(values.iter()) {
            fields.insert(header.clone(), json!(value));
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
    async fn test_csv_parser_basic() {
        let headers = vec![
            "timestamp".to_string(),
            "level".to_string(),
            "message".to_string(),
        ];
        let parser = CsvParser::new("test_csv".to_string(), headers, ',', Some("timestamp".to_string()));

        let line = "2025-01-01T10:30:45Z,INFO,test message";
        let result = parser.parse(line).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert_eq!(event.fields.get("level").and_then(|v| v.as_str()), Some("INFO"));
        assert_eq!(event.fields.get("message").and_then(|v| v.as_str()), Some("test message"));
    }

    #[tokio::test]
    async fn test_csv_parser_quoted_values() {
        let headers = vec!["name".to_string(), "description".to_string()];
        let parser = CsvParser::new("test_csv".to_string(), headers, ',', None);

        let line = r#""John Doe","A person with, commas""#;
        let result = parser.parse(line).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert_eq!(event.fields.get("name").and_then(|v| v.as_str()), Some("John Doe"));
    }

    #[tokio::test]
    async fn test_csv_parser_field_count_mismatch() {
        let headers = vec!["col1".to_string(), "col2".to_string()];
        let parser = CsvParser::new("test_csv".to_string(), headers, ',', None);

        let line = "value1,value2,value3";
        let result = parser.parse(line).await;

        assert!(result.is_err());
    }
}
