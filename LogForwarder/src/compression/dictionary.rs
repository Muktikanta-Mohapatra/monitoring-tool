use chrono::{DateTime, Utc};
use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;

#[derive(Clone)]
pub struct DictionaryManager {
    pub dict_cache: Arc<RwLock<HashMap<String, Arc<Vec<u8>>>>>,
    pub checkpoint_dir: PathBuf,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DictionaryMetadata {
    pub sourcetype: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub version: u32,
    pub size: usize,
}

impl DictionaryManager {
    pub fn new<P: AsRef<Path>>(checkpoint_dir: P) -> Self {
        let checkpoint_dir = checkpoint_dir.as_ref().to_path_buf();

        if !checkpoint_dir.exists() {
            let _ = fs::create_dir_all(&checkpoint_dir);
        }

        DictionaryManager {
            dict_cache: Arc::new(RwLock::new(HashMap::new())),
            checkpoint_dir,
        }
    }

    pub fn get_or_create(&self, sourcetype: &str, sample_data: &[u8]) -> Arc<Vec<u8>> {
        {
            let cache = self.dict_cache.read();
            if let Some(dict) = cache.get(sourcetype) {
                return Arc::clone(dict);
            }
        }

        let dict = self.train_dictionary(sourcetype, sample_data);

        let mut cache = self.dict_cache.write();
        cache.insert(sourcetype.to_string(), Arc::clone(&dict));

        dict
    }

    pub fn load_dictionary(&self, sourcetype: &str) -> Option<Arc<Vec<u8>>> {
        {
            let cache = self.dict_cache.read();
            if let Some(dict) = cache.get(sourcetype) {
                return Some(Arc::clone(dict));
            }
        }

        let path = self.dictionary_path(sourcetype);
        if path.exists() {
            if let Ok(data) = fs::read(&path) {
                let dict = Arc::new(data);
                let mut cache = self.dict_cache.write();
                cache.insert(sourcetype.to_string(), Arc::clone(&dict));
                return Some(dict);
            }
        }

        None
    }

    pub fn train_dictionary(&self, sourcetype: &str, sample_data: &[u8]) -> Arc<Vec<u8>> {
        let dict = if sample_data.is_empty() {
            Vec::new()
        } else {
            self.build_dictionary(sample_data)
        };

        let dict_arc = Arc::new(dict);

        let path = self.dictionary_path(sourcetype);
        let _ = fs::write(&path, &*dict_arc);

        dict_arc
    }

    fn build_dictionary(&self, sample_data: &[u8]) -> Vec<u8> {
        if sample_data.len() < 1024 {
            return Vec::new();
        }

        let mut frequency = [0usize; 256];
        let mut bigrams: HashMap<[u8; 2], usize> = HashMap::new();

        for chunk in sample_data.windows(2) {
            *bigrams.entry([chunk[0], chunk[1]]).or_insert(0) += 1;
        }

        for &byte in sample_data {
            frequency[byte as usize] += 1;
        }

        let mut dict = Vec::with_capacity(65536);

        let mut bigram_list: Vec<_> = bigrams.into_iter().collect();
        bigram_list.sort_by(|a, b| b.1.cmp(&a.1));

        for (pair, _) in bigram_list.iter().take(256) {
            dict.extend_from_slice(pair);
        }

        for (byte, &count) in frequency.iter().enumerate() {
            if count > 100 && byte < 128 {
                dict.push(byte as u8);
            }
        }

        if dict.len() > 65536 {
            dict.truncate(65536);
        }

        dict
    }

    pub fn update_dictionary_if_needed(
        &self,
        sourcetype: &str,
        current_ratio: f32,
        threshold: f32,
        sample_data: &[u8],
    ) -> bool {
        if current_ratio < threshold {
            let _ = self.train_dictionary(sourcetype, sample_data);
            true
        } else {
            false
        }
    }

    fn dictionary_path(&self, sourcetype: &str) -> PathBuf {
        let safe_name = sourcetype.replace(['/', '\\'], "_");
        self.checkpoint_dir.join(format!("{}.zstd_dict", safe_name))
    }

    pub fn list_dictionaries(&self) -> Vec<String> {
        if let Ok(entries) = fs::read_dir(&self.checkpoint_dir) {
            entries
                .filter_map(|entry| {
                    entry.ok().and_then(|e| {
                        let path = e.path();
                        if path.extension().is_some_and(|ext| ext == "zstd_dict") {
                            path.file_stem()
                                .and_then(|name| name.to_str().map(|s| s.to_string()))
                        } else {
                            None
                        }
                    })
                })
                .collect()
        } else {
            Vec::new()
        }
    }

    pub fn clear_cache(&self) {
        self.dict_cache.write().clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_dictionary_training() {
        let tmpdir = TempDir::new().unwrap();
        let manager = DictionaryManager::new(tmpdir.path());

        let mut sample = Vec::new();
        for i in 0..100 {
            sample.extend_from_slice(
                format!("ERROR: Connection failed to 10.1.1.{}\n", i % 256).as_bytes(),
            );
        }

        let dict = manager.train_dictionary("test", &sample);
        assert!(!dict.is_empty());
    }

    #[test]
    fn test_dictionary_caching() {
        let tmpdir = TempDir::new().unwrap();
        let manager = DictionaryManager::new(tmpdir.path());

        let sample = b"test data";
        let dict1 = manager.get_or_create("sourcetype", sample);
        let dict2 = manager.get_or_create("sourcetype", sample);

        assert!(Arc::ptr_eq(&dict1, &dict2));
    }

    #[test]
    fn test_dictionary_persistence() {
        let tmpdir = TempDir::new().unwrap();
        let sample = b"test data";

        {
            let manager = DictionaryManager::new(tmpdir.path());
            manager.train_dictionary("test", sample);
        }

        {
            let manager = DictionaryManager::new(tmpdir.path());
            let dict = manager.load_dictionary("test");
            assert!(dict.is_some());
        }
    }

    #[test]
    fn test_list_dictionaries() {
        let tmpdir = TempDir::new().unwrap();
        let manager = DictionaryManager::new(tmpdir.path());

        manager.train_dictionary("type1", b"data1");
        manager.train_dictionary("type2", b"data2");

        let dicts = manager.list_dictionaries();
        assert!(dicts.len() >= 2);
    }
}
