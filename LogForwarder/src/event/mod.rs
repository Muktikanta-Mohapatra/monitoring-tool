use crate::queue::QueueItem;
use bytes::Bytes;
use std::sync::Arc;
use std::collections::HashMap;
use serde_json::Value;

pub mod severity;
pub use severity::Severity;

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum SourceType {
    Unknown = 0,
    ApacheAccess = 1,
    ApacheError = 2,
    Json = 3,
    Syslog = 4,
    Csv = 5,
}

impl SourceType {
    pub fn from_u8(val: u8) -> Self {
        match val {
            1 => SourceType::ApacheAccess,
            2 => SourceType::ApacheError,
            3 => SourceType::Json,
            4 => SourceType::Syslog,
            5 => SourceType::Csv,
            _ => SourceType::Unknown,
        }
    }
}

#[derive(Clone, Debug)]
pub struct EnrichedEventMetadata {
    pub parsed_timestamp: chrono::DateTime<chrono::Utc>,
    pub detected_format: String,
    pub parse_duration_us: u64,
    pub parsed_fields: HashMap<String, Value>,
    pub enriched_fields: HashMap<String, Value>,
}

#[derive(Clone)]
pub struct Event {
    raw_data: Arc<[u8]>,
    timestamp: i64,
    offset: u64,
    source_id: u32,
    sourcetype: SourceType,
    severity: Severity,
    forwarder_id: Option<String>,
    source_name: Option<Arc<str>>,
    host_id: u32,
    index_id: u16,
    flags: u8,
    enriched_metadata: Option<Arc<EnrichedEventMetadata>>,
}

impl Event {
    pub fn new(
        raw_data: Bytes,
        timestamp: i64,
        offset: u64,
        source_id: u32,
        sourcetype: SourceType,
        host_id: u32,
        index_id: u16,
    ) -> Self {
        Event {
            raw_data: Arc::from(raw_data.to_vec().into_boxed_slice()),
            timestamp,
            offset,
            source_id,
            sourcetype,
            severity: Severity::Unknown,
            forwarder_id: None,
            source_name: None,
            host_id,
            index_id,
            flags: 0,
            enriched_metadata: None,
        }
    }

    pub fn with_severity(mut self, severity: Severity) -> Self {
        self.severity = severity;
        self
    }

    pub fn with_forwarder_id(mut self, forwarder_id: String) -> Self {
        self.forwarder_id = Some(forwarder_id);
        self
    }

    pub fn with_source_name(mut self, source_name: Arc<str>) -> Self {
        self.source_name = Some(source_name);
        self
    }

    #[inline]
    pub fn raw_data(&self) -> &[u8] {
        &self.raw_data
    }

    #[inline]
    pub fn timestamp(&self) -> i64 {
        self.timestamp
    }

    #[inline]
    pub fn offset(&self) -> u64 {
        self.offset
    }

    #[inline]
    pub fn source_id(&self) -> u32 {
        self.source_id
    }

    #[inline]
    pub fn sourcetype(&self) -> SourceType {
        self.sourcetype
    }

    #[inline]
    pub fn host_id(&self) -> u32 {
        self.host_id
    }

    #[inline]
    pub fn index_id(&self) -> u16 {
        self.index_id
    }

    #[inline]
    pub fn flags(&self) -> u8 {
        self.flags
    }

    #[inline]
    pub fn size(&self) -> usize {
        std::mem::size_of::<Self>() + self.raw_data.len()
    }

    #[inline]
    pub fn enriched_metadata(&self) -> Option<&Arc<EnrichedEventMetadata>> {
        self.enriched_metadata.as_ref()
    }

    pub fn with_enriched_metadata(mut self, metadata: EnrichedEventMetadata) -> Self {
        self.enriched_metadata = Some(Arc::new(metadata));
        self
    }

    #[inline]
    pub fn severity(&self) -> Severity {
        self.severity
    }

    #[inline]
    pub fn forwarder_id(&self) -> Option<&str> {
        self.forwarder_id.as_deref()
    }

    #[inline]
    pub fn source_name(&self) -> Option<&str> {
        self.source_name.as_deref()
    }
}

impl QueueItem for Event {
    fn size(&self) -> usize {
        Event::size(self)
    }
}

impl std::fmt::Debug for Event {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Event")
            .field("timestamp", &self.timestamp)
            .field("offset", &self.offset)
            .field("source_id", &self.source_id)
            .field("sourcetype", &self.sourcetype)
            .field("host_id", &self.host_id)
            .field("index_id", &self.index_id)
            .field("data_len", &self.raw_data.len())
            .field("has_enriched_metadata", &self.enriched_metadata.is_some())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_event_size() {
        let event = Event::new(
            Bytes::from("test data"),
            1699000000000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        );
        assert!(event.size() < 512);
    }

    #[test]
    fn test_event_zero_copy() {
        let data = Bytes::from("shared data");
        let event1 = Event::new(data.clone(), 1000, 0, 1, SourceType::Json, 1, 1);
        let event2 = event1.clone();
        assert_eq!(event1.raw_data(), event2.raw_data());
        assert!(Arc::ptr_eq(&event1.raw_data, &event2.raw_data));
    }

    #[test]
    fn test_event_with_severity() {
        let event = Event::new(
            Bytes::from("test"),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        ).with_severity(Severity::Error);
        
        assert_eq!(event.severity(), Severity::Error);
    }

    #[test]
    fn test_event_with_forwarder_id() {
        let event = Event::new(
            Bytes::from("test"),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        ).with_forwarder_id("forwarder-123".to_string());
        
        assert_eq!(event.forwarder_id(), Some("forwarder-123"));
    }

    #[test]
    fn test_event_with_source_name() {
        let event = Event::new(
            Bytes::from("test"),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        ).with_source_name(Arc::from("app12.log"));
        
        assert_eq!(event.source_name(), Some("app12.log"));
    }

    #[test]
    fn test_event_new_defaults() {
        let event = Event::new(
            Bytes::from("test"),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        );
        
        assert_eq!(event.severity(), Severity::Unknown);
        assert_eq!(event.forwarder_id(), None);
        assert_eq!(event.source_name(), None);
    }
}
