use super::syslog::{create_event_from_syslog, SyslogMessage};
use crate::config::TcpInputConfig;
use crate::event::Event;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio::time::timeout;
use tracing::{debug, error, warn};

#[derive(Clone)]
pub struct TcpInput {
    config: Arc<TcpInputConfig>,
    index_id: u16,
    active_connections: Arc<AtomicUsize>,
}

impl TcpInput {
    pub fn new(config: TcpInputConfig, index_id: u16) -> Self {
        TcpInput {
            config: Arc::new(config),
            index_id,
            active_connections: Arc::new(AtomicUsize::new(0)),
        }
    }

    pub async fn start(
        &self,
        event_tx: mpsc::Sender<Event>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let addr = format!("{}:{}", self.config.bind_address, self.config.port);
        let listener = TcpListener::bind(&addr).await?;

        debug!(
            "TCP input {} listening on {} (max {} connections)",
            self.config.name, addr, self.config.max_connections
        );

        loop {
            match listener.accept().await {
                Ok((socket, peer_addr)) => {
                    let current_connections = self.active_connections.load(Ordering::Relaxed);
                    if current_connections >= self.config.max_connections {
                        warn!(
                            "TCP input {}: connection limit ({}) reached, rejecting from {}",
                            self.config.name, self.config.max_connections, peer_addr
                        );
                        continue;
                    }

                    self.active_connections.fetch_add(1, Ordering::Relaxed);
                    let active_conn = self.active_connections.clone();
                    let config = self.config.clone();
                    let index_id = self.index_id;
                    let event_tx = event_tx.clone();

                    tokio::spawn(async move {
                        if let Err(e) = handle_tcp_connection(
                            socket,
                            peer_addr,
                            config.clone(),
                            index_id,
                            event_tx,
                        )
                        .await
                        {
                            error!(
                                "TCP input {}: error handling connection from {}: {}",
                                config.name, peer_addr, e
                            );
                        }

                        active_conn.fetch_sub(1, Ordering::Relaxed);
                    });
                }
                Err(e) => {
                    error!("TCP input {}: accept error: {}", self.config.name, e);
                }
            }
        }
    }

    pub fn active_connections(&self) -> usize {
        self.active_connections.load(Ordering::Relaxed)
    }
}

async fn handle_tcp_connection(
    socket: TcpStream,
    peer_addr: SocketAddr,
    config: Arc<TcpInputConfig>,
    index_id: u16,
    event_tx: mpsc::Sender<Event>,
) -> Result<(), Box<dyn std::error::Error>> {
    let (reader, _) = socket.into_split();
    let mut buf_reader = BufReader::with_capacity(config.read_buffer_kb * 1024, reader);

    let connection_timeout = Duration::from_secs(config.connection_timeout_secs);
    let mut line = String::with_capacity(4096);

    loop {
        line.clear();

        match timeout(connection_timeout, buf_reader.read_line(&mut line)).await {
            Ok(Ok(0)) => {
                debug!(
                    "TCP input {}: connection closed by {}",
                    config.name, peer_addr
                );
                break;
            }
            Ok(Ok(_)) => {
                if line.is_empty() || line.trim().is_empty() {
                    continue;
                }

                let syslog_msg = SyslogMessage::parse_bsd(line.trim());
                match syslog_msg {
                    Ok(msg) => {
                        let event = create_event_from_syslog(&msg, peer_addr, index_id);
                        if let Err(e) = event_tx.send(event).await {
                            error!("TCP input {}: failed to send event: {}", config.name, e);
                            break;
                        }
                    }
                    Err(e) => {
                        warn!(
                            "TCP input {}: failed to parse syslog from {}: {}",
                            config.name, peer_addr, e
                        );
                    }
                }
            }
            Ok(Err(e)) => {
                error!(
                    "TCP input {}: read error from {}: {}",
                    config.name, peer_addr, e
                );
                break;
            }
            Err(_) => {
                debug!(
                    "TCP input {}: connection timeout from {}",
                    config.name, peer_addr
                );
                break;
            }
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::AsyncWriteExt;
    use tokio::net::TcpStream;

    #[tokio::test]
    async fn test_tcp_input_creation() {
        let config = TcpInputConfig {
            name: "test_tcp".to_string(),
            port: 9999,
            bind_address: "127.0.0.1".to_string(),
            sourcetype: "syslog".to_string(),
            index: "main".to_string(),
            max_connections: 100,
            read_buffer_kb: 64,
            connection_timeout_secs: 300,
        };

        let input = TcpInput::new(config, 1);
        assert_eq!(input.active_connections(), 0);
    }

    #[tokio::test]
    async fn test_tcp_syslog_receive() {
        let config = TcpInputConfig {
            name: "test_tcp_syslog".to_string(),
            port: 10000,
            bind_address: "127.0.0.1".to_string(),
            sourcetype: "syslog".to_string(),
            index: "main".to_string(),
            max_connections: 10,
            read_buffer_kb: 64,
            connection_timeout_secs: 10,
        };

        let input = TcpInput::new(config, 1);
        let (event_tx, mut event_rx) = mpsc::channel::<Event>(100);

        let server_task = tokio::spawn({
            let input = input.clone();
            async move {
                let _ = input.start(event_tx).await;
            }
        });

        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        if let Ok(mut stream) = TcpStream::connect("127.0.0.1:10000").await {
            let msg = "<34>Oct 11 22:14:15 mymachine su: test message\n";
            let _ = stream.write_all(msg.as_bytes()).await;
        }

        tokio::time::sleep(std::time::Duration::from_millis(500)).await;

        if let Ok(event) =
            tokio::time::timeout(std::time::Duration::from_secs(1), event_rx.recv()).await
        {
            assert!(event.is_some());
        }

        server_task.abort();
    }
}
