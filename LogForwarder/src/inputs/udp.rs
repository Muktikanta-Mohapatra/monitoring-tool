use super::syslog::create_event_from_udp;
use crate::config::UdpInputConfig;
use crate::event::Event;
use std::sync::Arc;
use tokio::net::UdpSocket;
use tokio::sync::mpsc;
use tracing::{debug, error};

pub struct UdpInput {
    config: Arc<UdpInputConfig>,
    index_id: u16,
}

impl UdpInput {
    pub fn new(config: UdpInputConfig, index_id: u16) -> Self {
        UdpInput {
            config: Arc::new(config),
            index_id,
        }
    }

    pub async fn start(
        &self,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let addr = format!("{}:{}", self.config.bind_address, self.config.port);
        let socket = UdpSocket::bind(&addr).await?;
        let socket = Arc::new(socket);

        debug!("UDP input {} listening on {}", self.config.name, addr);

        let mut buf = vec![0u8; self.config.max_datagram_size];

        loop {
            match socket.recv_from(&mut buf).await {
                Ok((len, peer_addr)) => {
                    if len == 0 {
                        continue;
                    }

                    let data = &buf[..len];
                    let event = create_event_from_udp(data, peer_addr, self.index_id);

                    if let Err(e) = event_tx.send(event).await {
                        error!(
                            "UDP input {}: failed to send event: {}",
                            self.config.name, e
                        );
                        break;
                    }
                }
                Err(e) => {
                    error!("UDP input {}: receive error: {}", self.config.name, e);
                }
            }
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_udp_input_creation() {
        let config = UdpInputConfig {
            name: "test_udp".to_string(),
            port: 9999,
            bind_address: "127.0.0.1".to_string(),
            sourcetype: "syslog".to_string(),
            index: "main".to_string(),
            max_datagram_size: 65536,
        };

        let input = UdpInput::new(config, 1);
        assert_eq!(input.index_id, 1);
    }
}
