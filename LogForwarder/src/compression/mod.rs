pub mod buffer_pool;
pub mod dictionary;

use buffer_pool::BufferPool;
use dictionary::DictionaryManager;
use parking_lot::RwLock;
use std::io::Write;
use std::sync::Arc;

pub use buffer_pool::{BUFFER_SIZE, POOL_SIZE};
pub use dictionary::DictionaryMetadata;

#[derive(Clone)]
pub struct CompressionConfig {
    pub level: u32,
    pub adaptive: bool,
    pub use_dictionary: bool,
    pub batch_size_bytes: usize,
}

impl Default for CompressionConfig {
    fn default() -> Self {
        CompressionConfig {
            level: 3,
            adaptive: true,
            use_dictionary: true,
            batch_size_bytes: 1024 * 1024,
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub enum CompressionLevel {
    Fast = 1,
    Default = 3,
    High = 6,
    Maximum = 9,
}

impl From<u32> for CompressionLevel {
    fn from(level: u32) -> Self {
        match level {
            1 => CompressionLevel::Fast,
            3 => CompressionLevel::Default,
            6 => CompressionLevel::High,
            9 => CompressionLevel::Maximum,
            _ => CompressionLevel::Default,
        }
    }
}

pub struct CompressionEngine {
    config: Arc<RwLock<CompressionConfig>>,
    buffer_pool: BufferPool,
    dictionary_manager: Option<DictionaryManager>,
}

impl CompressionEngine {
    pub fn new(config: CompressionConfig, checkpoint_dir: Option<&str>) -> Self {
        let dict_manager = checkpoint_dir.map(DictionaryManager::new);

        CompressionEngine {
            config: Arc::new(RwLock::new(config)),
            buffer_pool: BufferPool::new(),
            dictionary_manager: dict_manager,
        }
    }

    pub fn compress(
        &self,
        data: &[u8],
        _sourcetype: Option<&str>,
    ) -> Result<Vec<u8>, CompressionError> {
        if data.is_empty() {
            return Ok(Vec::new());
        }

        let config = self.config.read();
        let level = self.get_compression_level(&config);

        zstd::encode_all(data, level as i32).map_err(|e| CompressionError::ZstdError(e.to_string()))
    }

    pub fn decompress(&self, data: &[u8]) -> Result<Vec<u8>, CompressionError> {
        if data.is_empty() {
            return Ok(Vec::new());
        }

        zstd::decode_all(data).map_err(|e| CompressionError::ZstdError(e.to_string()))
    }

    pub fn compress_streaming(
        &self,
        data: &[u8],
        _sourcetype: Option<&str>,
    ) -> Result<Vec<u8>, CompressionError> {
        if data.is_empty() {
            return Ok(Vec::new());
        }

        let config = self.config.read();
        let level = self.get_compression_level(&config);

        let mut output = self.buffer_pool.acquire();
        let mut encoder = zstd::stream::Encoder::new(&mut output, level as i32)
            .map_err(|e| CompressionError::ZstdError(e.to_string()))?;

        encoder
            .write_all(data)
            .map_err(|e| CompressionError::IoError(e.to_string()))?;

        encoder
            .finish()
            .map_err(|e| CompressionError::ZstdError(e.to_string()))?;

        Ok(output.into_inner())
    }

    pub fn get_compression_ratio(&self, original_size: usize, compressed_size: usize) -> f32 {
        if original_size == 0 {
            1.0
        } else {
            compressed_size as f32 / original_size as f32
        }
    }

    pub fn update_config(&self, config: CompressionConfig) {
        *self.config.write() = config;
    }

    pub fn train_dictionary(
        &self,
        sourcetype: &str,
        sample_data: &[u8],
    ) -> Result<(), CompressionError> {
        if let Some(dict_mgr) = &self.dictionary_manager {
            dict_mgr.train_dictionary(sourcetype, sample_data);
            Ok(())
        } else {
            Err(CompressionError::DictionaryNotConfigured)
        }
    }

    pub fn buffer_pool(&self) -> &BufferPool {
        &self.buffer_pool
    }

    fn get_compression_level(&self, config: &CompressionConfig) -> u32 {
        if config.adaptive {
            let cpu_usage = self.estimate_cpu_usage();

            if cpu_usage > 80 {
                1
            } else if cpu_usage > 50 {
                3
            } else {
                config.level
            }
        } else {
            config.level
        }
    }

    fn estimate_cpu_usage(&self) -> u32 {
        50
    }
}

impl Clone for CompressionEngine {
    fn clone(&self) -> Self {
        CompressionEngine {
            config: Arc::clone(&self.config),
            buffer_pool: self.buffer_pool.clone(),
            dictionary_manager: self.dictionary_manager.clone(),
        }
    }
}

#[derive(Debug)]
pub enum CompressionError {
    ZstdError(String),
    IoError(String),
    DictionaryNotConfigured,
    InvalidData,
}

impl std::fmt::Display for CompressionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CompressionError::ZstdError(e) => write!(f, "Zstd error: {}", e),
            CompressionError::IoError(e) => write!(f, "IO error: {}", e),
            CompressionError::DictionaryNotConfigured => {
                write!(f, "Dictionary manager not configured")
            }
            CompressionError::InvalidData => write!(f, "Invalid data for compression"),
        }
    }
}

impl std::error::Error for CompressionError {}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_compress_decompress() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);
        let mut data = Vec::new();
        for _ in 0..1000 {
            data.extend_from_slice(b"test data to compress ");
        }

        let compressed = engine.compress(&data, None).unwrap();
        assert!(compressed.len() < data.len());

        let decompressed = engine.decompress(&compressed).unwrap();
        assert_eq!(decompressed, data);
    }

    #[test]
    fn test_empty_data() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);
        let compressed = engine.compress(b"", None).unwrap();
        assert!(compressed.is_empty());
    }

    #[test]
    fn test_streaming_compression() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);
        let data = b"streaming test data";

        let compressed = engine.compress_streaming(data, None).unwrap();
        let decompressed = engine.decompress(&compressed).unwrap();
        assert_eq!(decompressed, data);
    }

    #[test]
    fn test_compression_ratio() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);
        let ratio = engine.get_compression_ratio(1000, 500);
        assert_eq!(ratio, 0.5);
    }

    #[test]
    fn test_compression_with_sourcetype() {
        let tmpdir = TempDir::new().unwrap();
        let engine = CompressionEngine::new(
            CompressionConfig {
                use_dictionary: true,
                ..Default::default()
            },
            Some(tmpdir.path().to_str().unwrap()),
        );

        let data = b"ERROR: Connection failed";
        let compressed = engine.compress(data, Some("error_logs")).unwrap();
        let decompressed = engine.decompress(&compressed).unwrap();
        assert_eq!(decompressed, data);
    }

    #[test]
    fn test_compression_level_from_u32() {
        assert!(matches!(CompressionLevel::from(1), CompressionLevel::Fast));
        assert!(matches!(
            CompressionLevel::from(3),
            CompressionLevel::Default
        ));
        assert!(matches!(CompressionLevel::from(6), CompressionLevel::High));
        assert!(matches!(
            CompressionLevel::from(9),
            CompressionLevel::Maximum
        ));
    }

    #[test]
    fn test_config_update() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);

        let new_config = CompressionConfig {
            level: 6,
            ..Default::default()
        };
        engine.update_config(new_config);

        let config = engine.config.read();
        assert_eq!(config.level, 6);
    }

    #[test]
    fn test_large_data_compression() {
        let engine = CompressionEngine::new(CompressionConfig::default(), None);
        let data = vec![b'a'; 1024 * 1024];

        let compressed = engine.compress(&data, None).unwrap();
        assert!(compressed.len() < data.len());

        let decompressed = engine.decompress(&compressed).unwrap();
        assert_eq!(decompressed, data);
    }
}
