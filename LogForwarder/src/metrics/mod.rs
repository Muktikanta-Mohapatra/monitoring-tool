use parking_lot::RwLock;
use prometheus::{
    register_counter_vec_with_registry, register_counter_with_registry,
    register_gauge_vec_with_registry, register_gauge_with_registry,
    register_histogram_vec_with_registry, Counter, CounterVec, Gauge, GaugeVec, HistogramVec,
    Registry,
};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Instant;

#[derive(Clone, Debug)]
pub struct CompressionStats {
    pub samples: u64,
    pub total_original: u64,
    pub total_compressed: u64,
}

impl CompressionStats {
    pub fn ratio(&self) -> f64 {
        if self.total_original == 0 {
            0.0
        } else {
            (self.total_compressed as f64) / (self.total_original as f64)
        }
    }
}

pub struct PipelineMetrics {
    events_processed: Arc<AtomicU64>,
    events_dropped: Arc<AtomicU64>,
    bytes_processed: Arc<AtomicU64>,
    batches_flushed: Arc<AtomicU64>,
    compression_ratio_samples: Arc<AtomicU64>,
    compression_ratio_sum: Arc<AtomicU64>,

    latency_samples: Arc<AtomicUsize>,
    latency_sum_micros: Arc<AtomicU64>,
    latency_min_micros: Arc<AtomicU64>,
    latency_max_micros: Arc<AtomicU64>,

    input_queue_depth: Arc<AtomicUsize>,
    batch_queue_depth: Arc<AtomicUsize>,
    output_queue_depth: Arc<AtomicUsize>,

    start_time: Instant,

    prometheus_registry: Arc<Registry>,

    events_read_counter: CounterVec,
    events_sent_counter: CounterVec,
    events_dropped_counter: CounterVec,
    bytes_read_counter: CounterVec,
    bytes_sent_counter: CounterVec,
    bytes_compressed_counter: Counter,

    queue_depth_gauge: GaugeVec,
    queue_capacity_gauge: GaugeVec,
    active_connections_gauge: GaugeVec,
    open_files_gauge: Gauge,

    event_latency_histogram: HistogramVec,
    compression_ratio_summary: Arc<RwLock<HashMap<String, CompressionStats>>>,

    errors_total_counter: CounterVec,
    files_monitored_gauge: Gauge,
    files_rotated_counter: Counter,
    checkpoint_saves_counter: Counter,
}

impl PipelineMetrics {
    pub fn new() -> Self {
        let registry = Registry::new();

        let events_read_counter = register_counter_vec_with_registry!(
            "forwarder_events_read_total",
            "Total events read from sources",
            &["source", "sourcetype"],
            registry
        )
        .expect("failed to create events_read_counter");

        let events_sent_counter = register_counter_vec_with_registry!(
            "forwarder_events_sent_total",
            "Total events sent to destinations",
            &["destination"],
            registry
        )
        .expect("failed to create events_sent_counter");

        let events_dropped_counter = register_counter_vec_with_registry!(
            "forwarder_events_dropped_total",
            "Total events dropped",
            &["reason"],
            registry
        )
        .expect("failed to create events_dropped_counter");

        let bytes_read_counter = register_counter_vec_with_registry!(
            "forwarder_bytes_read_total",
            "Total bytes read from sources",
            &["source"],
            registry
        )
        .expect("failed to create bytes_read_counter");

        let bytes_sent_counter = register_counter_vec_with_registry!(
            "forwarder_bytes_sent_total",
            "Total bytes sent to destinations",
            &["destination"],
            registry
        )
        .expect("failed to create bytes_sent_counter");

        let bytes_compressed_counter = register_counter_with_registry!(
            "forwarder_bytes_compressed_total",
            "Total bytes compressed",
            registry
        )
        .expect("failed to create bytes_compressed_counter");

        let queue_depth_gauge = register_gauge_vec_with_registry!(
            "forwarder_queue_depth",
            "Current queue depth",
            &["queue"],
            registry
        )
        .expect("failed to create queue_depth_gauge");

        let queue_capacity_gauge = register_gauge_vec_with_registry!(
            "forwarder_queue_capacity",
            "Queue capacity",
            &["queue"],
            registry
        )
        .expect("failed to create queue_capacity_gauge");

        let active_connections_gauge = register_gauge_vec_with_registry!(
            "forwarder_connections_active",
            "Active connections",
            &["indexer"],
            registry
        )
        .expect("failed to create active_connections_gauge");

        let open_files_gauge = register_gauge_with_registry!(
            "forwarder_open_files_total",
            "Number of open files",
            registry
        )
        .expect("failed to create open_files_gauge");

        let event_latency_histogram = register_histogram_vec_with_registry!(
            "forwarder_event_latency_seconds",
            "Event latency in seconds",
            &["stage"],
            vec![0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0],
            registry
        )
        .expect("failed to create event_latency_histogram");

        let errors_total_counter = register_counter_vec_with_registry!(
            "forwarder_errors_total",
            "Total errors by component and type",
            &["component", "error_type"],
            registry
        )
        .expect("failed to create errors_total_counter");

        let files_monitored_gauge = register_gauge_with_registry!(
            "forwarder_files_monitored_total",
            "Number of files being monitored",
            registry
        )
        .expect("failed to create files_monitored_gauge");

        let files_rotated_counter = register_counter_with_registry!(
            "forwarder_files_rotated_total",
            "Total files rotated",
            registry
        )
        .expect("failed to create files_rotated_counter");

        let checkpoint_saves_counter = register_counter_with_registry!(
            "forwarder_checkpoint_saves_total",
            "Total checkpoint saves",
            registry
        )
        .expect("failed to create checkpoint_saves_counter");

        PipelineMetrics {
            events_processed: Arc::new(AtomicU64::new(0)),
            events_dropped: Arc::new(AtomicU64::new(0)),
            bytes_processed: Arc::new(AtomicU64::new(0)),
            batches_flushed: Arc::new(AtomicU64::new(0)),
            compression_ratio_samples: Arc::new(AtomicU64::new(0)),
            compression_ratio_sum: Arc::new(AtomicU64::new(0)),

            latency_samples: Arc::new(AtomicUsize::new(0)),
            latency_sum_micros: Arc::new(AtomicU64::new(0)),
            latency_min_micros: Arc::new(AtomicU64::new(u64::MAX)),
            latency_max_micros: Arc::new(AtomicU64::new(0)),

            input_queue_depth: Arc::new(AtomicUsize::new(0)),
            batch_queue_depth: Arc::new(AtomicUsize::new(0)),
            output_queue_depth: Arc::new(AtomicUsize::new(0)),

            start_time: Instant::now(),

            prometheus_registry: Arc::new(registry),

            events_read_counter,
            events_sent_counter,
            events_dropped_counter,
            bytes_read_counter,
            bytes_sent_counter,
            bytes_compressed_counter,

            queue_depth_gauge,
            queue_capacity_gauge,
            active_connections_gauge,
            open_files_gauge,

            event_latency_histogram,
            compression_ratio_summary: Arc::new(RwLock::new(HashMap::new())),

            errors_total_counter,
            files_monitored_gauge,
            files_rotated_counter,
            checkpoint_saves_counter,
        }
    }

    pub fn get_registry(&self) -> Arc<Registry> {
        Arc::clone(&self.prometheus_registry)
    }

    pub fn record_events_read(&self, count: u64, source: &str, sourcetype: &str) {
        self.events_processed.fetch_add(count, Ordering::Relaxed);
        self.events_read_counter
            .with_label_values(&[source, sourcetype])
            .inc_by(count as f64);
    }

    pub fn record_events_sent(&self, count: u64, destination: &str) {
        self.events_sent_counter
            .with_label_values(&[destination])
            .inc_by(count as f64);
    }

    pub fn record_events_dropped(&self, count: u64, reason: &str) {
        self.events_dropped.fetch_add(count, Ordering::Relaxed);
        self.events_dropped_counter
            .with_label_values(&[reason])
            .inc_by(count as f64);
    }

    pub fn record_bytes_read(&self, bytes: u64, source: &str) {
        self.bytes_processed.fetch_add(bytes, Ordering::Relaxed);
        self.bytes_read_counter
            .with_label_values(&[source])
            .inc_by(bytes as f64);
    }

    pub fn record_bytes_sent(&self, bytes: u64, destination: &str) {
        self.bytes_sent_counter
            .with_label_values(&[destination])
            .inc_by(bytes as f64);
    }

    pub fn record_bytes_compressed(&self, bytes: u64) {
        self.bytes_compressed_counter.inc_by(bytes as f64);
    }

    pub fn record_batch_flushed(&self) {
        self.batches_flushed.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_compression_ratio(&self, original_size: u64, compressed_size: u64) {
        if original_size > 0 {
            let ratio = (compressed_size * 10000) / original_size;
            self.compression_ratio_sum
                .fetch_add(ratio, Ordering::Relaxed);
            self.compression_ratio_samples
                .fetch_add(1, Ordering::Relaxed);
        }
    }

    pub fn record_latency_micros(&self, micros: u64, stage: &str) {
        self.latency_samples.fetch_add(1, Ordering::Relaxed);
        self.latency_sum_micros.fetch_add(micros, Ordering::Relaxed);

        let micros_secs = (micros as f64) / 1_000_000.0;
        self.event_latency_histogram
            .with_label_values(&[stage])
            .observe(micros_secs);

        let mut current_min = self.latency_min_micros.load(Ordering::Relaxed);
        while micros < current_min {
            match self.latency_min_micros.compare_exchange_weak(
                current_min,
                micros,
                Ordering::Release,
                Ordering::Relaxed,
            ) {
                Ok(_) => break,
                Err(actual) => current_min = actual,
            }
        }

        let mut current_max = self.latency_max_micros.load(Ordering::Relaxed);
        while micros > current_max {
            match self.latency_max_micros.compare_exchange_weak(
                current_max,
                micros,
                Ordering::Release,
                Ordering::Relaxed,
            ) {
                Ok(_) => break,
                Err(actual) => current_max = actual,
            }
        }
    }

    pub fn record_error(&self, component: &str, error_type: &str) {
        self.errors_total_counter
            .with_label_values(&[component, error_type])
            .inc();
    }

    pub fn set_queue_depth(&self, queue: &str, depth: usize) {
        self.queue_depth_gauge
            .with_label_values(&[queue])
            .set(depth as f64);
    }

    pub fn set_queue_capacity(&self, queue: &str, capacity: usize) {
        self.queue_capacity_gauge
            .with_label_values(&[queue])
            .set(capacity as f64);
    }

    pub fn set_active_connections(&self, indexer: &str, count: usize) {
        self.active_connections_gauge
            .with_label_values(&[indexer])
            .set(count as f64);
    }

    pub fn set_open_files(&self, count: usize) {
        self.open_files_gauge.set(count as f64);
    }

    pub fn set_files_monitored(&self, count: usize) {
        self.files_monitored_gauge.set(count as f64);
    }

    pub fn record_file_rotated(&self) {
        self.files_rotated_counter.inc();
    }

    pub fn record_checkpoint_saved(&self) {
        self.checkpoint_saves_counter.inc();
    }

    pub fn update_queue_depths(&self, input: usize, batch: usize, output: usize) {
        self.input_queue_depth.store(input, Ordering::Release);
        self.batch_queue_depth.store(batch, Ordering::Release);
        self.output_queue_depth.store(output, Ordering::Release);

        self.set_queue_depth("input", input);
        self.set_queue_depth("batch", batch);
        self.set_queue_depth("output", output);
    }

    pub fn get_events_processed(&self) -> u64 {
        self.events_processed.load(Ordering::Acquire)
    }

    pub fn get_events_dropped(&self) -> u64 {
        self.events_dropped.load(Ordering::Acquire)
    }

    pub fn get_bytes_processed(&self) -> u64 {
        self.bytes_processed.load(Ordering::Acquire)
    }

    pub fn get_batches_flushed(&self) -> u64 {
        self.batches_flushed.load(Ordering::Acquire)
    }

    pub fn get_avg_compression_ratio(&self) -> f64 {
        let samples = self.compression_ratio_samples.load(Ordering::Acquire);
        if samples == 0 {
            0.0
        } else {
            let sum = self.compression_ratio_sum.load(Ordering::Acquire);
            (sum as f64) / (samples as f64 * 10000.0)
        }
    }

    pub fn get_avg_latency_micros(&self) -> f64 {
        let samples = self.latency_samples.load(Ordering::Acquire);
        if samples == 0 {
            0.0
        } else {
            let sum = self.latency_sum_micros.load(Ordering::Acquire);
            (sum as f64) / (samples as f64)
        }
    }

    pub fn get_min_latency_micros(&self) -> u64 {
        self.latency_min_micros.load(Ordering::Acquire)
    }

    pub fn get_max_latency_micros(&self) -> u64 {
        self.latency_max_micros.load(Ordering::Acquire)
    }

    pub fn get_throughput_events_per_sec(&self) -> f64 {
        let elapsed_secs = self.start_time.elapsed().as_secs_f64();
        if elapsed_secs > 0.0 {
            (self.get_events_processed() as f64) / elapsed_secs
        } else {
            0.0
        }
    }

    pub fn get_throughput_bytes_per_sec(&self) -> f64 {
        let elapsed_secs = self.start_time.elapsed().as_secs_f64();
        if elapsed_secs > 0.0 {
            (self.get_bytes_processed() as f64) / elapsed_secs
        } else {
            0.0
        }
    }

    pub fn get_queue_depths(&self) -> (usize, usize, usize) {
        (
            self.input_queue_depth.load(Ordering::Acquire),
            self.batch_queue_depth.load(Ordering::Acquire),
            self.output_queue_depth.load(Ordering::Acquire),
        )
    }

    pub fn get_uptime_secs(&self) -> f64 {
        self.start_time.elapsed().as_secs_f64()
    }

    pub fn reset_counters(&self) {
        self.events_processed.store(0, Ordering::Release);
        self.events_dropped.store(0, Ordering::Release);
        self.bytes_processed.store(0, Ordering::Release);
        self.batches_flushed.store(0, Ordering::Release);
        self.compression_ratio_samples.store(0, Ordering::Release);
        self.compression_ratio_sum.store(0, Ordering::Release);
        self.latency_samples.store(0, Ordering::Release);
        self.latency_sum_micros.store(0, Ordering::Release);
        self.latency_min_micros.store(u64::MAX, Ordering::Release);
        self.latency_max_micros.store(0, Ordering::Release);
    }
}

impl Clone for PipelineMetrics {
    fn clone(&self) -> Self {
        PipelineMetrics {
            events_processed: Arc::clone(&self.events_processed),
            events_dropped: Arc::clone(&self.events_dropped),
            bytes_processed: Arc::clone(&self.bytes_processed),
            batches_flushed: Arc::clone(&self.batches_flushed),
            compression_ratio_samples: Arc::clone(&self.compression_ratio_samples),
            compression_ratio_sum: Arc::clone(&self.compression_ratio_sum),
            latency_samples: Arc::clone(&self.latency_samples),
            latency_sum_micros: Arc::clone(&self.latency_sum_micros),
            latency_min_micros: Arc::clone(&self.latency_min_micros),
            latency_max_micros: Arc::clone(&self.latency_max_micros),
            input_queue_depth: Arc::clone(&self.input_queue_depth),
            batch_queue_depth: Arc::clone(&self.batch_queue_depth),
            output_queue_depth: Arc::clone(&self.output_queue_depth),
            start_time: self.start_time,
            prometheus_registry: Arc::clone(&self.prometheus_registry),
            events_read_counter: self.events_read_counter.clone(),
            events_sent_counter: self.events_sent_counter.clone(),
            events_dropped_counter: self.events_dropped_counter.clone(),
            bytes_read_counter: self.bytes_read_counter.clone(),
            bytes_sent_counter: self.bytes_sent_counter.clone(),
            bytes_compressed_counter: self.bytes_compressed_counter.clone(),
            queue_depth_gauge: self.queue_depth_gauge.clone(),
            queue_capacity_gauge: self.queue_capacity_gauge.clone(),
            active_connections_gauge: self.active_connections_gauge.clone(),
            open_files_gauge: self.open_files_gauge.clone(),
            event_latency_histogram: self.event_latency_histogram.clone(),
            compression_ratio_summary: Arc::clone(&self.compression_ratio_summary),
            errors_total_counter: self.errors_total_counter.clone(),
            files_monitored_gauge: self.files_monitored_gauge.clone(),
            files_rotated_counter: self.files_rotated_counter.clone(),
            checkpoint_saves_counter: self.checkpoint_saves_counter.clone(),
        }
    }
}

impl Default for PipelineMetrics {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metrics_recording() {
        let metrics = PipelineMetrics::new();

        metrics.record_events_read(100, "file", "apache_access");
        assert_eq!(metrics.get_events_processed(), 100);

        metrics.record_bytes_read(50000, "file");
        assert_eq!(metrics.get_bytes_processed(), 50000);

        metrics.record_batch_flushed();
        assert_eq!(metrics.get_batches_flushed(), 1);
    }

    #[test]
    fn test_compression_ratio() {
        let metrics = PipelineMetrics::new();

        metrics.record_compression_ratio(1000, 500);
        metrics.record_compression_ratio(1000, 600);

        let avg_ratio = metrics.get_avg_compression_ratio();
        assert!(avg_ratio > 0.5 && avg_ratio < 0.6);
    }

    #[test]
    fn test_latency_tracking() {
        let metrics = PipelineMetrics::new();

        metrics.record_latency_micros(100, "read");
        metrics.record_latency_micros(200, "read");
        metrics.record_latency_micros(150, "read");

        assert_eq!(metrics.get_min_latency_micros(), 100);
        assert_eq!(metrics.get_max_latency_micros(), 200);
        assert!((metrics.get_avg_latency_micros() - 150.0).abs() < 0.1);
    }

    #[test]
    fn test_queue_depths() {
        let metrics = PipelineMetrics::new();

        metrics.update_queue_depths(10, 5, 2);
        let (input, batch, output) = metrics.get_queue_depths();

        assert_eq!(input, 10);
        assert_eq!(batch, 5);
        assert_eq!(output, 2);
    }

    #[test]
    fn test_metrics_clone() {
        let metrics = PipelineMetrics::new();
        metrics.record_events_read(50, "file", "json");

        let cloned = metrics.clone();
        assert_eq!(cloned.get_events_processed(), 50);

        cloned.record_events_read(25, "file", "json");
        assert_eq!(metrics.get_events_processed(), 75);
    }

    #[test]
    fn test_prometheus_registry_export() {
        let metrics = PipelineMetrics::new();

        metrics.record_events_read(100, "file", "apache");
        metrics.record_bytes_compressed(1000);

        let registry = metrics.get_registry();
        let families = registry.gather();

        assert!(!families.is_empty());
    }
}
