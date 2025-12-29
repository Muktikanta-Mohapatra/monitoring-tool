use std::sync::{Arc, Mutex};
use std::time::SystemTime;
use tracing::{error, warn};

#[derive(Debug, Clone)]
pub struct DeadLetterEntry {
    pub batch_id: String,
    pub indexer_id: String,
    pub data_size: usize,
    pub error_message: String,
    pub timestamp: SystemTime,
    pub retry_count: u32,
}

pub struct DeadLetterQueue {
    entries: Arc<Mutex<Vec<DeadLetterEntry>>>,
    max_entries: usize,
}

impl DeadLetterQueue {
    pub fn new(max_entries: usize) -> Self {
        DeadLetterQueue {
            entries: Arc::new(Mutex::new(Vec::new())),
            max_entries,
        }
    }

    pub fn log_failed_batch(
        &self,
        batch_id: String,
        indexer_id: String,
        data_size: usize,
        error_message: String,
        retry_count: u32,
    ) -> Result<(), String> {
        let mut entries = self.entries.lock().map_err(|e| e.to_string())?;

        if entries.len() >= self.max_entries {
            warn!("Dead-letter queue full ({} entries), dropping oldest entry", self.max_entries);
            entries.remove(0);
        }

        let entry = DeadLetterEntry {
            batch_id,
            indexer_id,
            data_size,
            error_message,
            timestamp: SystemTime::now(),
            retry_count,
        };

        error!(
            "Dead-letter entry: batch_id={}, retry_count={}, error={}",
            entry.batch_id, entry.retry_count, entry.error_message
        );

        entries.push(entry);
        Ok(())
    }

    pub fn get_entries(&self) -> Result<Vec<DeadLetterEntry>, String> {
        self.entries
            .lock()
            .map(|entries| entries.clone())
            .map_err(|e| e.to_string())
    }

    pub fn clear(&self) -> Result<(), String> {
        self.entries
            .lock()
            .map(|mut entries| entries.clear())
            .map_err(|e| e.to_string())
    }

    pub fn entry_count(&self) -> Result<usize, String> {
        self.entries
            .lock()
            .map(|entries| entries.len())
            .map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dead_letter_queue_creation() {
        let dlq = DeadLetterQueue::new(100);
        assert_eq!(dlq.entry_count().unwrap(), 0);
    }

    #[test]
    fn test_log_failed_batch() {
        let dlq = DeadLetterQueue::new(100);

        let result = dlq.log_failed_batch(
            "batch-123".to_string(),
            "indexer-001".to_string(),
            1024,
            "Connection timeout".to_string(),
            1,
        );

        assert!(result.is_ok());
        assert_eq!(dlq.entry_count().unwrap(), 1);
    }

    #[test]
    fn test_dead_letter_queue_max_entries() {
        let dlq = DeadLetterQueue::new(3);

        for i in 0..5 {
            let _ = dlq.log_failed_batch(
                format!("batch-{}", i),
                format!("indexer-{}", i),
                1024,
                "Error".to_string(),
                1,
            );
        }

        assert_eq!(dlq.entry_count().unwrap(), 3);
    }

    #[test]
    fn test_get_entries() {
        let dlq = DeadLetterQueue::new(100);

        dlq.log_failed_batch(
            "batch-123".to_string(),
            "indexer-001".to_string(),
            1024,
            "Connection timeout".to_string(),
            1,
        )
        .unwrap();

        dlq.log_failed_batch(
            "batch-456".to_string(),
            "indexer-002".to_string(),
            2048,
            "Service unavailable".to_string(),
            2,
        )
        .unwrap();

        let entries = dlq.get_entries().unwrap();
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].batch_id, "batch-123");
        assert_eq!(entries[1].batch_id, "batch-456");
    }

    #[test]
    fn test_clear_entries() {
        let dlq = DeadLetterQueue::new(100);

        dlq.log_failed_batch(
            "batch-123".to_string(),
            "indexer-001".to_string(),
            1024,
            "Error".to_string(),
            1,
        )
        .unwrap();

        assert_eq!(dlq.entry_count().unwrap(), 1);
        dlq.clear().unwrap();
        assert_eq!(dlq.entry_count().unwrap(), 0);
    }
}
