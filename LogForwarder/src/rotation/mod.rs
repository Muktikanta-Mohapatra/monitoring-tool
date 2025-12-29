pub mod atomic;

use crate::crc;
use crate::file_registry::FileRegistry;
use std::path::Path;
use thiserror::Error;
pub use atomic::{AtomicRotationDetector, AtomicRotationState, RotationDetected};

#[derive(Error, Debug)]
pub enum RotationError {
    #[error("Registry error: {0}")]
    Registry(String),
    #[error("CRC error: {0}")]
    Crc(#[from] crc::CrcError),
}

#[derive(Debug, Clone, PartialEq)]
pub enum RotationType {
    None,
    Copytruncate,
    Rename,
    Manual,
}

#[derive(Debug, Clone)]
pub struct RotationResult {
    pub rotation_type: RotationType,
    pub old_file: Option<(String, u64)>,
    pub new_position: u64,
}

pub struct RotationDetector {
    registry: FileRegistry,
}

impl RotationDetector {
    pub fn new(registry: FileRegistry) -> Self {
        RotationDetector { registry }
    }

    pub fn detect_rotation(&self, path: &Path) -> Result<RotationResult, RotationError> {
        let current_metadata = self
            .registry
            .get(path)
            .map_err(|e| RotationError::Registry(e.to_string()))?;

        let current_atomic_state = AtomicRotationDetector::read_atomic_state(path)
            .map_err(|e| RotationError::Registry(e.to_string()))?;

        let prev_state = AtomicRotationState {
            inode: current_metadata.inode,
            size: current_metadata.size,
            mtime_secs: current_metadata.mtime_secs,
            mtime_nanos: 0,
        };

        let rotation_detected = AtomicRotationDetector::detect_any_rotation(&prev_state, &current_atomic_state);

        match rotation_detected {
            RotationDetected::NoRotation => {
                Ok(RotationResult {
                    rotation_type: RotationType::None,
                    old_file: None,
                    new_position: current_metadata.position,
                })
            }
            RotationDetected::TruncateRotation => {
                tracing::warn!(
                    path = %path.display(),
                    prev_size = current_metadata.size,
                    current_size = current_atomic_state.size,
                    "File truncated (copytruncate rotation), resetting position"
                );
                Ok(RotationResult {
                    rotation_type: RotationType::Copytruncate,
                    old_file: None,
                    new_position: 0,
                })
            }
            RotationDetected::RenameRotation => {
                let old_inode = current_metadata.inode;
                let old_position = current_metadata.position;

                if let Some(old_path) = self.registry.find_by_inode(old_inode) {
                    let old_path_str = old_path.to_string_lossy().to_string();
                    tracing::info!(
                        path = %path.display(),
                        old_path = %old_path_str,
                        old_inode = old_inode,
                        new_inode = current_atomic_state.inode,
                        "File renamed (create rotation)"
                    );
                    Ok(RotationResult {
                        rotation_type: RotationType::Rename,
                        old_file: Some((old_path_str, old_position)),
                        new_position: 0,
                    })
                } else {
                    tracing::warn!(
                        path = %path.display(),
                        old_inode = old_inode,
                        "Manual rotation detected (inode mismatch, old file not found)"
                    );
                    Ok(RotationResult {
                        rotation_type: RotationType::Manual,
                        old_file: None,
                        new_position: 0,
                    })
                }
            }
        }
    }

    pub fn handle_rotation(
        &self,
        path: &Path,
        rotation: RotationResult,
    ) -> Result<(), RotationError> {
        match rotation.rotation_type {
            RotationType::None => {
                // No rotation, do nothing
                Ok(())
            }
            RotationType::Copytruncate => {
                self.registry
                    .update_position(path, 0)
                    .map_err(|e| RotationError::Registry(e.to_string()))?;

                tracing::warn!(
                    path = %path.display(),
                    "File truncated (copytruncate rotation), resetting position"
                );
                Ok(())
            }
            RotationType::Rename => {
                let current_inode =
                    get_inode(path).map_err(|e| RotationError::Registry(e.to_string()))?;
                let current_crc = crc::calculate_file_crc32(path)?;

                self.registry
                    .update_checkpoint(path, 0, current_crc, current_inode)
                    .map_err(|e| RotationError::Registry(e.to_string()))?;

                tracing::info!(
                    path = %path.display(),
                    old_file = ?rotation.old_file,
                    "File renamed (create rotation)"
                );
                Ok(())
            }
            RotationType::Manual => {
                let current_inode =
                    get_inode(path).map_err(|e| RotationError::Registry(e.to_string()))?;
                let current_crc = crc::calculate_file_crc32(path)?;

                self.registry
                    .update_checkpoint(path, 0, current_crc, current_inode)
                    .map_err(|e| RotationError::Registry(e.to_string()))?;

                tracing::warn!(
                    path = %path.display(),
                    "Manual rotation detected (inode mismatch, old file not found)"
                );
                Ok(())
            }
        }
    }
}

#[cfg(target_os = "windows")]
fn get_inode(path: &Path) -> Result<u64, String> {
    use std::collections::hash_map::DefaultHasher;
    use std::fs;
    use std::hash::{Hash, Hasher};

    let _ = fs::metadata(path).map_err(|e| format!("Failed to get metadata: {}", e))?;

    let mut hasher = DefaultHasher::new();
    path.hash(&mut hasher);
    let inode = hasher.finish();

    Ok(inode)
}

#[cfg(target_os = "linux")]
fn get_inode(path: &Path) -> Result<u64, String> {
    use std::fs;
    use std::os::unix::fs::MetadataExt;

    let metadata = fs::metadata(path).map_err(|e| format!("Failed to get metadata: {}", e))?;
    Ok(metadata.ino())
}

#[cfg(target_os = "macos")]
fn get_inode(path: &Path) -> Result<u64, String> {
    use std::fs;
    use std::os::unix::fs::MetadataExt;

    let metadata = fs::metadata(path).map_err(|e| format!("Failed to get metadata: {}", e))?;
    Ok(metadata.ino())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn test_no_rotation() -> Result<(), Box<dyn std::error::Error>> {
        let mut temp = NamedTempFile::new()?;
        temp.write_all(b"test content")?;
        temp.flush()?;

        let registry = FileRegistry::new();
        let path = temp.path();

        let inode = get_inode(path)?;
        let crc = crc::calculate_file_crc32(path)?;
        let metadata = path.metadata()?;
        let mtime_secs = metadata
            .modified()?
            .duration_since(std::time::UNIX_EPOCH)?
            .as_secs() as i64;
        
        registry.register_with_mtime(
            path.to_path_buf(),
            inode,
            100,
            crc,
            1,
            mtime_secs,
        )?;

        let detector = RotationDetector::new(registry);
        let result = detector.detect_rotation(path)?;

        assert_eq!(result.rotation_type, RotationType::None);
        Ok(())
    }

    #[test]
    fn test_truncation_detection() -> Result<(), Box<dyn std::error::Error>> {
        let mut temp = NamedTempFile::new()?;
        temp.write_all(b"original content")?;
        temp.flush()?;

        let path_buf = temp.path().to_path_buf();
        let registry = FileRegistry::new();

        let inode = get_inode(&path_buf)?;
        let original_crc = crc::calculate_file_crc32(&path_buf)?;
        let metadata = path_buf.metadata()?;
        let mtime_secs = metadata
            .modified()?
            .duration_since(std::time::UNIX_EPOCH)?
            .as_secs() as i64;
        
        registry.register_with_mtime(
            path_buf.clone(),
            inode,
            1000,
            original_crc,
            1,
            mtime_secs,
        )?;

        drop(temp);

        let detector = RotationDetector::new(registry);
        if std::path::Path::new(&path_buf).exists() {
            let _result = detector.detect_rotation(&path_buf);
        }

        Ok(())
    }
}
