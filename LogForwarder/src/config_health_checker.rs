use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tokio::time::sleep;

pub struct HealthCheckMetrics {
    pub events_processed: Arc<AtomicU64>,
    pub errors_occurred: Arc<AtomicU64>,
    pub last_event_time: Arc<AtomicU64>,
    pub config_applied: Arc<AtomicBool>,
}

impl HealthCheckMetrics {
    pub fn new() -> Self {
        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        HealthCheckMetrics {
            events_processed: Arc::new(AtomicU64::new(0)),
            errors_occurred: Arc::new(AtomicU64::new(0)),
            last_event_time: Arc::new(AtomicU64::new(current_time)),
            config_applied: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn record_event(&self) {
        self.events_processed.fetch_add(1, Ordering::Relaxed);

        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        self.last_event_time.store(current_time, Ordering::Relaxed);
    }

    pub fn record_error(&self) {
        self.errors_occurred.fetch_add(1, Ordering::Relaxed);
    }

    pub fn mark_config_applied(&self) {
        self.config_applied.store(true, Ordering::Release);
    }
}

impl Default for HealthCheckMetrics {
    fn default() -> Self {
        Self::new()
    }
}

pub struct ConfigHealthChecker {
    metrics: HealthCheckMetrics,
    grace_period: Duration,
    max_allowed_errors: u64,
}

#[derive(Debug, Clone)]
pub struct HealthCheckResult {
    pub healthy: bool,
    pub events_processed: u64,
    pub errors_occurred: u64,
    pub event_flow_active: bool,
    pub reason: String,
}

impl ConfigHealthChecker {
    pub fn new(grace_period: Duration, max_allowed_errors: u64) -> Self {
        ConfigHealthChecker {
            metrics: HealthCheckMetrics::new(),
            grace_period,
            max_allowed_errors,
        }
    }

    pub fn get_metrics(&self) -> HealthCheckMetrics {
        HealthCheckMetrics {
            events_processed: Arc::clone(&self.metrics.events_processed),
            errors_occurred: Arc::clone(&self.metrics.errors_occurred),
            last_event_time: Arc::clone(&self.metrics.last_event_time),
            config_applied: Arc::clone(&self.metrics.config_applied),
        }
    }

    pub async fn run_health_check(&self) -> HealthCheckResult {
        let start = Instant::now();

        while start.elapsed() < self.grace_period {
            sleep(Duration::from_millis(100)).await;
        }

        let events_processed = self.metrics.events_processed.load(Ordering::Acquire);
        let errors_occurred = self.metrics.errors_occurred.load(Ordering::Acquire);
        let last_event_time = self.metrics.last_event_time.load(Ordering::Acquire);

        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let time_since_last_event = current_time.saturating_sub(last_event_time);
        let event_flow_active = time_since_last_event < self.grace_period.as_secs();

        if errors_occurred > self.max_allowed_errors {
            return HealthCheckResult {
                healthy: false,
                events_processed,
                errors_occurred,
                event_flow_active,
                reason: format!(
                    "Too many errors: {} > {}",
                    errors_occurred, self.max_allowed_errors
                ),
            };
        }

        if !event_flow_active {
            tracing::warn!(
                "Event flow not active: no events in last {} seconds",
                time_since_last_event
            );
        }

        HealthCheckResult {
            healthy: true,
            events_processed,
            errors_occurred,
            event_flow_active,
            reason: "Health check passed".to_string(),
        }
    }

    pub fn reset(&self) {
        self.metrics.events_processed.store(0, Ordering::Release);
        self.metrics.errors_occurred.store(0, Ordering::Release);

        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        self.metrics
            .last_event_time
            .store(current_time, Ordering::Release);
        self.metrics.config_applied.store(false, Ordering::Release);
    }
}
