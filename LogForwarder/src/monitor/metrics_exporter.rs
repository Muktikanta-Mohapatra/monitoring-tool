use prometheus::{Encoder, Registry, TextEncoder};
use std::sync::Arc;
use tokio::task::JoinHandle;
use tracing::{debug, error};

pub struct MetricsExporter {
    registry: Arc<Registry>,
    bind_address: String,
    port: u16,
}

impl MetricsExporter {
    pub fn new(registry: Arc<Registry>, bind_address: &str, port: u16) -> Self {
        MetricsExporter {
            registry,
            bind_address: bind_address.to_string(),
            port,
        }
    }

    pub fn start(&self) -> JoinHandle<()> {
        let registry = self.registry.clone();
        let bind_address = self.bind_address.clone();
        let port = self.port;

        tokio::spawn(async move {
            let addr = format!("{}:{}", bind_address, port);

            match tokio::net::TcpListener::bind(&addr).await {
                Ok(listener) => {
                    debug!("Metrics exporter listening on {}", addr);

                    loop {
                        match listener.accept().await {
                            Ok((socket, peer_addr)) => {
                                let registry = registry.clone();

                                tokio::spawn(async move {
                                    if let Err(e) = handle_metrics_request(socket, registry).await {
                                        error!(
                                            "Error handling metrics request from {}: {}",
                                            peer_addr, e
                                        );
                                    }
                                });
                            }
                            Err(e) => {
                                error!("Metrics server accept error: {}", e);
                            }
                        }
                    }
                }
                Err(e) => {
                    error!("Failed to bind metrics server to {}: {}", addr, e);
                }
            }
        })
    }
}

async fn handle_metrics_request(
    socket: tokio::net::TcpStream,
    registry: Arc<Registry>,
) -> Result<(), Box<dyn std::error::Error>> {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    let mut buf = [0u8; 4096];
    let (mut reader, mut writer) = socket.into_split();

    let n = reader.read(&mut buf).await?;
    let request = String::from_utf8_lossy(&buf[..n]);

    if request.contains("GET /metrics") {
        let encoder = TextEncoder::new();
        let metric_families = registry.gather();
        let mut buffer = vec![];
        encoder.encode(&metric_families, &mut buffer)?;

        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain; version=0.0.4\r\nContent-Length: {}\r\n\r\n",
            buffer.len()
        );

        writer.write_all(response.as_bytes()).await?;
        writer.write_all(&buffer).await?;
    } else if request.contains("GET /health") {
        let response =
            b"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK";
        writer.write_all(response).await?;
    } else {
        let response = b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n";
        writer.write_all(response).await?;
    }

    writer.flush().await?;
    Ok(())
}
