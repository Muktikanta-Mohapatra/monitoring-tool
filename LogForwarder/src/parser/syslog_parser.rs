use crate::parser::{LogParser, ParsedEvent, ParseError, ParseResult};
use chrono::Utc;
use regex::Regex;
use serde_json::json;
use std::collections::HashMap;
use std::time::Instant;

pub enum SyslogFormat {
    RFC3164,
    RFC5424,
}

pub struct SyslogParser {
    name: String,
    format: SyslogFormat,
    rfc3164_regex: Option<Regex>,
    rfc5424_regex: Option<Regex>,
}

impl SyslogParser {
    pub fn new(name: String, format: SyslogFormat) -> Result<Self, ParseError> {
        let (rfc3164_regex, rfc5424_regex) = match format {
            SyslogFormat::RFC3164 => {
                let pattern = Regex::new(
                    r#"^(?:(?<timestamp>Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(?:\d{1,2})\s+(?:\d{2}):(?:\d{2}):(?:\d{2}))\s+(?<hostname>\S+)\s+(?<tag>[\w.-]*)\[?(?<pid>\d+)?\]?:?\s+(?<message>.*)"#
                )
                .map_err(|e| ParseError::InvalidFormat(format!("RFC3164 regex error: {}", e)))?;
                (Some(pattern), None)
            }
            SyslogFormat::RFC5424 => {
                let pattern = Regex::new(
                    r#"^<(?<priority>\d+)>(?<version>\d)?\s+(?<timestamp>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[+-]\d{2}:\d{2}|Z)?)\s+(?<hostname>\S+)\s+(?<app_name>\S+)\s+(?<proc_id>\S+)\s+(?<msg_id>\S+)\s+(?:\[(?<structured_data>[^\]]*)\])?\s*(?<message>.*)"#
                )
                .map_err(|e| ParseError::InvalidFormat(format!("RFC5424 regex error: {}", e)))?;
                (None, Some(pattern))
            }
        };

        Ok(SyslogParser {
            name,
            format,
            rfc3164_regex,
            rfc5424_regex,
        })
    }

    fn parse_rfc3164(&self, line: &str) -> ParseResult<HashMap<String, serde_json::Value>> {
        let regex = self.rfc3164_regex.as_ref()
            .ok_or_else(|| ParseError::ParseFailed("RFC3164 regex not initialized".to_string()))?;

        let captures = regex
            .captures(line)
            .ok_or_else(|| ParseError::ParseFailed("RFC3164 pattern did not match".to_string()))?;

        let mut fields = HashMap::new();
        fields.insert("timestamp".to_string(), json!(captures.name("timestamp").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("hostname".to_string(), json!(captures.name("hostname").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("tag".to_string(), json!(captures.name("tag").map(|m| m.as_str()).unwrap_or("")));
        
        if let Some(pid) = captures.name("pid") {
            fields.insert("pid".to_string(), json!(pid.as_str().parse::<u32>().unwrap_or(0)));
        }
        
        fields.insert("message".to_string(), json!(captures.name("message").map(|m| m.as_str()).unwrap_or("")));

        Ok(fields)
    }

    fn parse_rfc5424(&self, line: &str) -> ParseResult<HashMap<String, serde_json::Value>> {
        let regex = self.rfc5424_regex.as_ref()
            .ok_or_else(|| ParseError::ParseFailed("RFC5424 regex not initialized".to_string()))?;

        let captures = regex
            .captures(line)
            .ok_or_else(|| ParseError::ParseFailed("RFC5424 pattern did not match".to_string()))?;

        let mut fields = HashMap::new();
        
        if let Some(priority) = captures.name("priority") {
            let priority_val: u32 = priority.as_str().parse().unwrap_or(0);
            fields.insert("priority".to_string(), json!(priority_val));
            fields.insert("severity".to_string(), json!(priority_val % 8));
            fields.insert("facility".to_string(), json!(priority_val / 8));
        }

        if let Some(version) = captures.name("version") {
            fields.insert("version".to_string(), json!(version.as_str()));
        }

        fields.insert("timestamp".to_string(), json!(captures.name("timestamp").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("hostname".to_string(), json!(captures.name("hostname").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("app_name".to_string(), json!(captures.name("app_name").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("proc_id".to_string(), json!(captures.name("proc_id").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("msg_id".to_string(), json!(captures.name("msg_id").map(|m| m.as_str()).unwrap_or("")));
        fields.insert("message".to_string(), json!(captures.name("message").map(|m| m.as_str()).unwrap_or("")));

        if let Some(structured) = captures.name("structured_data") {
            fields.insert("structured_data".to_string(), json!(structured.as_str()));
        }

        Ok(fields)
    }
}

#[async_trait::async_trait]
impl LogParser for SyslogParser {
    fn name(&self) -> &str {
        &self.name
    }

    fn can_parse(&self, sample: &str) -> f64 {
        match self.format {
            SyslogFormat::RFC5424 => {
                if sample.starts_with('<') && sample.contains("Z") || sample.contains("+") {
                    0.92
                } else {
                    0.0
                }
            }
            SyslogFormat::RFC3164 => {
                if sample.contains("Jan") || sample.contains("Feb") || sample.contains("Mar") 
                    || sample.contains("Apr") || sample.contains("May") || sample.contains("Jun")
                    || sample.contains("Jul") || sample.contains("Aug") || sample.contains("Sep")
                    || sample.contains("Oct") || sample.contains("Nov") || sample.contains("Dec") {
                    0.88
                } else {
                    0.0
                }
            }
        }
    }

    async fn parse(&self, line: &str) -> ParseResult<ParsedEvent> {
        let start = Instant::now();

        let fields = match self.format {
            SyslogFormat::RFC3164 => self.parse_rfc3164(line)?,
            SyslogFormat::RFC5424 => self.parse_rfc5424(line)?,
        };

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
    async fn test_syslog_rfc5424() {
        let parser = SyslogParser::new("test_syslog5424".to_string(), SyslogFormat::RFC5424).unwrap();
        let line = r#"<134>1 2025-01-01T10:30:45Z myhost myapp 1234 ID47 - test message"#;

        let result = parser.parse(line).await;
        assert!(result.is_ok());

        let event = result.unwrap();
        assert_eq!(event.fields.get("app_name").and_then(|v| v.as_str()), Some("myapp"));
    }

    #[tokio::test]
    async fn test_syslog_rfc3164() {
        let parser = SyslogParser::new("test_syslog3164".to_string(), SyslogFormat::RFC3164).unwrap();
        let line = "Jan  1 10:30:45 myhost myapp[1234]: test message";

        let result = parser.parse(line).await;
        assert!(result.is_ok());

        let event = result.unwrap();
        assert_eq!(event.fields.get("hostname").and_then(|v| v.as_str()), Some("myhost"));
    }
}
