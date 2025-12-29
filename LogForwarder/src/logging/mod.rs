use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use tracing::Level;
use tracing_subscriber::fmt::format::FmtSpan;
use tracing_subscriber::prelude::*;

pub struct StructuredLogger {
    level: Level,
    metrics: Arc<LogMetrics>,
}

#[derive(Default)]
pub struct LogMetrics {
    trace_count: AtomicUsize,
    debug_count: AtomicUsize,
    info_count: AtomicUsize,
    warn_count: AtomicUsize,
    error_count: AtomicUsize,
}

impl LogMetrics {
    pub fn record_trace(&self) {
        self.trace_count.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_debug(&self) {
        self.debug_count.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_info(&self) {
        self.info_count.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_warn(&self) {
        self.warn_count.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_error(&self) {
        self.error_count.fetch_add(1, Ordering::Relaxed);
    }

    pub fn get_counts(&self) -> LogCounts {
        LogCounts {
            trace: self.trace_count.load(Ordering::Relaxed),
            debug: self.debug_count.load(Ordering::Relaxed),
            info: self.info_count.load(Ordering::Relaxed),
            warn: self.warn_count.load(Ordering::Relaxed),
            error: self.error_count.load(Ordering::Relaxed),
        }
    }

    pub fn reset(&self) {
        self.trace_count.store(0, Ordering::Relaxed);
        self.debug_count.store(0, Ordering::Relaxed);
        self.info_count.store(0, Ordering::Relaxed);
        self.warn_count.store(0, Ordering::Relaxed);
        self.error_count.store(0, Ordering::Relaxed);
    }
}

#[derive(Debug, Clone)]
pub struct LogCounts {
    pub trace: usize,
    pub debug: usize,
    pub info: usize,
    pub warn: usize,
    pub error: usize,
}

impl StructuredLogger {
    pub fn new(level: Level) -> Self {
        StructuredLogger {
            level,
            metrics: Arc::new(LogMetrics::default()),
        }
    }

    pub fn init(&self) {
        let env_filter = tracing_subscriber::EnvFilter::new(format!("{}", self.level));

        tracing_subscriber::registry()
            .with(env_filter)
            .with(
                tracing_subscriber::fmt::layer()
                    .with_span_events(FmtSpan::CLOSE)
                    .json()
                    .with_target(true)
                    .with_thread_ids(true)
                    .with_thread_names(true),
            )
            .init();
    }

    pub fn init_compact(&self) {
        let env_filter = tracing_subscriber::EnvFilter::new(format!("{}", self.level));

        tracing_subscriber::registry()
            .with(env_filter)
            .with(tracing_subscriber::fmt::layer().compact().with_target(true))
            .init();
    }

    pub fn get_metrics(&self) -> Arc<LogMetrics> {
        Arc::clone(&self.metrics)
    }
}

pub struct PerformanceTimer {
    name: String,
    start: std::time::Instant,
}

impl PerformanceTimer {
    pub fn new(name: impl Into<String>) -> Self {
        PerformanceTimer {
            name: name.into(),
            start: std::time::Instant::now(),
        }
    }

    pub fn elapsed_ms(&self) -> u128 {
        self.start.elapsed().as_millis()
    }

    pub fn elapsed_micros(&self) -> u128 {
        self.start.elapsed().as_micros()
    }
}

impl Drop for PerformanceTimer {
    fn drop(&mut self) {
        let elapsed = self.start.elapsed();
        tracing::debug!(
            name = %self.name,
            elapsed_ms = elapsed.as_millis(),
            "Performance timer completed"
        );
    }
}

#[macro_export]
macro_rules! perf_timer {
    ($name:expr) => {
        $crate::logging::PerformanceTimer::new($name)
    };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_log_metrics() {
        let metrics = LogMetrics::default();

        metrics.record_info();
        metrics.record_info();
        metrics.record_error();

        let counts = metrics.get_counts();
        assert_eq!(counts.info, 2);
        assert_eq!(counts.error, 1);
    }

    #[test]
    fn test_performance_timer() {
        let timer = PerformanceTimer::new("test");
        std::thread::sleep(std::time::Duration::from_millis(10));

        assert!(timer.elapsed_ms() >= 10);
    }
}
