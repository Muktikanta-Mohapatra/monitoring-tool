use parking_lot::RwLock;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum RegistryError {
    #[error("File not found in registry: {0}")]
    NotFound(String),
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}

#[derive(Debug, Clone)]
pub struct FileMetadata {
    pub path: PathBuf,
    pub inode: u64,
    pub size: u64,
    pub crc32: u32,
    pub last_modified: i64,
    pub position: u64,
    pub watch_id: u64,
    pub mtime_secs: i64,
    pub rotation_detected_at: i64,
}

impl FileMetadata {
    pub fn new(path: PathBuf, inode: u64, size: u64, crc32: u32, watch_id: u64) -> Self {
        FileMetadata {
            path,
            inode,
            size,
            crc32,
            last_modified: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            position: 0,
            watch_id,
            mtime_secs: 0,
            rotation_detected_at: 0,
        }
    }

    pub fn with_mtime(mut self, mtime_secs: i64) -> Self {
        self.mtime_secs = mtime_secs;
        self
    }

    pub fn has_rotated(&self, current_inode: u64, current_crc: u32) -> bool {
        self.inode != current_inode || self.crc32 != current_crc
    }

    pub fn set_rotation_detected(&mut self) {
        self.rotation_detected_at = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
    }

    pub fn is_rotation_recent(&self, threshold_secs: i64) -> bool {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        
        self.rotation_detected_at > 0 && (now - self.rotation_detected_at) < threshold_secs
    }
}

pub struct FileRegistry {
    files: Arc<RwLock<HashMap<PathBuf, FileMetadata>>>,
    by_inode: Arc<RwLock<HashMap<u64, PathBuf>>>,
}

impl FileRegistry {
    pub fn new() -> Self {
        FileRegistry {
            files: Arc::new(RwLock::new(HashMap::new())),
            by_inode: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub fn register(
        &self,
        path: PathBuf,
        inode: u64,
        size: u64,
        crc32: u32,
        watch_id: u64,
    ) -> Result<(), RegistryError> {
        let metadata = FileMetadata::new(path.clone(), inode, size, crc32, watch_id);

        let mut files = self.files.write();
        let mut by_inode = self.by_inode.write();

        files.insert(path.clone(), metadata);
        by_inode.insert(inode, path);

        Ok(())
    }

    pub fn register_with_mtime(
        &self,
        path: PathBuf,
        inode: u64,
        size: u64,
        crc32: u32,
        watch_id: u64,
        mtime_secs: i64,
    ) -> Result<(), RegistryError> {
        let mut metadata = FileMetadata::new(path.clone(), inode, size, crc32, watch_id);
        metadata.mtime_secs = mtime_secs;

        let mut files = self.files.write();
        let mut by_inode = self.by_inode.write();

        files.insert(path.clone(), metadata);
        by_inode.insert(inode, path);

        Ok(())
    }

    pub fn get(&self, path: &Path) -> Result<FileMetadata, RegistryError> {
        self.files
            .read()
            .get(path)
            .cloned()
            .ok_or_else(|| RegistryError::NotFound(path.display().to_string()))
    }

    pub fn update_position(&self, path: &Path, position: u64) -> Result<(), RegistryError> {
        self.files
            .write()
            .get_mut(path)
            .ok_or_else(|| RegistryError::NotFound(path.display().to_string()))
            .map(|meta| {
                meta.position = position;
            })
    }

    pub fn update_checkpoint(
        &self,
        path: &Path,
        position: u64,
        crc32: u32,
        inode: u64,
    ) -> Result<(), RegistryError> {
        self.files
            .write()
            .get_mut(path)
            .ok_or_else(|| RegistryError::NotFound(path.display().to_string()))
            .map(|meta| {
                meta.position = position;
                meta.crc32 = crc32;
                meta.inode = inode;
                meta.last_modified = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs() as i64;
            })
    }

    pub fn find_by_inode(&self, inode: u64) -> Option<PathBuf> {
        self.by_inode.read().get(&inode).cloned()
    }

    pub fn unregister(&self, path: &Path) -> Result<(), RegistryError> {
        let mut files = self.files.write();
        if let Some(metadata) = files.remove(path) {
            let mut by_inode = self.by_inode.write();
            by_inode.remove(&metadata.inode);
            Ok(())
        } else {
            Err(RegistryError::NotFound(path.display().to_string()))
        }
    }

    pub fn list_files(&self) -> Vec<FileMetadata> {
        self.files.read().values().cloned().collect()
    }

    pub fn count(&self) -> usize {
        self.files.read().len()
    }
}

impl Default for FileRegistry {
    fn default() -> Self {
        Self::new()
    }
}

impl Clone for FileRegistry {
    fn clone(&self) -> Self {
        FileRegistry {
            files: Arc::clone(&self.files),
            by_inode: Arc::clone(&self.by_inode),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_register_file() -> Result<(), Box<dyn std::error::Error>> {
        let registry = FileRegistry::new();
        let path = PathBuf::from("/var/log/test.log");

        registry.register(path.clone(), 12345, 1000, 0xABCD, 1)?;
        assert_eq!(registry.count(), 1);

        let meta = registry.get(&path)?;
        assert_eq!(meta.inode, 12345);
        assert_eq!(meta.crc32, 0xABCD);

        Ok(())
    }

    #[test]
    fn test_find_by_inode() -> Result<(), Box<dyn std::error::Error>> {
        let registry = FileRegistry::new();
        let path = PathBuf::from("/var/log/test.log");

        registry.register(path.clone(), 12345, 1000, 0xABCD, 1)?;

        assert_eq!(registry.find_by_inode(12345), Some(path));
        assert_eq!(registry.find_by_inode(99999), None);

        Ok(())
    }

    #[test]
    fn test_update_checkpoint() -> Result<(), Box<dyn std::error::Error>> {
        let registry = FileRegistry::new();
        let path = PathBuf::from("/var/log/test.log");

        registry.register(path.clone(), 12345, 1000, 0xABCD, 1)?;
        registry.update_checkpoint(&path, 500, 0xDEF0, 67890)?;

        let meta = registry.get(&path)?;
        assert_eq!(meta.position, 500);
        assert_eq!(meta.crc32, 0xDEF0);
        assert_eq!(meta.inode, 67890);

        Ok(())
    }

    #[test]
    fn test_has_rotated() {
        let meta = FileMetadata::new(PathBuf::from("/var/log/test.log"), 12345, 1000, 0xABCD, 1);

        assert!(!meta.has_rotated(12345, 0xABCD));
        assert!(meta.has_rotated(67890, 0xABCD));
        assert!(meta.has_rotated(12345, 0xDEF0));
    }

    #[test]
    fn test_unregister_file() -> Result<(), Box<dyn std::error::Error>> {
        let registry = FileRegistry::new();
        let path = PathBuf::from("/var/log/test.log");

        registry.register(path.clone(), 12345, 1000, 0xABCD, 1)?;
        assert_eq!(registry.count(), 1);

        registry.unregister(&path)?;
        assert_eq!(registry.count(), 0);
        assert!(registry.find_by_inode(12345).is_none());

        Ok(())
    }
}
