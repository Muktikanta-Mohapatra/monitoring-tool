use std::collections::HashMap;
use std::path::{Path, PathBuf};
use thiserror::Error;

pub mod metrics_exporter;

#[derive(Error, Debug)]
pub enum MonitorError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Platform not supported: {0}")]
    PlatformNotSupported(String),
    #[error("Invalid watch: {0}")]
    InvalidWatch(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum FileChangeEvent {
    Modified,
    Created,
    Deleted,
    Renamed,
    Attrib,
}

#[derive(Debug, Clone)]
pub struct FileEvent {
    pub path: PathBuf,
    pub event_type: FileChangeEvent,
    pub timestamp: i64,
}

pub trait FileSystemMonitor: Send + Sync {
    fn add_watch(&mut self, path: &Path) -> Result<u64, MonitorError>;
    fn remove_watch(&mut self, watch_id: u64) -> Result<(), MonitorError>;
    fn poll_events(&mut self, timeout_ms: u64) -> Result<Vec<FileEvent>, MonitorError>;
}

pub fn create_monitor() -> Result<Box<dyn FileSystemMonitor>, MonitorError> {
    Ok(Box::new(SimpleFileMonitor::new()))
}

#[derive(Debug)]
pub struct SimpleFileMonitor {
    watches: HashMap<u64, PathBuf>,
    next_watch_id: u64,
}

impl SimpleFileMonitor {
    pub fn new() -> Self {
        SimpleFileMonitor {
            watches: HashMap::new(),
            next_watch_id: 1,
        }
    }

    pub fn watches(&self) -> &HashMap<u64, PathBuf> {
        &self.watches
    }
}

impl Default for SimpleFileMonitor {
    fn default() -> Self {
        Self::new()
    }
}

impl FileSystemMonitor for SimpleFileMonitor {
    fn add_watch(&mut self, path: &Path) -> Result<u64, MonitorError> {
        if !path.exists() {
            return Err(MonitorError::InvalidWatch(format!(
                "Path does not exist: {}",
                path.display()
            )));
        }

        let watch_id = self.next_watch_id;
        self.next_watch_id = self.next_watch_id.wrapping_add(1);
        self.watches.insert(watch_id, path.to_path_buf());

        tracing::info!(watch_id = watch_id, path = %path.display(), "Added watch");
        Ok(watch_id)
    }

    fn remove_watch(&mut self, watch_id: u64) -> Result<(), MonitorError> {
        if self.watches.remove(&watch_id).is_some() {
            tracing::info!(watch_id = watch_id, "Removed watch");
            Ok(())
        } else {
            Err(MonitorError::InvalidWatch(format!(
                "Watch ID not found: {}",
                watch_id
            )))
        }
    }

    fn poll_events(&mut self, _timeout_ms: u64) -> Result<Vec<FileEvent>, MonitorError> {
        Ok(Vec::new())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_simple_monitor_add_watch() -> Result<(), Box<dyn std::error::Error>> {
        let temp_dir = TempDir::new()?;
        let mut monitor = SimpleFileMonitor::new();

        let watch_id = monitor.add_watch(temp_dir.path())?;
        assert_eq!(watch_id, 1);
        assert!(monitor.watches().contains_key(&watch_id));

        Ok(())
    }

    #[test]
    fn test_simple_monitor_remove_watch() -> Result<(), Box<dyn std::error::Error>> {
        let temp_dir = TempDir::new()?;
        let mut monitor = SimpleFileMonitor::new();

        let watch_id = monitor.add_watch(temp_dir.path())?;
        assert!(monitor.remove_watch(watch_id).is_ok());
        assert!(!monitor.watches().contains_key(&watch_id));

        Ok(())
    }

    #[test]
    fn test_monitor_nonexistent_path() {
        let mut monitor = SimpleFileMonitor::new();
        let result = monitor.add_watch(Path::new("/nonexistent/path"));
        assert!(result.is_err());
    }
}
