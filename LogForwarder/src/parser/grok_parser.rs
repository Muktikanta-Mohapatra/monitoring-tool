use crate::parser::{LogParser, ParsedEvent, ParseError, ParseResult};
use chrono::Utc;
use regex::Regex;
use serde_json::json;
use std::collections::HashMap;
use std::time::Instant;

pub struct GrokParser {
    name: String,
    pattern: Regex,
    field_names: Vec<String>,
    timestamp_field: Option<String>,
}

impl GrokParser {
    pub fn new(
        name: String,
        grok_pattern: String,
        timestamp_field: Option<String>,
    ) -> Result<Self, ParseError> {
        let regex_pattern = Self::convert_grok_to_regex(&grok_pattern)?;
        let regex = Regex::new(&regex_pattern)
            .map_err(|e| ParseError::InvalidFormat(format!("Invalid Grok pattern: {}", e)))?;

        let field_names = Self::extract_field_names(&grok_pattern);

        Ok(GrokParser {
            name,
            pattern: regex,
            field_names,
            timestamp_field,
        })
    }

    fn convert_grok_to_regex(grok_pattern: &str) -> Result<String, ParseError> {
        let patterns = [
            ("COMBINEDAPACHELOG", r#"(?<clientip>\S+) \S+ (?<user>\S+) \[(?<timestamp>.*?)\] "(?<verb>\S+) (?<request>\S+) (?<httpversion>HTTP/\S+)" (?<response>\d+|-) (?<bytes>\d+|-) "(?<referrer>.*?)" "(?<agent>.*?)""#),
            ("HTTPD20_ERRORLOG", r#"\[(?<timestamp>.*?)\] \[(?<module>.*?):(?<level>.*?)\] \[pid (?<pid>\d+)(?::tid (?<tid>\d+))?\](?: \[client (?<clientip>.*?)\])? (?<message>.*)"#),
            ("SYSLOGBASE", r#"(?<timestamp>(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(?:[ \d]\d|\d{2}) (?:2[0-3]|[01]?[0-9]):(?:[0-5][0-9]):(?:[0-5][0-9])) (?:(?<logsource>[\w._-]+)|(?<hostname>[\w._@-]+)) (?<programname>[\w.\[\]-]*?)(?:\[(?<pid>\d+)\])?(?:: (?<message>.*)|(?<message>)))"#),
            ("TIMESTAMP_ISO8601", r#"(?<timestamp>\d{4}-\d{2}-\d{2}[T ]?\d{2}:\d{2}:\d{2}(?:[+-]\d{2}:\d{2})?)"#),
            ("QS", r#""(?:\\.|[^\\"])*""#),
            ("WORD", r#"\b\w+\b"#),
            ("NUMBER", r#"(?:%{BASE10NUM})"#),
            ("BASE10NUM", r#"([+\-]?)(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)"#),
            ("IP", r#"(?<![0-9])(?:(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2}))(?![0-9])"#),
        ];

        let mut result = grok_pattern.to_string();

        for (grok_name, regex_pattern) in &patterns {
            result = result.replace(&format!("%{{{}}}", grok_name), regex_pattern);
        }

        result = result.replace("%{IP:clientip}", r#"(?<clientip>(?<![0-9])(?:(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[.](?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2}))(?![0-9]))"#);

        Ok(result)
    }

    fn extract_field_names(grok_pattern: &str) -> Vec<String> {
        let mut fields = Vec::new();
        let mut i = 0;
        let chars: Vec<char> = grok_pattern.chars().collect();

        while i < chars.len() {
            if chars[i] == '<' {
                let mut name = String::new();
                i += 1;
                while i < chars.len() && chars[i] != '>' {
                    name.push(chars[i]);
                    i += 1;
                }
                if !name.is_empty() && !fields.contains(&name) {
                    fields.push(name);
                }
            }
            i += 1;
        }

        fields
    }
}

#[async_trait::async_trait]
impl LogParser for GrokParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, sample: &str) -> f64 {
        if self.pattern.is_match(sample) {
            0.90
        } else {
            0.0
        }
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();
        let trimmed = line.trim();

        let captures = self
            .pattern
            .captures(trimmed)
            .ok_or_else(|| ParseError::ParseFailed("Grok pattern did not match".to_string()))?;

        let mut fields = HashMap::new();

        for field_name in &self.field_names {
            if let Some(cap) = captures.name(field_name) {
                fields.insert(field_name.clone(), json!(cap.as_str()));
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
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_grok_parser_basic() {
        let pattern = r#"(?<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \[(?<level>\w+)\] (?<message>.*)"#.to_string();
        let parser = GrokParser::new("test_grok".to_string(), pattern, None).unwrap();

        let line = "2025-01-01 10:30:45 [INFO] test message";
        let result = parser.parse(line).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert!(event.fields.get("level").is_some());
    }

    #[test]
    fn test_grok_pattern_conversion() {
        let result = GrokParser::convert_grok_to_regex("%{COMBINEDAPACHELOG}");
        assert!(result.is_ok());
        let regex_str = result.unwrap();
        assert!(regex_str.contains("clientip"));
    }
}
