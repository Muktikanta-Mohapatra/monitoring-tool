use std::fs::Metadata;
use std::path::Path;
use std::io;
use tracing::{info, warn};

pub struct AtomicRotationState {
    pub inode: u64,
    pub size: u64,
    pub mtime_secs: i64,
    pub mtime_nanos: u32,
}

pub struct AtomicRotationDetector;

impl AtomicRotationDetector {
    pub fn read_atomic_state(path: &Path) -> io::Result<AtomicRotationState> {
        let metadata = path.metadata()?;
        
        let inode = get_inode(&metadata);
        let size = metadata.len();
        
        let mtime = metadata.modified()?;
        let duration = mtime
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default();
        let mtime_secs = duration.as_secs() as i64;
        let mtime_nanos = duration.subsec_nanos();
        
        Ok(AtomicRotationState {
            inode,
            size,
            mtime_secs,
            mtime_nanos,
        })
    }

    pub fn detect_rename_rotation(
        prev_inode: u64,
        current_inode: u64,
        _prev_size: u64,
        _current_size: u64,
    ) -> bool {
        inode_changed(prev_inode, current_inode)
    }

    pub fn detect_truncate_rotation(
        prev_inode: u64,
        current_inode: u64,
        prev_size: u64,
        current_size: u64,
    ) -> bool {
        same_inode(prev_inode, current_inode) && size_decreased(prev_size, current_size)
    }

    pub fn detect_any_rotation(
        prev_state: &AtomicRotationState,
        current_state: &AtomicRotationState,
    ) -> RotationDetected {
        if inode_changed(prev_state.inode, current_state.inode) {
            info!(
                prev_inode = prev_state.inode,
                current_inode = current_state.inode,
                "Rename rotation detected (inode changed)"
            );
            return RotationDetected::RenameRotation;
        }

        if same_inode(prev_state.inode, current_state.inode)
            && size_decreased(prev_state.size, current_state.size)
        {
            info!(
                inode = current_state.inode,
                prev_size = prev_state.size,
                current_size = current_state.size,
                "Truncate rotation detected (size decreased)"
            );
            return RotationDetected::TruncateRotation;
        }

        if high_frequency_writes(prev_state.mtime_secs, current_state.mtime_secs) {
            let size_growth = current_state.size.saturating_sub(prev_state.size);
            if size_growth > 10 * 1024 * 1024 {
                warn!(
                    "High-frequency writes detected: {} MB in last poll",
                    size_growth / (1024 * 1024)
                );
            }
        }

        RotationDetected::NoRotation
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RotationDetected {
    NoRotation,
    RenameRotation,
    TruncateRotation,
}

#[inline]
fn inode_changed(prev: u64, current: u64) -> bool {
    prev != current && prev != 0
}

#[inline]
fn same_inode(prev: u64, current: u64) -> bool {
    prev == current && prev != 0
}

#[inline]
fn size_decreased(prev_size: u64, current_size: u64) -> bool {
    current_size < prev_size
}

#[inline]
fn high_frequency_writes(prev_mtime: i64, current_mtime: i64) -> bool {
    current_mtime > prev_mtime && (current_mtime - prev_mtime) < 1
}

#[cfg(target_os = "windows")]
fn get_inode(metadata: &Metadata) -> u64 {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};

    let mut hasher = DefaultHasher::new();
    metadata.len().hash(&mut hasher);
    metadata.modified()
        .ok()
        .hash(&mut hasher);
    
    hasher.finish()
}

#[cfg(target_os = "linux")]
fn get_inode(metadata: &Metadata) -> u64 {
    use std::os::unix::fs::MetadataExt;
    metadata.ino()
}

#[cfg(target_os = "macos")]
fn get_inode(metadata: &Metadata) -> u64 {
    use std::os::unix::fs::MetadataExt;
    metadata.ino()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rename_rotation_detection() {
        let detected = AtomicRotationDetector::detect_rename_rotation(1000, 1001, 1024, 1024);
        assert!(detected);
    }

    #[test]
    fn test_no_rotation_same_inode_same_size() {
        let detected = AtomicRotationDetector::detect_rename_rotation(1000, 1000, 1024, 2048);
        assert!(!detected);
    }

    #[test]
    fn test_truncate_rotation_detection() {
        let detected =
            AtomicRotationDetector::detect_truncate_rotation(1000, 1000, 2048, 1024);
        assert!(detected);
    }

    #[test]
    fn test_no_truncate_if_inode_changed() {
        let detected =
            AtomicRotationDetector::detect_truncate_rotation(1000, 1001, 2048, 1024);
        assert!(!detected);
    }

    #[test]
    fn test_rotation_detected_enum() {
        let prev_state = AtomicRotationState {
            inode: 1000,
            size: 2048,
            mtime_secs: 100,
            mtime_nanos: 0,
        };

        let current_state = AtomicRotationState {
            inode: 1001,
            size: 1024,
            mtime_secs: 101,
            mtime_nanos: 0,
        };

        let result = AtomicRotationDetector::detect_any_rotation(&prev_state, &current_state);
        assert_eq!(result, RotationDetected::RenameRotation);
    }

    #[test]
    fn test_truncate_rotation_enum() {
        let prev_state = AtomicRotationState {
            inode: 1000,
            size: 2048,
            mtime_secs: 100,
            mtime_nanos: 0,
        };

        let current_state = AtomicRotationState {
            inode: 1000,
            size: 512,
            mtime_secs: 101,
            mtime_nanos: 0,
        };

        let result = AtomicRotationDetector::detect_any_rotation(&prev_state, &current_state);
        assert_eq!(result, RotationDetected::TruncateRotation);
    }

    #[test]
    fn test_no_rotation_enum() {
        let prev_state = AtomicRotationState {
            inode: 1000,
            size: 1024,
            mtime_secs: 100,
            mtime_nanos: 0,
        };

        let current_state = AtomicRotationState {
            inode: 1000,
            size: 2048,
            mtime_secs: 101,
            mtime_nanos: 0,
        };

        let result = AtomicRotationDetector::detect_any_rotation(&prev_state, &current_state);
        assert_eq!(result, RotationDetected::NoRotation);
    }
}
