use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;
use tracing::{error, info, warn};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ShutdownPhase {
    Initial,
    StoppingInputs,
    DrainingPipeline,
    SavingState,
    Cleanup,
}

#[derive(Clone)]
pub struct ShutdownSignal {
    initiated: Arc<AtomicBool>,
    phase: Arc<RwLock<ShutdownPhase>>,
}

impl Default for ShutdownSignal {
    fn default() -> Self {
        Self::new()
    }
}

impl ShutdownSignal {
    pub fn new() -> Self {
        ShutdownSignal {
            initiated: Arc::new(AtomicBool::new(false)),
            phase: Arc::new(RwLock::new(ShutdownPhase::Initial)),
        }
    }

    pub async fn wait_for_signal(&self) {
        let signal_clone = self.clone();

        tokio::select! {
            _ = tokio::signal::ctrl_c() => {
                warn!("Received SIGINT (Ctrl+C)");
                signal_clone.set_initiated().await;
            }
            _ = Self::wait_sigterm() => {
                warn!("Received SIGTERM");
                signal_clone.set_initiated().await;
            }
        }
    }

    #[cfg(unix)]
    async fn wait_sigterm() {
        use tokio::signal::unix::{signal, SignalKind};
        if let Ok(mut sigterm) = signal(SignalKind::terminate()) {
            let _ = sigterm.recv().await;
        }
    }

    #[cfg(not(unix))]
    async fn wait_sigterm() {
        loop {
            tokio::time::sleep(Duration::from_secs(1)).await;
        }
    }

    pub async fn set_initiated(&self) {
        self.initiated.store(true, Ordering::Release);
    }

    pub fn is_shutdown(&self) -> bool {
        self.initiated.load(Ordering::Acquire)
    }

    pub async fn set_phase(&self, phase: ShutdownPhase) {
        *self.phase.write().await = phase;
    }

    pub async fn get_phase(&self) -> ShutdownPhase {
        *self.phase.read().await
    }
}

pub struct GracefulShutdown {
    signal: ShutdownSignal,
    timeout: Duration,
    start_time: Option<Instant>,
}

impl GracefulShutdown {
    pub fn new(signal: ShutdownSignal, timeout_secs: u64) -> Self {
        GracefulShutdown {
            signal,
            timeout: Duration::from_secs(timeout_secs),
            start_time: None,
        }
    }

    pub async fn execute<F1, F2, F3, F4>(
        mut self,
        stop_inputs: F1,
        drain_pipeline: F2,
        save_state: F3,
        close_connections: F4,
    ) -> Result<(), String>
    where
        F1: std::future::Future<Output = Result<(), String>>,
        F2: std::future::Future<Output = Result<u64, String>>,
        F3: std::future::Future<Output = Result<(), String>>,
        F4: std::future::Future<Output = Result<(), String>>,
    {
        self.start_time = Some(Instant::now());

        info!(
            "=== Graceful Shutdown Started (timeout: {:?}) ===",
            self.timeout
        );

        self.signal.set_phase(ShutdownPhase::StoppingInputs).await;
        info!("Phase 1/4: Stopping inputs");

        if let Err(e) = self.execute_with_timeout(stop_inputs, "stop inputs").await {
            warn!("Failed to stop inputs gracefully: {}", e);
        }

        self.signal.set_phase(ShutdownPhase::DrainingPipeline).await;
        info!("Phase 2/4: Draining event pipeline");

        match self
            .execute_with_timeout(drain_pipeline, "drain pipeline")
            .await
        {
            Ok(drained) => info!("Drained {} events", drained),
            Err(e) => warn!("Failed to drain pipeline: {}", e),
        }

        self.signal.set_phase(ShutdownPhase::SavingState).await;
        info!("Phase 3/4: Saving checkpoints and state");

        if let Err(e) = self.execute_with_timeout(save_state, "save state").await {
            warn!("Failed to save state: {}", e);
        }

        self.signal.set_phase(ShutdownPhase::Cleanup).await;
        info!("Phase 4/4: Cleanup and releasing resources");

        if let Err(e) = self
            .execute_with_timeout(close_connections, "close connections")
            .await
        {
            warn!("Failed to close connections: {}", e);
        }

        let elapsed = self.start_time.unwrap().elapsed();
        info!(
            "=== Graceful Shutdown Complete (elapsed: {:?}) ===",
            elapsed
        );

        Ok(())
    }

    async fn execute_with_timeout<F, T>(&self, future: F, operation: &str) -> Result<T, String>
    where
        F: std::future::Future<Output = Result<T, String>>,
    {
        let remaining = self.get_remaining_time();

        if remaining <= Duration::from_millis(100) {
            error!("Shutdown timeout exceeded, forcing exit");
            return Err("Shutdown timeout exceeded".to_string());
        }

        match tokio::time::timeout(remaining, future).await {
            Ok(result) => result,
            Err(_) => {
                let elapsed = self.start_time.unwrap().elapsed();
                error!("Operation '{}' timed out after {:?}", operation, elapsed);
                Err(format!("Operation '{}' timed out", operation))
            }
        }
    }

    fn get_remaining_time(&self) -> Duration {
        if let Some(start) = self.start_time {
            let elapsed = start.elapsed();
            if elapsed >= self.timeout {
                Duration::from_millis(0)
            } else {
                self.timeout - elapsed
            }
        } else {
            self.timeout
        }
    }

    pub fn is_timeout_exceeded(&self) -> bool {
        if let Some(start) = self.start_time {
            start.elapsed() >= self.timeout
        } else {
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_shutdown_signal_creation() {
        let signal = ShutdownSignal::new();
        assert!(!signal.is_shutdown());
    }

    #[tokio::test]
    async fn test_shutdown_signal_set_initiated() {
        let signal = ShutdownSignal::new();
        signal.set_initiated().await;
        assert!(signal.is_shutdown());
    }

    #[tokio::test]
    async fn test_shutdown_phase_transitions() {
        let signal = ShutdownSignal::new();

        signal.set_phase(ShutdownPhase::StoppingInputs).await;
        assert_eq!(signal.get_phase().await, ShutdownPhase::StoppingInputs);

        signal.set_phase(ShutdownPhase::DrainingPipeline).await;
        assert_eq!(signal.get_phase().await, ShutdownPhase::DrainingPipeline);
    }

    #[tokio::test]
    async fn test_graceful_shutdown_completion() {
        let signal = ShutdownSignal::new();
        signal.set_initiated().await;

        let shutdown = GracefulShutdown::new(signal, 10);

        let result = shutdown
            .execute(
                async { Ok(()) },
                async { Ok(100u64) },
                async { Ok(()) },
                async { Ok(()) },
            )
            .await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_graceful_shutdown_timeout() {
        let signal = ShutdownSignal::new();
        signal.set_initiated().await;

        let shutdown = GracefulShutdown::new(signal, 1);

        let result = shutdown
            .execute(
                async {
                    tokio::time::sleep(Duration::from_secs(5)).await;
                    Ok(())
                },
                async { Ok(0u64) },
                async { Ok(()) },
                async { Ok(()) },
            )
            .await;

        assert!(result.is_ok());
    }
}
