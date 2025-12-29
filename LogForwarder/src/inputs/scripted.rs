use crate::config::ScriptedInputConfig;
use crate::event::Event;
use std::process::Stdio;
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::mpsc;
use tokio::time::{interval, timeout};
use tracing::{debug, error, warn};

pub struct ScriptedInput {
    config: Arc<ScriptedInputConfig>,
    index_id: u16,
}

impl ScriptedInput {
    pub fn new(config: ScriptedInputConfig, index_id: u16) -> Self {
        ScriptedInput {
            config: Arc::new(config),
            index_id,
        }
    }

    pub async fn start(
        &self,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut interval_timer = interval(Duration::from_secs(self.config.interval_secs));
        let config = self.config.clone();
        let index_id = self.index_id;

        loop {
            interval_timer.tick().await;

            debug!(
                "Scripted input {}: executing {}",
                config.name, config.command
            );

            match execute_script(&config, index_id, event_tx.clone()).await {
                Ok(events_sent) => {
                    debug!(
                        "Scripted input {}: sent {} events",
                        config.name, events_sent
                    );
                }
                Err(e) => {
                    error!("Scripted input {}: execution failed: {}", config.name, e);
                }
            }
        }
    }
}

async fn execute_script(
    config: &ScriptedInputConfig,
    index_id: u16,
    event_tx: mpsc::Sender<Event>,
) -> Result<usize, Box<dyn std::error::Error>> {
    let timeout_duration = Duration::from_secs(config.timeout_secs);

    let future = async {
        let mut cmd = Command::new(&config.command);

        for arg in &config.args {
            cmd.arg(arg);
        }

        for (key, value) in &config.environment {
            cmd.env(key, value);
        }

        cmd.stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .stdin(Stdio::null());

        let mut child = cmd.spawn()?;

        let stdout = child.stdout.take().ok_or("Failed to open stdout")?;
        let mut reader = BufReader::new(stdout);
        let mut line = String::new();
        let mut events_sent = 0;

        loop {
            line.clear();
            match reader.read_line(&mut line).await {
                Ok(0) => break,
                Ok(_) => {
                    if !line.trim().is_empty() {
                        match serde_json::from_str::<serde_json::Value>(line.trim()) {
                            Ok(json) => {
                                let event_data =
                                    serde_json::to_string(&json).unwrap_or_else(|_| line.clone());
                                let event = create_scripted_event(
                                    event_data.as_bytes(),
                                    &config.name,
                                    index_id,
                                );
                                if let Err(e) = event_tx.send(event).await {
                                    return Err(format!("Failed to send event: {}", e).into());
                                }
                                events_sent += 1;
                            }
                            Err(_) => {
                                let event =
                                    create_scripted_event(line.as_bytes(), &config.name, index_id);
                                if let Err(e) = event_tx.send(event).await {
                                    return Err(format!("Failed to send event: {}", e).into());
                                }
                                events_sent += 1;
                            }
                        }
                    }
                }
                Err(e) => return Err(e.into()),
            }
        }

        let status = child.wait().await?;
        if !status.success() {
            warn!(
                "Script {} exited with code: {:?}",
                config.command,
                status.code()
            );
        }

        Ok(events_sent)
    };

    match timeout(timeout_duration, future).await {
        Ok(Ok(count)) => Ok(count),
        Ok(Err(e)) => Err(e),
        Err(_) => {
            error!(
                "Script {} exceeded timeout of {}s",
                config.command, config.timeout_secs
            );
            Err("Script timeout".into())
        }
    }
}

fn create_scripted_event(data: &[u8], script_name: &str, index_id: u16) -> Event {
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos() as i64;

    let source_str = format!("script:{}", script_name);
    let source_id = fnv_hash(&source_str) as u32;

    Event::new(
        bytes::Bytes::copy_from_slice(data),
        timestamp,
        0,
        source_id,
        crate::event::SourceType::Json,
        fnv_hash(script_name) as u32,
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
    use std::collections::HashMap;

    #[test]
    fn test_scripted_input_creation() {
        let config = ScriptedInputConfig {
            name: "test_script".to_string(),
            command: "/bin/echo".to_string(),
            args: vec!["hello".to_string()],
            interval_secs: 60,
            timeout_secs: 30,
            sourcetype: "json".to_string(),
            index: "main".to_string(),
            environment: HashMap::new(),
            resource_limits: crate::config::ResourceLimits {
                max_memory_mb: Some(100),
                max_cpu_percent: Some(20),
            },
        };

        let input = ScriptedInput::new(config, 1);
        assert_eq!(input.index_id, 1);
    }
}
