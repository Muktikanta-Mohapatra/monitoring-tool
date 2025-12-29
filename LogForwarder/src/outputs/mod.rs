pub mod grpc_sender;
pub mod retry_policy;

use crate::compression::{CompressionConfig, CompressionEngine};
use crate::config::OutputConfig;
use crate::event::Event;
use async_trait::async_trait;
use serde_json;
use std::sync::Arc;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum OutputError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Network error: {0}")]
    Network(String),
    #[error("Serialization error: {0}")]
    Serialization(String),
    #[error("gRPC error: {0}")]
    GrpcError(String),
}

#[async_trait]
pub trait OutputSenderTrait: Send + Sync {
    async fn send_batch(&self, batch: EventBatch) -> Result<(), OutputError>;
}

pub struct EventBatch {
    pub events: Vec<Event>,
    pub total_bytes: usize,
}

impl EventBatch {
    pub fn new(events: Vec<Event>) -> Self {
        let total_bytes = events.iter().map(|e| e.size()).sum();
        EventBatch {
            events,
            total_bytes,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }
}

pub struct OutputSender {
    config: OutputConfig,
    client: tokio::sync::Mutex<Option<reqwest::Client>>,
    compression: Option<CompressionEngine>,
}

#[async_trait]
impl OutputSenderTrait for OutputSender {
    async fn send_batch(&self, batch: EventBatch) -> Result<(), OutputError> {
        self.send_batch_internal(batch).await
    }
}

impl OutputSender {
    pub fn new(config: OutputConfig) -> Self {
        let compression = if config.compression_enabled {
            Some(CompressionEngine::new(
                CompressionConfig {
                    level: config.compression_level,
                    adaptive: config.compression_adaptive,
                    use_dictionary: config.compression_use_dict,
                    batch_size_bytes: 1024 * 1024,
                },
                None,
            ))
        } else {
            None
        };

        OutputSender {
            config,
            client: tokio::sync::Mutex::new(None),
            compression,
        }
    }

    pub fn new_with_checkpoint<P: AsRef<std::path::Path>>(
        config: OutputConfig,
        checkpoint_dir: P,
    ) -> Self {
        let compression = if config.compression_enabled {
            Some(CompressionEngine::new(
                CompressionConfig {
                    level: config.compression_level,
                    adaptive: config.compression_adaptive,
                    use_dictionary: config.compression_use_dict,
                    batch_size_bytes: 1024 * 1024,
                },
                Some(checkpoint_dir.as_ref().to_str().unwrap_or("")),
            ))
        } else {
            None
        };

        OutputSender {
            config,
            client: tokio::sync::Mutex::new(None),
            compression,
        }
    }

    pub async fn initialize(&self) -> Result<(), OutputError> {
        let mut client = self.client.lock().await;
        if client.is_none() {
            let builder = reqwest::Client::builder();
            let http_client = builder
                .danger_accept_invalid_certs(!self.config.tls_verify)
                .build()
                .map_err(|e| OutputError::Network(e.to_string()))?;

            *client = Some(http_client);
        }
        Ok(())
    }

    async fn send_batch_internal(&self, batch: EventBatch) -> Result<(), OutputError> {
        if batch.is_empty() {
            return Ok(());
        }

        if std::env::var("FORWARDER_CONSOLE_OUTPUT").is_ok() || self.config.url == "console" {
            self.print_to_console(&batch);
            return Ok(());
        }

        self.initialize().await?;

        let client_guard = self.client.lock().await;
        if let Some(client) = client_guard.as_ref() {
            let payload = self.serialize_batch(&batch)?;
            let final_payload = self.apply_compression(payload.as_bytes())?;

            let mut retry_count = 0;
            loop {
                let api_key_value = if !self.config.api_key.is_empty() {
                    self.config.api_key.clone()
                } else {
                    self.config.token.clone()
                };
                
                let mut req = client
                    .post(&self.config.url)
                    .header("X-API-KEY", api_key_value);

                if self.compression.is_some() {
                    req = req.header("Content-Encoding", "zstd");
                }

                let response_result = req
                    .header("Content-Type", "application/json")
                    .body(final_payload.clone())
                    .send()
                    .await;

                match response_result {
                    Ok(response) => {
                        if response.status().is_success() {
                            return Ok(());
                        } else if retry_count < self.config.retry_count {
                            retry_count += 1;
                            tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                            continue;
                        } else {
                            return Err(OutputError::Network(format!(
                                "HTTP error: {}",
                                response.status()
                            )));
                        }
                    }
                    Err(_e) if retry_count < self.config.retry_count => {
                        retry_count += 1;
                        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                    }
                    Err(e) => return Err(OutputError::Network(e.to_string())),
                }
            }
        }

        Err(OutputError::Network("Client not initialized".to_string()))
    }

    fn apply_compression(&self, data: &[u8]) -> Result<Vec<u8>, OutputError> {
        if let Some(compressor) = &self.compression {
            compressor
                .compress(data, None)
                .map_err(|e| OutputError::Serialization(e.to_string()))
        } else {
            Ok(data.to_vec())
        }
    }

    fn serialize_batch(&self, batch: &EventBatch) -> Result<String, OutputError> {
        use chrono::{DateTime, Utc, TimeZone};
        
        let batch_id = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;
        
        let mut json = String::new();
        json.push_str(r#"{"forwarderId":"#);
        json.push_str(
            &serde_json::to_string(&self.config.forwarder_id)
                .map_err(|e| OutputError::Serialization(e.to_string()))?,
        );
        json.push_str(r#","apiKey":"#);
        json.push_str(
            &serde_json::to_string(&self.config.token)
                .map_err(|e| OutputError::Serialization(e.to_string()))?,
        );
        json.push_str(r#","events":["#);

        for (i, event) in batch.events.iter().enumerate() {
            if i > 0 {
                json.push(',');
            }

            let raw_data_str = String::from_utf8_lossy(event.raw_data());
            let timestamp: DateTime<Utc> = Utc.timestamp_nanos(event.timestamp());
            let sourcetype_str = format!("{:?}", event.sourcetype());
            
            json.push_str(r#"{"timestamp":"#);
            json.push_str(&serde_json::to_string(&timestamp.to_rfc3339())
                .map_err(|e| OutputError::Serialization(e.to_string()))?);
            json.push_str(r#","sourcetype":"#);
            json.push_str(&serde_json::to_string(&sourcetype_str)
                .map_err(|e| OutputError::Serialization(e.to_string()))?);
            json.push_str(r#","rawData":"#);
            json.push_str(&serde_json::to_string(&raw_data_str)
                .map_err(|e| OutputError::Serialization(e.to_string()))?);
            json.push_str(r#","rawMessage":"#);
            json.push_str(&serde_json::to_string(&raw_data_str)
                .map_err(|e| OutputError::Serialization(e.to_string()))?);
            json.push_str(r#","forwarderId":"#);
            json.push_str(&serde_json::to_string(&self.config.forwarder_id)
                .map_err(|e| OutputError::Serialization(e.to_string()))?);
            
            if let Some(enriched_meta) = event.enriched_metadata() {
                json.push_str(r#","severity":"INFO""#);
                json.push_str(r#","detectedFormat":"#);
                json.push_str(&serde_json::to_string(&enriched_meta.detected_format)
                    .map_err(|e| OutputError::Serialization(e.to_string()))?);
                json.push_str(r#","parseDurationUs":"#);
                json.push_str(&enriched_meta.parse_duration_us.to_string());
                json.push_str(r#","parsedFields":"#);
                json.push_str(&serde_json::to_string(&enriched_meta.parsed_fields)
                    .map_err(|e| OutputError::Serialization(e.to_string()))?);
                json.push_str(r#","enrichedFields":"#);
                json.push_str(&serde_json::to_string(&enriched_meta.enriched_fields)
                    .map_err(|e| OutputError::Serialization(e.to_string()))?);
            } else {
                json.push_str(r#","severity":"INFO""#);
                json.push_str(r#","parsedFields":{}"#);
                json.push_str(r#","enrichedFields":{}"#);
            }
            
            json.push('}');
        }

        json.push_str(r#"],"batchId":"#);
        json.push_str(&batch_id.to_string());
        json.push_str(r#","batchSize":"#);
        json.push_str(&batch.len().to_string());
        json.push('}');
        
        Ok(json)
    }

    fn print_to_console(&self, batch: &EventBatch) {
        println!(
            "\n=== Event Batch Output (Output: {}) ===",
            self.config.name
        );
        println!("Total Events: {}", batch.len());
        println!("Total Bytes: {}", batch.total_bytes);
        println!("---");

        for (i, event) in batch.events.iter().enumerate() {
            let data_str = String::from_utf8_lossy(event.raw_data());
            
            if let Some(enriched_meta) = event.enriched_metadata() {
                println!("[Event {}] ENRICHED", i + 1);
                println!("  Raw: {}", data_str);
                println!("  Format: {}", enriched_meta.detected_format);
                println!("  Duration: {}µs", enriched_meta.parse_duration_us);
                println!("  Timestamp: {}", enriched_meta.parsed_timestamp.to_rfc3339());
                println!("  Parsed Fields: {}", serde_json::to_string(&enriched_meta.parsed_fields).unwrap_or_default());
                println!("  Enriched Fields: {}", serde_json::to_string(&enriched_meta.enriched_fields).unwrap_or_default());
            } else {
                println!("[Event {}] {}", i + 1, data_str);
            }
        }

        println!("===================================\n");
    }
}

pub struct OutputPool {
    senders: Arc<Vec<Arc<dyn OutputSenderTrait>>>,
    current: std::sync::atomic::AtomicUsize,
}

impl OutputPool {
    pub fn new(configs: Vec<OutputConfig>, indexers: Vec<crate::config::IndexerConfig>) -> Self {
        let mut senders: Vec<Arc<dyn OutputSenderTrait>> = Vec::new();

        for config in configs {
            let protocol = config.protocol.to_lowercase();
            let sender: Arc<dyn OutputSenderTrait> = if protocol == "grpc" {
                Arc::new(grpc_sender::GrpcOutputSender::new_from_config(
                    config,
                    indexers.clone(),
                ))
            } else {
                Arc::new(OutputSender::new(config))
            };
            senders.push(sender);
        }

        OutputPool {
            senders: Arc::new(senders),
            current: std::sync::atomic::AtomicUsize::new(0),
        }
    }

    pub fn get_next_sender(&self) -> Arc<dyn OutputSenderTrait> {
        let idx = self
            .current
            .fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        Arc::clone(&self.senders[idx % self.senders.len()])
    }

    pub async fn send_to_all(&self, batch: EventBatch) -> Result<(), OutputError> {
        for sender in self.senders.iter() {
            sender
                .send_batch(EventBatch::new(batch.events.clone()))
                .await?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::event::SourceType;

    #[test]
    fn test_event_batch() {
        let event = Event::new(
            bytes::Bytes::from("test data"),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        );
        let batch = EventBatch::new(vec![event]);
        assert!(!batch.is_empty());
        assert_eq!(batch.len(), 1);
    }

    #[tokio::test]
    async fn test_output_sender_new() {
        let config = OutputConfig {
            name: "test".to_string(),
            url: "http://localhost:8088/services/collector".to_string(),
            token: "test-token".to_string(),
            api_key: "".to_string(),
            forwarder_id: "test-forwarder".to_string(),
            tls_verify: true,
            batch_size: 100,
            flush_interval_ms: 1000,
            retry_count: 3,
            compression_enabled: false,
            compression_level: 3,
            compression_adaptive: true,
            compression_use_dict: false,
            protocol: "grpc".to_string(),
        };

        let sender = OutputSender::new(config);
        assert_eq!(sender.config.name, "test");
    }

    #[tokio::test]
    async fn test_output_sender_with_compression() {
        let config = OutputConfig {
            name: "test".to_string(),
            url: "http://localhost:8088/services/collector".to_string(),
            token: "test-token".to_string(),
            api_key: "".to_string(),
            forwarder_id: "test-forwarder".to_string(),
            tls_verify: true,
            batch_size: 100,
            flush_interval_ms: 1000,
            retry_count: 3,
            compression_enabled: true,
            compression_level: 3,
            compression_adaptive: false,
            compression_use_dict: false,
            protocol: "grpc".to_string(),
        };

        let sender = OutputSender::new(config);
        assert_eq!(sender.config.name, "test");
        assert!(sender.compression.is_some());
    }
}
