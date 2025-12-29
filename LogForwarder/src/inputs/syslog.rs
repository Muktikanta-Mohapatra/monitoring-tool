use crate::event::{Event, SourceType};
use bytes::Bytes;
use chrono::DateTime;
use std::net::SocketAddr;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SyslogParseError {
    #[error("Invalid syslog message")]
    InvalidMessage,
    #[error("Invalid priority")]
    InvalidPriority,
    #[error("Invalid timestamp")]
    InvalidTimestamp,
}

#[derive(Debug, Clone)]
pub struct SyslogMessage {
    pub priority: u32,
    pub facility: u32,
    pub severity: u32,
    pub timestamp: Option<DateTime<chrono::Utc>>,
    pub hostname: Option<String>,
    pub tag: Option<String>,
    pub message: String,
    pub raw: String,
}

impl SyslogMessage {
    pub fn parse_bsd(line: &str) -> Result<Self, SyslogParseError> {
        if line.is_empty() {
            return Err(SyslogParseError::InvalidMessage);
        }

        let mut message = line.to_string();
        let raw = message.clone();

        if let Some(priority_end) = message.find('>') {
            if message.starts_with('<') {
                let priority_str = &message[1..priority_end];
                let priority = priority_str
                    .parse::<u32>()
                    .map_err(|_| SyslogParseError::InvalidPriority)?;

                let facility = priority >> 3;
                let severity = priority & 0x07;

                message = message[priority_end + 1..].to_string();

                let facility_and_severity = (facility, severity);

                let parts: Vec<&str> = message.split_whitespace().collect();

                if parts.len() >= 2 {
                    if let Ok(ts) = parse_bsd_timestamp(parts[0]) {
                        let timestamp = Some(ts);
                        let hostname = Some(parts[1].to_string());
                        let rest = parts[2..].join(" ");

                        let tag = rest
                            .find('[')
                            .or_else(|| rest.find(':'))
                            .map(|tag_end| rest[..tag_end].to_string());

                        return Ok(SyslogMessage {
                            priority,
                            facility: facility_and_severity.0,
                            severity: facility_and_severity.1,
                            timestamp,
                            hostname,
                            tag,
                            message: rest,
                            raw,
                        });
                    }
                }

                return Ok(SyslogMessage {
                    priority,
                    facility: facility_and_severity.0,
                    severity: facility_and_severity.1,
                    timestamp: None,
                    hostname: None,
                    tag: None,
                    message,
                    raw,
                });
            }
        }

        Ok(SyslogMessage {
            priority: 13,
            facility: 1,
            severity: 5,
            timestamp: None,
            hostname: None,
            tag: None,
            message: line.to_string(),
            raw,
        })
    }

    pub fn parse_rfc5424(line: &str) -> Result<Self, SyslogParseError> {
        if !line.starts_with('<') {
            return Err(SyslogParseError::InvalidMessage);
        }

        if let Some(priority_end) = line.find('>') {
            let priority_str = &line[1..priority_end];
            let priority = priority_str
                .parse::<u32>()
                .map_err(|_| SyslogParseError::InvalidPriority)?;

            let facility = priority >> 3;
            let severity = priority & 0x07;

            let rest = &line[priority_end + 1..];

            if rest.starts_with('1') {
                let parts: Vec<&str> = rest.split_whitespace().collect();

                if parts.len() >= 3 {
                    let timestamp = parts[1].parse::<chrono::DateTime<chrono::Utc>>().ok();
                    let hostname = Some(parts[2].to_string());
                    let tag = if parts.len() > 3 {
                        Some(parts[3].to_string())
                    } else {
                        None
                    };
                    let message = parts[4..].join(" ");

                    return Ok(SyslogMessage {
                        priority,
                        facility,
                        severity,
                        timestamp,
                        hostname,
                        tag,
                        message,
                        raw: line.to_string(),
                    });
                }
            }
        }

        Err(SyslogParseError::InvalidMessage)
    }
}

fn parse_bsd_timestamp(s: &str) -> Result<DateTime<chrono::Utc>, SyslogParseError> {
    if let Ok(dt) = DateTime::parse_from_rfc3339(&format!("2025-{}Z", s)) {
        return Ok(dt.with_timezone(&chrono::Utc));
    }

    if let Ok(naive_dt) =
        chrono::NaiveDateTime::parse_from_str(&format!("2025-{} 00:00:00", s), "%Y-%b %d %H:%M:%S")
    {
        return Ok(DateTime::<chrono::Utc>::from_naive_utc_and_offset(
            naive_dt,
            chrono::Utc,
        ));
    }

    Err(SyslogParseError::InvalidTimestamp)
}

pub fn create_event_from_syslog(
    syslog: &SyslogMessage,
    source_addr: SocketAddr,
    index_id: u16,
) -> Event {
    let timestamp = syslog
        .timestamp
        .map(|t| t.timestamp_nanos_opt().unwrap_or(0))
        .unwrap_or_else(|| {
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos() as i64
        });

    let source_str = format!("tcp:{}:{}", source_addr.ip(), source_addr.port());
    let source_id = fnv_hash(&source_str) as u32;

    Event::new(
        Bytes::from(syslog.raw.clone()),
        timestamp,
        0,
        source_id,
        SourceType::Syslog,
        fnv_hash(&source_addr.ip().to_string()) as u32,
        index_id,
    )
}

pub fn create_event_from_udp(data: &[u8], source_addr: SocketAddr, index_id: u16) -> Event {
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos() as i64;

    let source_str = format!("udp:{}:{}", source_addr.ip(), source_addr.port());
    let source_id = fnv_hash(&source_str) as u32;

    Event::new(
        Bytes::copy_from_slice(data),
        timestamp,
        0,
        source_id,
        SourceType::Syslog,
        fnv_hash(&source_addr.ip().to_string()) as u32,
        index_id,
    )
}

fn fnv_hash(s: &str) -> u64 {
    const FNV_PRIME: u64 = 1099511628211;
    const FNV_OFFSET_BASIS: u64 = 14695981039346656037;

    let mut hash = FNV_OFFSET_BASIS;
    for byte in s.bytes() {
        hash ^= byte as u64;
        hash = hash.wrapping_mul(FNV_PRIME);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_bsd_syslog() {
        let line = "<34>Oct 11 22:14:15 mymachine su: 'su root' failed for lonvick";
        let msg = SyslogMessage::parse_bsd(line).unwrap();

        assert_eq!(msg.priority, 34);
        assert_eq!(msg.facility, 4);
        assert_eq!(msg.severity, 2);
    }

    #[test]
    fn test_parse_invalid() {
        let result = SyslogMessage::parse_bsd("");
        assert!(result.is_err());
    }
}
