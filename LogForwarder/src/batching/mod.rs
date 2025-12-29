use crate::event::Event;
use crate::queue::BatchBuffer;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Copy)]
pub struct BatchingConfig {
    pub min_events: usize,
    pub max_events: usize,
    pub target_size_bytes: usize,
    pub max_size_bytes: usize,
    pub default_flush_interval_ms: u64,
    pub min_flush_interval_ms: u64,
    pub max_flush_interval_ms: u64,
}

impl Default for BatchingConfig {
    fn default() -> Self {
        BatchingConfig {
            min_events: 100,
            max_events: 1000,
            target_size_bytes: 1024 * 1024,
            max_size_bytes: 2 * 1024 * 1024,
            default_flush_interval_ms: 5000,
            min_flush_interval_ms: 1000,
            max_flush_interval_ms: 30000,
        }
    }
}

pub struct AdaptiveBatcher {
    config: BatchingConfig,
    current_batch: BatchBuffer<Event>,
    last_flush: Instant,
    event_rate: EventRateEstimator,
}

impl AdaptiveBatcher {
    pub fn new(config: BatchingConfig) -> Self {
        AdaptiveBatcher {
            current_batch: BatchBuffer::new(config.max_events, config.max_size_bytes),
            config,
            last_flush: Instant::now(),
            event_rate: EventRateEstimator::new(),
        }
    }

    pub fn push(&mut self, event: Event) -> Option<Vec<Event>> {
        self.event_rate.record_event();

        if !self.current_batch.push(event.clone()) {
            let batch = self.current_batch.drain();
            let mut new_batch =
                BatchBuffer::new(self.config.max_events, self.config.max_size_bytes);
            let _ = new_batch.push(event);
            self.current_batch = new_batch;
            self.last_flush = Instant::now();
            return Some(batch);
        }

        if self.should_flush() {
            let batch = self.current_batch.drain();
            self.current_batch =
                BatchBuffer::new(self.config.max_events, self.config.max_size_bytes);
            self.last_flush = Instant::now();
            return Some(batch);
        }

        None
    }

    pub fn flush(&mut self) -> Option<Vec<Event>> {
        if self.current_batch.is_empty() {
            return None;
        }

        let batch = self.current_batch.drain();
        self.current_batch = BatchBuffer::new(self.config.max_events, self.config.max_size_bytes);
        self.last_flush = Instant::now();
        Some(batch)
    }

    fn should_flush(&self) -> bool {
        if self.current_batch.len() >= self.config.min_events {
            return true;
        }

        if self.current_batch.current_size_bytes() >= self.config.target_size_bytes {
            return true;
        }

        let elapsed = self.last_flush.elapsed();
        let timeout = self.adaptive_timeout();
        elapsed >= timeout
    }

    fn adaptive_timeout(&self) -> Duration {
        let rate = self.event_rate.get_rate();
        let timeout_ms = if rate > 10000.0 {
            self.config.min_flush_interval_ms
        } else if rate > 1000.0 {
            self.config.default_flush_interval_ms / 2
        } else if rate < 100.0 {
            self.config.max_flush_interval_ms
        } else {
            self.config.default_flush_interval_ms
        };

        Duration::from_millis(timeout_ms)
    }

    pub fn get_batch_size(&self) -> usize {
        let rate = self.event_rate.get_rate();
        let base_size = self.config.max_events;

        if rate > 10000.0 {
            (base_size as f64 * 0.5) as usize
        } else if rate > 1000.0 {
            (base_size as f64 * 0.75) as usize
        } else {
            base_size
        }
    }

    pub fn current_fill_percentage(&self) -> f64 {
        (self.current_batch.current_size_bytes() as f64) / (self.config.max_size_bytes as f64)
            * 100.0
    }

    pub fn is_batching(&self) -> bool {
        !self.current_batch.is_empty()
    }
}

struct EventRateEstimator {
    event_count: usize,
    window_start: Instant,
}

impl EventRateEstimator {
    fn new() -> Self {
        EventRateEstimator {
            event_count: 0,
            window_start: Instant::now(),
        }
    }

    fn record_event(&mut self) {
        self.event_count += 1;

        if self.window_start.elapsed() >= Duration::from_secs(1) {
            self.event_count = 0;
            self.window_start = Instant::now();
        }
    }

    fn get_rate(&self) -> f64 {
        let elapsed_secs = self.window_start.elapsed().as_secs_f64();
        if elapsed_secs > 0.0 {
            (self.event_count as f64) / elapsed_secs
        } else {
            0.0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::event::SourceType;
    use bytes::Bytes;

    fn create_test_event(data_len: usize) -> Event {
        Event::new(
            Bytes::from(vec![0u8; data_len]),
            1000,
            0,
            1,
            SourceType::Json,
            1,
            1,
        )
    }

    #[test]
    fn test_batching_by_event_count() {
        let mut batcher = AdaptiveBatcher::new(BatchingConfig::default());

        for _ in 0..99 {
            let event = create_test_event(100);
            assert!(batcher.push(event).is_none());
        }

        let event = create_test_event(100);
        let result = batcher.push(event);
        assert!(result.is_some());
        assert_eq!(result.unwrap().len(), 100);
    }

    #[test]
    fn test_batching_by_size() {
        let config = BatchingConfig {
            target_size_bytes: 10000,
            max_events: 1000,
            ..Default::default()
        };
        let mut batcher = AdaptiveBatcher::new(config);

        let large_event = create_test_event(5000);
        assert!(batcher.push(large_event).is_none());

        let large_event = create_test_event(5000);
        let result = batcher.push(large_event);
        assert!(result.is_some());
    }

    #[test]
    fn test_manual_flush() {
        let mut batcher = AdaptiveBatcher::new(BatchingConfig::default());

        let event = create_test_event(100);
        let _ = batcher.push(event);

        let result = batcher.flush();
        assert!(result.is_some());
        assert_eq!(result.unwrap().len(), 1);
        assert!(!batcher.is_batching());
    }

    #[test]
    fn test_batcher_empty_flush() {
        let mut batcher = AdaptiveBatcher::new(BatchingConfig::default());

        let result = batcher.flush();
        assert!(result.is_none());
    }
}
