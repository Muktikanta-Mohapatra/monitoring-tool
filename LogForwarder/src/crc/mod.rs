use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum CrcError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}

pub fn calculate_crc32(data: &[u8]) -> u32 {
    let mut crc = 0xffffffff;

    for &byte in data {
        crc ^= byte as u32;
        for _ in 0..8 {
            crc = if crc & 1 == 1 {
                (crc >> 1) ^ 0xedb88320
            } else {
                crc >> 1
            };
        }
    }

    crc ^ 0xffffffff
}

pub fn calculate_file_crc32(path: &Path) -> Result<u32, CrcError> {
    let mut file = File::open(path)?;
    let metadata = file.metadata()?;
    let file_size = metadata.len();

    let mut crc_head = calculate_crc32(&[]);

    let mut buffer = vec![0u8; 256];
    let read_size = file.read(&mut buffer)? as u64;
    if read_size > 0 {
        crc_head = calculate_crc32(&buffer[..read_size as usize]);
    }

    if file_size > 256 {
        file.seek(SeekFrom::End(-256))?;
        let mut tail_buffer = vec![0u8; 256];
        let tail_size = file.read(&mut tail_buffer)?;
        let crc_tail = calculate_crc32(&tail_buffer[..tail_size]);
        Ok(crc_head ^ crc_tail)
    } else {
        Ok(crc_head)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn test_calculate_crc32() {
        let data1 = b"Hello, World!";
        let crc1 = calculate_crc32(data1);
        let crc2 = calculate_crc32(data1);

        assert_eq!(crc1, crc2);
        assert_ne!(crc1, 0);
    }

    #[test]
    fn test_calculate_file_crc32() -> Result<(), Box<dyn std::error::Error>> {
        let mut temp = NamedTempFile::new()?;
        temp.write_all(b"test content")?;
        temp.flush()?;

        let crc = calculate_file_crc32(temp.path())?;
        assert_ne!(crc, 0);

        let crc2 = calculate_file_crc32(temp.path())?;
        assert_eq!(crc, crc2);

        Ok(())
    }

    #[test]
    fn test_crc_different_content() -> Result<(), Box<dyn std::error::Error>> {
        let mut temp1 = NamedTempFile::new()?;
        temp1.write_all(b"content1")?;
        temp1.flush()?;

        let mut temp2 = NamedTempFile::new()?;
        temp2.write_all(b"content2")?;
        temp2.flush()?;

        let crc1 = calculate_file_crc32(temp1.path())?;
        let crc2 = calculate_file_crc32(temp2.path())?;

        assert_ne!(crc1, crc2);

        Ok(())
    }
}
