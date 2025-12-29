use crate::event::Event;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub struct BatchManagerConfig {
    pub batch_size: usize,
    pub batch_timeout_ms: u64,
}

impl BatchManagerConfig {
    pub fn new(batch_size: usize, batch_timeout_ms: u64) -> Self {
        BatchManagerConfig {
            batch_size,
            batch_timeout_ms,
        }
    }
}

pub struct InputBatchAccumulator {
    config: BatchManagerConfig,
    accumulated_events: Vec<Event>,
    accumulation_start: Instant,
}

impl InputBatchAccumulator {
    pub fn new(config: BatchManagerConfig) -> Self {
        let batch_size = config.batch_size;
        InputBatchAccumulator {
            config,
            accumulated_events: Vec::with_capacity(batch_size),
            accumulation_start: Instant::now(),
        }
    }

    pub fn add_event(&mut self, event: Event) -> Option<Vec<Event>> {
        self.accumulated_events.push(event);
        
        if self.accumulated_events.len() >= self.config.batch_size {
            return Some(self.flush_batch());
        }
        
        None
    }

    pub fn add_events(&mut self, events: Vec<Event>) -> Option<Vec<Event>> {
        for event in events {
            self.accumulated_events.push(event);
            
            if self.accumulated_events.len() >= self.config.batch_size {
                return Some(self.flush_batch());
            }
        }
        
        None
    }

    pub fn flush(&mut self) -> Option<Vec<Event>> {
        if self.accumulated_events.is_empty() {
            return None;
        }
        
        Some(self.flush_batch())
    }

    fn flush_batch(&mut self) -> Vec<Event> {
        let batch = std::mem::take(&mut self.accumulated_events);
        self.accumulated_events = Vec::with_capacity(self.config.batch_size);
        self.accumulation_start = Instant::now();
        batch
    }

    pub fn current_batch_size(&self) -> usize {
        self.accumulated_events.len()
    }

    pub fn is_batch_ready(&self) -> bool {
        self.accumulated_events.len() >= self.config.batch_size
    }

    pub fn time_since_batch_start(&self) -> Duration {
        self.accumulation_start.elapsed()
    }

    pub fn has_events(&self) -> bool {
        !self.accumulated_events.is_empty()
    }

    pub fn get_batch_size(&self) -> usize {
        self.config.batch_size
    }
}

pub struct ThreadSafeBatchAccumulator {
    inner: Arc<Mutex<InputBatchAccumulator>>,
}

impl ThreadSafeBatchAccumulator {
    pub fn new(config: BatchManagerConfig) -> Self {
        ThreadSafeBatchAccumulator {
            inner: Arc::new(Mutex::new(InputBatchAccumulator::new(config))),
        }
    }

    pub fn add_event(&self, event: Event) -> Option<Vec<Event>> {
        if let Ok(mut accumulator) = self.inner.lock() {
            accumulator.add_event(event)
        } else {
            None
        }
    }

    pub fn add_events(&self, events: Vec<Event>) -> Option<Vec<Event>> {
        if let Ok(mut accumulator) = self.inner.lock() {
            accumulator.add_events(events)
        } else {
            None
        }
    }

    pub fn flush(&self) -> Option<Vec<Event>> {
        if let Ok(mut accumulator) = self.inner.lock() {
            accumulator.flush()
        } else {
            None
        }
    }

    pub fn current_batch_size(&self) -> usize {
        if let Ok(accumulator) = self.inner.lock() {
            accumulator.current_batch_size()
        } else {
            0
        }
    }

    pub fn is_batch_ready(&self) -> bool {
        if let Ok(accumulator) = self.inner.lock() {
            accumulator.is_batch_ready()
        } else {
            false
        }
    }

    pub fn has_events(&self) -> bool {
        if let Ok(accumulator) = self.inner.lock() {
            accumulator.has_events()
        } else {
            false
        }
    }
}

impl Clone for ThreadSafeBatchAccumulator {
    fn clone(&self) -> Self {
        ThreadSafeBatchAccumulator {
            inner: Arc::clone(&self.inner),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::event::SourceType;
    use bytes::Bytes;

    fn create_test_event(id: usize) -> Event {
        Event::new(
            Bytes::from(format!("test_event_{}", id)),
            1000 + id as i64,
            id as u64,
            1,
            SourceType::Json,
            1,
            1,
        )
    }

    #[test]
    fn test_accumulator_batching() {
        let config = BatchManagerConfig::new(5, 5000);
        let mut accumulator = InputBatchAccumulator::new(config);

        for i in 0..4 {
            let result = accumulator.add_event(create_test_event(i));
            assert!(result.is_none(), "Should not return batch until reaching batch_size");
        }

        let result = accumulator.add_event(create_test_event(4));
        assert!(result.is_some(), "Should return batch when reaching batch_size");
        assert_eq!(result.unwrap().len(), 5);
    }

    #[test]
    fn test_accumulator_add_events() {
        let config = BatchManagerConfig::new(3, 5000);
        let mut accumulator = InputBatchAccumulator::new(config);

        let events = vec![
            create_test_event(0),
            create_test_event(1),
            create_test_event(2),
        ];

        let result = accumulator.add_events(events);
        assert!(result.is_some());
        assert_eq!(result.unwrap().len(), 3);
    }

    #[test]
    fn test_accumulator_flush() {
        let config = BatchManagerConfig::new(5, 5000);
        let mut accumulator = InputBatchAccumulator::new(config);

        for i in 0..3 {
            let _ = accumulator.add_event(create_test_event(i));
        }

        let result = accumulator.flush();
        assert!(result.is_some());
        assert_eq!(result.unwrap().len(), 3);

        let result2 = accumulator.flush();
        assert!(result2.is_none(), "Empty accumulator should return None");
    }

    #[test]
    fn test_thread_safe_accumulator() {
        let config = BatchManagerConfig::new(2, 5000);
        let accumulator = ThreadSafeBatchAccumulator::new(config);

        let result = accumulator.add_event(create_test_event(0));
        assert!(result.is_none());

        let result = accumulator.add_event(create_test_event(1));
        assert!(result.is_some());
        assert_eq!(result.unwrap().len(), 2);
    }
}
