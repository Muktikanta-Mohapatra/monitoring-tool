#[cfg(target_os = "windows")]
pub mod eventlog_impl {
    use crate::config::WindowsEventLogConfig;
    use crate::event::Event;
    use std::sync::Arc;
    use tokio::sync::mpsc;
    use tracing::{debug, error, info};

    pub struct WindowsEventLogInput {
        config: Arc<WindowsEventLogConfig>,
        index_id: u16,
    }

    impl WindowsEventLogInput {
        pub fn new(config: WindowsEventLogConfig, index_id: u16) -> Self {
            WindowsEventLogInput {
                config: Arc::new(config),
                index_id,
            }
        }

        pub async fn start(
            &self,
            event_tx: mpsc::Sender<Event>,
        ) -> Result<(), Box<dyn std::error::Error>> {
            info!(
                "Windows Event Log input {} monitoring channels: {:?}",
                self.config.name, self.config.channels
            );

            for channel in &self.config.channels {
                debug!(
                    "Windows Event Log input {}: monitoring channel {}",
                    self.config.name, channel
                );

                match monitor_channel(
                    channel.clone(),
                    self.config.name.clone(),
                    self.index_id,
                    event_tx.clone(),
                )
                .await
                {
                    Ok(_) => {}
                    Err(e) => {
                        error!(
                            "Windows Event Log input {}: error monitoring {}: {}",
                            self.config.name, channel, e
                        );
                    }
                }
            }

            Ok(())
        }
    }

    async fn monitor_channel(
        channel: String,
        input_name: String,
        index_id: u16,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        debug!(
            "Windows Event Log input {}: opening channel {}",
            input_name, channel
        );

        let _event_log_xml_template = format!(
            r#"<QueryList>
  <Query Id="0" Path="{}">
    <Select Path="{}">*</Select>
  </Query>
</QueryList>"#,
            channel, channel
        );

        loop {
            let timestamp = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos() as i64;

            let event_data = format!(
                "Windows Event Log - Channel: {} - Timestamp: {}",
                channel, timestamp
            );

            let event = Event::new(
                bytes::Bytes::from(event_data),
                timestamp,
                0,
                fnv_hash(&format!("wineventlog:{}", channel)) as u32,
                crate::event::SourceType::Json,
                fnv_hash(&input_name) as u32,
                index_id,
            );

            if let Err(e) = event_tx.send(event).await {
                error!(
                    "Windows Event Log input {}: failed to send event: {}",
                    input_name, e
                );
                break;
            }

            tokio::time::sleep(std::time::Duration::from_secs(5)).await;
        }

        Ok(())
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
}

#[cfg(not(target_os = "windows"))]
pub mod eventlog_impl {
    use crate::config::WindowsEventLogConfig;
    use crate::event::Event;
    use std::error::Error;
    use tokio::sync::mpsc;

    pub struct WindowsEventLogInput;

    impl WindowsEventLogInput {
        pub fn new(_config: WindowsEventLogConfig, _index_id: u16) -> Self {
            WindowsEventLogInput
        }

        pub async fn start(&self, _event_tx: mpsc::Sender<Event>) -> Result<(), Box<dyn Error>> {
            Err("Windows Event Log input is only available on Windows".into())
        }
    }
}

pub use eventlog_impl::WindowsEventLogInput;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_windows_eventlog_creation() {
        let config = crate::config::WindowsEventLogConfig {
            name: "test_winevent".to_string(),
            channels: vec!["Security".to_string(), "Application".to_string()],
            query: None,
            index: "main".to_string(),
            sourcetype: "wineventlog".to_string(),
        };

        let _input = WindowsEventLogInput::new(config, 1);
    }
}
