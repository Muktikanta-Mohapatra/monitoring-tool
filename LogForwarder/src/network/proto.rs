use prost::Message;
use serde::{Deserialize, Serialize};

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct Event {
    #[prost(bytes, tag = "1")]
    pub raw_data: Vec<u8>,

    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,

    #[prost(int64, tag = "3")]
    pub timestamp_nanos: i64,

    #[prost(uint64, tag = "4")]
    pub offset: u64,

    #[prost(string, tag = "5")]
    pub source_id: String,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct Metadata {
    #[prost(string, tag = "1")]
    pub source: String,

    #[prost(string, tag = "2")]
    pub sourcetype: String,

    #[prost(string, tag = "3")]
    pub host: String,

    #[prost(string, tag = "4")]
    pub index: String,

    #[prost(map = "string, string", tag = "5")]
    pub tags: std::collections::HashMap<String, String>,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct EventBatch {
    #[prost(message, repeated, tag = "1")]
    pub events: Vec<Event>,

    #[prost(enumeration = "CompressionType", tag = "2")]
    pub compression: i32,

    #[prost(bytes, tag = "3")]
    pub compressed_data: Vec<u8>,

    #[prost(uint32, tag = "4")]
    pub uncompressed_size: u32,

    #[prost(uint32, tag = "5")]
    pub checksum: u32,

    #[prost(uint64, tag = "6")]
    pub batch_id: u64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[repr(i32)]
pub enum CompressionType {
    #[default]
    None = 0,
    Zstd = 1,
    Lz4 = 2,
}

impl From<i32> for CompressionType {
    fn from(v: i32) -> Self {
        match v {
            1 => CompressionType::Zstd,
            2 => CompressionType::Lz4,
            _ => CompressionType::None,
        }
    }
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct AckResponse {
    #[prost(uint64, tag = "1")]
    pub batch_id: u64,

    #[prost(bool, tag = "2")]
    pub success: bool,

    #[prost(uint32, tag = "3")]
    pub events_received: u32,

    #[prost(string, tag = "4")]
    pub error_message: String,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct HealthCheckRequest {
    #[prost(string, tag = "1")]
    pub forwarder_id: String,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct HealthCheckResponse {
    #[prost(bool, tag = "1")]
    pub healthy: bool,

    #[prost(double, tag = "2")]
    pub cpu_usage: f64,

    #[prost(double, tag = "3")]
    pub memory_usage_mb: f64,

    #[prost(uint64, tag = "4")]
    pub queue_depth: u64,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct CapacityRequest {
    #[prost(string, tag = "1")]
    pub forwarder_id: String,
}

#[derive(Clone, PartialEq, Message, Serialize, Deserialize)]
pub struct CapacityResponse {
    #[prost(uint64, tag = "1")]
    pub available_capacity_bytes: u64,

    #[prost(uint32, tag = "2")]
    pub max_events_per_second: u32,

    #[prost(bool, tag = "3")]
    pub accepting_connections: bool,
}
