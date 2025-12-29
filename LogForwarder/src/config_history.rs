use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConfigAuditEntry {
    pub timestamp: DateTime<Utc>,
    pub version: u32,
    pub action: ConfigAction,
    pub user: String,
    pub source: String,
    pub status: AuditStatus,
    pub error_message: Option<String>,
    pub changes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ConfigAction {
    Reload,
    Rollback,
    Initialize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AuditStatus {
    Success,
    Failed,
    RolledBack,
}

pub struct ConfigHistory {
    history_dir: PathBuf,
    audit_log_path: PathBuf,
    current_version: Arc<Mutex<u32>>,
}

impl ConfigHistory {
    pub fn new<P: AsRef<Path>>(history_dir: P) -> std::io::Result<Self> {
        let history_dir = history_dir.as_ref().to_path_buf();

        if !history_dir.exists() {
            fs::create_dir_all(&history_dir)?;
        }

        let audit_log_path = history_dir.join("audit.log");

        let current_version = Self::get_current_version(&history_dir).unwrap_or(1);

        Ok(ConfigHistory {
            history_dir,
            audit_log_path,
            current_version: Arc::new(Mutex::new(current_version)),
        })
    }

    fn get_current_version(history_dir: &Path) -> std::io::Result<u32> {
        let mut max_version = 0u32;

        for entry in fs::read_dir(history_dir)? {
            let entry = entry?;
            let path = entry.path();

            if let Some(filename) = path.file_name().and_then(|n| n.to_str()) {
                if filename.starts_with("config_v") && filename.ends_with(".yaml") {
                    if let Some(version_str) = filename
                        .strip_prefix("config_v")
                        .and_then(|s| s.split('_').next())
                    {
                        if let Ok(version) = version_str.parse::<u32>() {
                            max_version = max_version.max(version);
                        }
                    }
                }
            }
        }

        Ok(max_version.max(1))
    }

    pub fn save_config(&self, config_content: &str) -> std::io::Result<String> {
        let mut version = self.current_version.lock().unwrap();
        *version += 1;

        let timestamp = Utc::now().format("%Y%m%d_%H%M%S");
        let filename = format!("config_v{}_{}.yaml", version, timestamp);
        let file_path = self.history_dir.join(&filename);

        fs::write(&file_path, config_content)?;

        tracing::info!("Config saved to history: {}", filename);

        Ok(filename)
    }

    pub fn get_config_version(&self, version: u32) -> std::io::Result<String> {
        for entry in fs::read_dir(&self.history_dir)? {
            let entry = entry?;
            let path = entry.path();

            if let Some(filename) = path.file_name().and_then(|n| n.to_str()) {
                if filename.starts_with(&format!("config_v{}_", version)) {
                    return fs::read_to_string(&path);
                }
            }
        }

        Err(std::io::Error::new(
            std::io::ErrorKind::NotFound,
            format!("Config version {} not found", version),
        ))
    }

    pub fn log_audit(&self, entry: ConfigAuditEntry) -> std::io::Result<()> {
        let log_line = serde_json::to_string(&entry)
            .map_err(|e| std::io::Error::new(std::io::ErrorKind::InvalidData, e.to_string()))?;

        let mut file = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.audit_log_path)?;

        use std::io::Write;
        writeln!(file, "{}", log_line)?;

        tracing::info!("Audit log entry recorded: {:?}", entry.action);

        Ok(())
    }

    pub fn get_audit_history(&self) -> std::io::Result<Vec<ConfigAuditEntry>> {
        if !self.audit_log_path.exists() {
            return Ok(Vec::new());
        }

        let content = fs::read_to_string(&self.audit_log_path)?;
        let mut entries = Vec::new();

        for line in content.lines() {
            if let Ok(entry) = serde_json::from_str::<ConfigAuditEntry>(line) {
                entries.push(entry);
            }
        }

        Ok(entries)
    }

    pub fn get_current_version_number(&self) -> u32 {
        *self.current_version.lock().unwrap()
    }
}
