pub mod plugin;
pub mod scripted;
pub mod syslog;
pub mod tcp;
pub mod udp;
pub mod windows_eventlog;

use crate::config::FileInputConfig;
use crate::event::{Event, SourceType};
use std::fs::File;
use std::io::{BufReader, Read, Seek, SeekFrom};
use std::path::Path;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum InputError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Invalid encoding")]
    InvalidEncoding,
}

pub struct FileInput {
    config: FileInputConfig,
    file: BufReader<File>,
    current_position: u64,
    file_size: u64,
    file_path: String,
    inode: u64,
}

impl FileInput {
    pub fn open<P: AsRef<Path>>(config: FileInputConfig, path: P) -> Result<Self, InputError> {
        Self::open_from_position(config, path, None)
    }

    pub fn open_from_position<P: AsRef<Path>>(
        config: FileInputConfig,
        path: P,
        checkpoint_position: Option<u64>,
    ) -> Result<Self, InputError> {
        let path_ref = path.as_ref();
        let file = File::open(path_ref)?;
        let metadata = file.metadata()?;
        let file_size = metadata.len();
        let inode = get_inode(&metadata);

        let mut buf_reader = BufReader::with_capacity(config.read_buffer_kb * 1024, file);

        let current_position = if let Some(checkpoint_pos) = checkpoint_position {
            if checkpoint_pos < file_size {
                buf_reader.seek(SeekFrom::Start(checkpoint_pos))?
            } else {
                buf_reader.seek(SeekFrom::End(0))?
            }
        } else if config.follow_tail {
            buf_reader.seek(SeekFrom::End(0))?
        } else {
            0
        };

        Ok(FileInput {
            config,
            file: buf_reader,
            current_position,
            file_size,
            file_path: path_ref.to_string_lossy().to_string(),
            inode,
        })
    }

    pub fn read_events(&mut self) -> Result<Vec<Event>, InputError> {
        let mut events = Vec::with_capacity(self.config.batch_size);
        let mut buffer = vec![0; self.config.read_buffer_kb * 1024];
        let mut line_buffer = Vec::new();

        loop {
            let n = self.file.read(&mut buffer)?;
            if n == 0 {
                if !line_buffer.is_empty() {
                    if let Some(event) = self.create_event(&line_buffer)? {
                        events.push(event);
                    }
                }
                break;
            }
            let mut start = 0;

            for (i, &byte) in buffer[..n].iter().enumerate() {
                if byte == b'\n' {
                    line_buffer.extend_from_slice(&buffer[start..=i]);

                    if let Some(event) = self.create_event(&line_buffer)? {
                        events.push(event);
                    }

                    if events.len() >= self.config.batch_size {
                        let bytes_in_current_buffer = i + 1;
                        let bytes_not_consumed = n - bytes_in_current_buffer;
                        self.current_position += bytes_in_current_buffer as u64;
                        
                        if bytes_not_consumed > 0 {
                            let _ = self.file.seek(SeekFrom::Current(-(bytes_not_consumed as i64)));
                        }
                        
                        self.file_size = self.file.get_ref().metadata()?.len();
                        tracing::debug!(
                            "read_events(): returning {} events at position {} (batch size: {})",
                            events.len(),
                            self.current_position,
                            self.config.batch_size
                        );
                        return Ok(events);
                    }

                    line_buffer.clear();
                    start = i + 1;
                }
            }

            if start < n {
                line_buffer.extend_from_slice(&buffer[start..n]);
            }

            self.current_position += n as u64;
        }

        self.file_size = self.file.get_ref().metadata()?.len();
        tracing::debug!(
            "read_events(): returning {} events at EOF, position {} (read_buffer_kb: {})",
            events.len(),
            self.current_position,
            self.config.read_buffer_kb
        );
        Ok(events)
    }

    fn create_event(&self, line: &[u8]) -> Result<Option<Event>, InputError> {
        if line.is_empty() || line == b"\n" {
            return Ok(None);
        }

        let source_type = match self.config.sourcetype.as_str() {
            "apache_access" => SourceType::ApacheAccess,
            "apache_error" => SourceType::ApacheError,
            "json" => SourceType::Json,
            "syslog" => SourceType::Syslog,
            "csv" => SourceType::Csv,
            _ => SourceType::Unknown,
        };

        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos() as i64;

        let event = Event::new(
            bytes::Bytes::copy_from_slice(line),
            timestamp,
            self.current_position,
            fnv_hash(&self.config.name) as u32,
            source_type,
            fnv_hash(&self.config.path) as u32,
            0,
        );

        Ok(Some(event))
    }

    pub fn position(&self) -> u64 {
        self.current_position
    }

    pub fn file_size(&self) -> u64 {
        self.file_size
    }

    pub fn inode(&self) -> u64 {
        self.inode
    }

    pub fn file_path(&self) -> &str {
        &self.file_path
    }
}

fn get_inode(_metadata: &std::fs::Metadata) -> u64 {
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        _metadata.ino()
    }
    #[cfg(not(unix))]
    {
        0
    }
}

fn fnv_hash(s: &str) -> u64 {
    const FNV_PRIME: u64 = 1099511628211;
    const FNV_OFFSET_BASIS: u64 = 14695981039346656037;

    let mut hash = FNV_OFFSET_BASIS;
    for byte in s.bytes() {
        hash ^= byte as u64;
        hash = hash.wrapping_mul(FNV_PRIME);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn test_file_input() -> Result<(), Box<dyn std::error::Error>> {
        let mut temp = NamedTempFile::new()?;
        writeln!(temp, "line 1")?;
        writeln!(temp, "line 2")?;
        writeln!(temp, "line 3")?;
        temp.flush()?;

        let config = FileInputConfig {
            name: "test".to_string(),
            path: temp.path().to_string_lossy().to_string(),
            sourcetype: "json".to_string(),
            index: "main".to_string(),
            recursive: false,
            follow_tail: false,
            encoding: "utf-8".to_string(),
            batch_size: 10,
            read_buffer_kb: 4,
            checkpoint_interval_ms: 5000,
            exclude: None,
        };

        let mut input = FileInput::open(config, temp.path())?;
        let events = input.read_events()?;

        assert_eq!(events.len(), 3);
        assert_eq!(input.position() > 0, true);

        Ok(())
    }

    #[test]
    fn test_fnv_hash() {
        let hash1 = fnv_hash("test");
        let hash2 = fnv_hash("test");
        assert_eq!(hash1, hash2);

        let hash3 = fnv_hash("different");
        assert_ne!(hash1, hash3);
    }
}
