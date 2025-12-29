use memmap2::MmapMut;
use std::fs::OpenOptions;
use std::path::Path;
use std::sync::Arc;
use thiserror::Error;

const MAGIC: u32 = 0xDEADBEEF;
const VERSION: u32 = 1;
const HEADER_SIZE: usize = 64;
const RECORD_SIZE: usize = 128;
const BUCKET_COUNT: usize = 65536;
const BUCKET_SIZE: usize = 16;
const INDEX_SIZE: usize = BUCKET_COUNT * BUCKET_SIZE;

#[derive(Error, Debug)]
pub enum CheckpointError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Invalid checkpoint format")]
    InvalidFormat,
    #[error("Checkpoint not found")]
    NotFound,
}

#[repr(C)]
struct CheckpointHeader {
    magic: u32,
    version: u32,
    count: u64,
    capacity: u64,
    checksum: u64,
    _reserved: [u8; 24],
}

#[repr(C)]
pub struct CheckpointRecord {
    pub source_id_hash: u64,
    pub inode: u64,
    pub position: u64,
    pub file_size: u64,
    pub crc32: u32,
    pub _reserved1: u32,
    pub last_modified: i64,
    pub last_updated: i64,
    pub _reserved2: [u8; 56],
}

pub struct CheckpointStore {
    mmap: Arc<parking_lot::Mutex<MmapMut>>,
    path: String,
}

impl CheckpointStore {
    pub fn path(&self) -> &str {
        &self.path
    }

    pub fn open<P: AsRef<Path>>(path: P, capacity: usize) -> Result<Self, CheckpointError> {
        let path = path.as_ref().to_string_lossy().to_string();
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .open(&path)?;

        let total_size = HEADER_SIZE + INDEX_SIZE + (capacity * RECORD_SIZE);

        if file.metadata()?.len() == 0 {
            file.set_len(total_size as u64)?;
        }

        let mut mmap = unsafe { MmapMut::map_mut(&file)? };

        if mmap.len() < HEADER_SIZE {
            return Err(CheckpointError::InvalidFormat);
        }

        unsafe {
            let header = &*(mmap.as_ptr() as *const CheckpointHeader);
            if header.magic != MAGIC {
                Self::init_header(&mut mmap, capacity as u64);
            }
        }

        Ok(CheckpointStore {
            mmap: Arc::new(parking_lot::Mutex::new(mmap)),
            path,
        })
    }

    fn init_header(mmap: &mut MmapMut, capacity: u64) {
        unsafe {
            let header = &mut *(mmap.as_mut_ptr() as *mut CheckpointHeader);
            header.magic = MAGIC;
            header.version = VERSION;
            header.count = 0;
            header.capacity = capacity;
            header.checksum = 0;
        }
    }

    pub fn get(&self, source_hash: u64) -> Result<CheckpointRecord, CheckpointError> {
        let mmap = self.mmap.lock();
        let bucket_idx = (source_hash as usize) % BUCKET_COUNT;
        let offset = HEADER_SIZE + (bucket_idx * BUCKET_SIZE);

        if offset + BUCKET_SIZE > mmap.len() {
            return Err(CheckpointError::NotFound);
        }

        unsafe {
            let stored_idx = *(mmap.as_ptr().add(offset) as *const u64);
            if stored_idx == 0 {
                return Err(CheckpointError::NotFound);
            }

            let record_idx = (stored_idx - 1) as usize;
            let record_offset = HEADER_SIZE + INDEX_SIZE + (record_idx * RECORD_SIZE);
            let record = (mmap.as_ptr().add(record_offset) as *const CheckpointRecord).read();
            Ok(record)
        }
    }

    pub fn put(
        &self,
        source_hash: u64,
        inode: u64,
        position: u64,
        file_size: u64,
        crc32: u32,
        last_modified: i64,
    ) -> Result<(), CheckpointError> {
        let mut mmap = self.mmap.lock();

        unsafe {
            let header = &mut *(mmap.as_mut_ptr() as *mut CheckpointHeader);
            let record_idx = header.count;

            if record_idx >= header.capacity {
                return Err(CheckpointError::InvalidFormat);
            }

            let record_offset = HEADER_SIZE + INDEX_SIZE + (record_idx as usize * RECORD_SIZE);
            let last_updated = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_nanos() as i64)
                .unwrap_or_else(|e| {
                    tracing::warn!("Failed to get current system time: {}", e);
                    0
                });

            let record = CheckpointRecord {
                source_id_hash: source_hash,
                inode,
                position,
                file_size,
                crc32,
                _reserved1: 0,
                last_modified,
                last_updated,
                _reserved2: [0; 56],
            };

            std::ptr::copy_nonoverlapping(
                &record as *const _ as *const u8,
                mmap.as_mut_ptr().add(record_offset),
                RECORD_SIZE,
            );

            let bucket_idx = (source_hash as usize) % BUCKET_COUNT;
            let bucket_offset = HEADER_SIZE + (bucket_idx * BUCKET_SIZE);
            *(mmap.as_mut_ptr().add(bucket_offset) as *mut u64) = record_idx + 1;

            header.count += 1;
        }

        mmap.flush()?;
        Ok(())
    }

    pub fn flush(&self) -> Result<(), CheckpointError> {
        self.mmap.lock().flush()?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    #[test]
    fn test_checkpoint_store() -> Result<(), Box<dyn std::error::Error>> {
        let temp = NamedTempFile::new()?;
        let store = CheckpointStore::open(temp.path(), 1000)?;

        store.put(1001, 12345, 5000, 100000, 0xABCD, 1699000000)?;
        let record = store.get(1001)?;

        assert_eq!(record.inode, 12345);
        assert_eq!(record.position, 5000);
        assert_eq!(record.crc32, 0xABCD);

        Ok(())
    }
}
