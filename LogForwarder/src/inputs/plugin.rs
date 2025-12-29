use crate::event::Event;
use async_trait::async_trait;
use tokio::sync::mpsc;

#[async_trait]
pub trait InputPlugin: Send + Sync {
    fn name(&self) -> &str;

    fn version(&self) -> &str;

    fn description(&self) -> &str;

    async fn initialize(&mut self) -> Result<(), Box<dyn std::error::Error>>;

    async fn start(
        &mut self,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>>;

    async fn stop(&mut self) -> Result<(), Box<dyn std::error::Error>>;

    async fn health_check(&self) -> Result<(), Box<dyn std::error::Error>>;
}

pub struct HttpPollerPlugin {
    name: String,
    url: String,
    interval_secs: u64,
    running: bool,
}

impl HttpPollerPlugin {
    pub fn new(name: String, url: String, interval_secs: u64) -> Self {
        HttpPollerPlugin {
            name,
            url,
            interval_secs,
            running: false,
        }
    }
}

#[async_trait]
impl InputPlugin for HttpPollerPlugin {
    fn name(&self) -> &str {
        &self.name
    }

    fn version(&self) -> &str {
        "1.0.0"
    }

    fn description(&self) -> &str {
        "HTTP Poller Plugin - polls HTTP endpoints and sends responses as events"
    }

    async fn initialize(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.running = true;
        Ok(())
    }

    async fn start(
        &mut self,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let url = self.url.clone();
        let interval = std::time::Duration::from_secs(self.interval_secs);
        let name = self.name.clone();

        let client = reqwest::Client::new();

        loop {
            tokio::time::sleep(interval).await;

            match client.get(&url).send().await {
                Ok(response) => match response.text().await {
                    Ok(body) => {
                        let timestamp = std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .map(|d| d.as_nanos() as i64)
                            .unwrap_or_else(|e| {
                                tracing::warn!("HTTP Poller {}: Failed to get system time: {}", name, e);
                                0
                            });

                        let event = Event::new(
                            bytes::Bytes::from(body),
                            timestamp,
                            0,
                            fnv_hash(&format!("http_poller:{}", name)) as u32,
                            crate::event::SourceType::Json,
                            fnv_hash(&name) as u32,
                            0,
                        );

                        if let Err(e) = event_tx.send(event).await {
                            tracing::error!(
                                "HTTP Poller Plugin {}: failed to send event: {}",
                                name,
                                e
                            );
                            break;
                        }
                    }
                    Err(e) => {
                        tracing::warn!(
                            "HTTP Poller Plugin {}: failed to read response body: {}",
                            name,
                            e
                        );
                    }
                },
                Err(e) => {
                    tracing::warn!("HTTP Poller Plugin {}: request failed: {}", name, e);
                }
            }
        }

        Ok(())
    }

    async fn stop(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.running = false;
        Ok(())
    }

    async fn health_check(&self) -> Result<(), Box<dyn std::error::Error>> {
        if !self.running {
            return Err("Plugin not running".into());
        }

        match reqwest::Client::new().head(&self.url).send().await {
            Ok(response) => {
                if response.status().is_success() {
                    Ok(())
                } else {
                    Err(format!("HTTP {} response", response.status()).into())
                }
            }
            Err(e) => Err(Box::new(e)),
        }
    }
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
    fn test_http_poller_plugin_creation() {
        let plugin = HttpPollerPlugin::new(
            "test_http_poller".to_string(),
            "http://localhost:8080/api/events".to_string(),
            30,
        );

        assert_eq!(plugin.name(), "test_http_poller");
        assert_eq!(plugin.version(), "1.0.0");
    }
}
