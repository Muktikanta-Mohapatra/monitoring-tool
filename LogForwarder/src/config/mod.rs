use arc_swap::ArcSwap;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum ConfigError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("YAML parse error: {0}")]
    YamlError(#[from] serde_yaml::Error),
    #[error("Invalid configuration: {0}")]
    Invalid(String),
    #[error("Validation error: {0}")]
    Validation(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ValidationLevel {
    Syntax,
    Semantic,
    Runtime,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type")]
pub enum InputConfig {
    #[serde(rename = "file")]
    File(FileInputConfig),
    #[serde(rename = "tcp")]
    Tcp(TcpInputConfig),
    #[serde(rename = "udp")]
    Udp(UdpInputConfig),
    #[serde(rename = "script")]
    Script(ScriptedInputConfig),
    #[serde(rename = "windows_event_log")]
    WindowsEventLog(WindowsEventLogConfig),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct FileInputConfig {
    pub name: String,
    pub path: String,
    pub sourcetype: String,
    pub index: String,
    pub recursive: bool,
    pub follow_tail: bool,
    pub encoding: String,
    pub batch_size: usize,
    pub read_buffer_kb: usize,
    pub checkpoint_interval_ms: u64,
    pub exclude: Option<Vec<String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct TcpInputConfig {
    pub name: String,
    pub port: u16,
    #[serde(default = "default_bind_address")]
    pub bind_address: String,
    pub sourcetype: String,
    pub index: String,
    #[serde(default = "default_max_connections")]
    pub max_connections: usize,
    #[serde(default = "default_read_buffer_kb")]
    pub read_buffer_kb: usize,
    #[serde(default = "default_connection_timeout")]
    pub connection_timeout_secs: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct UdpInputConfig {
    pub name: String,
    pub port: u16,
    #[serde(default = "default_bind_address")]
    pub bind_address: String,
    pub sourcetype: String,
    pub index: String,
    #[serde(default = "default_max_datagram_size")]
    pub max_datagram_size: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ScriptedInputConfig {
    pub name: String,
    pub command: String,
    #[serde(default)]
    pub args: Vec<String>,
    pub interval_secs: u64,
    #[serde(default = "default_timeout_secs")]
    pub timeout_secs: u64,
    pub sourcetype: String,
    pub index: String,
    #[serde(default)]
    pub environment: std::collections::HashMap<String, String>,
    #[serde(default)]
    pub resource_limits: ResourceLimits,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
pub struct ResourceLimits {
    #[serde(default)]
    pub max_memory_mb: Option<u32>,
    #[serde(default)]
    pub max_cpu_percent: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct WindowsEventLogConfig {
    pub name: String,
    pub channels: Vec<String>,
    #[serde(default)]
    pub query: Option<String>,
    pub index: String,
    #[serde(default)]
    pub sourcetype: String,
}

fn default_bind_address() -> String {
    "0.0.0.0".to_string()
}

fn default_max_connections() -> usize {
    1000
}

fn default_read_buffer_kb() -> usize {
    64
}

fn default_connection_timeout() -> u64 {
    300
}

fn default_max_datagram_size() -> usize {
    65536
}

fn default_timeout_secs() -> u64 {
    30
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct OutputConfig {
    pub name: String,
    pub url: String,
    pub token: String,
    #[serde(default)]
    pub api_key: String,
    #[serde(default = "default_forwarder_id")]
    pub forwarder_id: String,
    pub tls_verify: bool,
    pub batch_size: usize,
    pub flush_interval_ms: u64,
    pub retry_count: usize,
    #[serde(default)]
    pub compression_enabled: bool,
    #[serde(default = "default_compression_level")]
    pub compression_level: u32,
    #[serde(default = "default_compression_adaptive")]
    pub compression_adaptive: bool,
    #[serde(default)]
    pub compression_use_dict: bool,
    #[serde(default)]
    pub protocol: String,
}

fn default_forwarder_id() -> String {
    format!("forwarder-{}", uuid::Uuid::new_v4())
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct IndexerConfig {
    pub id: String,
    pub host: String,
    pub port: u16,
    #[serde(default = "default_indexer_weight")]
    pub weight: u32,
}

fn default_indexer_weight() -> u32 {
    100
}

fn default_compression_level() -> u32 {
    3
}

fn default_compression_adaptive() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GracefulShutdownConfig {
    #[serde(default = "default_shutdown_enabled")]
    pub enabled: bool,
    #[serde(default = "default_shutdown_timeout_secs")]
    pub timeout_secs: u64,
}

fn default_shutdown_enabled() -> bool {
    true
}

fn default_shutdown_timeout_secs() -> u64 {
    10
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueConfig {
    #[serde(default = "default_queue_capacity")]
    pub capacity: usize,
    #[serde(default = "default_queue_max_memory_mb")]
    pub max_memory_mb: usize,
}

fn default_queue_capacity() -> usize {
    65536
}

fn default_queue_max_memory_mb() -> usize {
    50
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatchConfig {
    #[serde(default = "default_batch_size")]
    pub size: usize,
    #[serde(default = "default_batch_max_memory_mb")]
    pub max_memory_mb: usize,
    #[serde(default = "default_batch_flush_interval_ms")]
    pub flush_interval_ms: u64,
}

fn default_batch_size() -> usize {
    1000
}

fn default_batch_max_memory_mb() -> usize {
    10
}

fn default_batch_flush_interval_ms() -> u64 {
    1000
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsConfig {
    #[serde(default = "default_metrics_bind_address")]
    pub bind_address: String,
    #[serde(default = "default_metrics_port")]
    pub port: u16,
    #[serde(default = "default_metrics_enabled")]
    pub enabled: bool,
}

fn default_metrics_bind_address() -> String {
    "0.0.0.0".to_string()
}

fn default_metrics_port() -> u16 {
    9090
}

fn default_metrics_enabled() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileWatcherConfig {
    #[serde(default = "default_file_watcher_delay_ms")]
    pub delay_ms: u64,
    #[serde(default = "default_file_watcher_enabled")]
    pub enabled: bool,
}

fn default_file_watcher_delay_ms() -> u64 {
    500
}

fn default_file_watcher_enabled() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HealthCheckConfig {
    #[serde(default = "default_health_check_interval_secs")]
    pub interval_secs: u64,
    #[serde(default = "default_health_check_threshold")]
    pub error_threshold: usize,
}

fn default_health_check_interval_secs() -> u64 {
    30
}

fn default_health_check_threshold() -> usize {
    10
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileInputRuntimeConfig {
    #[serde(default = "default_paused_sleep_ms")]
    pub paused_sleep_ms: u64,
    #[serde(default = "default_throttled_sleep_ms")]
    pub throttled_sleep_ms: u64,
    #[serde(default = "default_normal_sleep_ms")]
    pub normal_sleep_ms: u64,
}

fn default_paused_sleep_ms() -> u64 {
    500
}

fn default_throttled_sleep_ms() -> u64 {
    500
}

fn default_normal_sleep_ms() -> u64 {
    100
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemConfig {
    #[serde(default)]
    pub queue: QueueConfig,
    #[serde(default)]
    pub batch: BatchConfig,
    #[serde(default)]
    pub metrics: MetricsConfig,
    #[serde(default)]
    pub file_watcher: FileWatcherConfig,
    #[serde(default)]
    pub health_check: HealthCheckConfig,
    #[serde(default)]
    pub file_input: FileInputRuntimeConfig,
    #[serde(default = "default_config_history_path")]
    pub config_history_path: String,
    #[serde(default = "default_checkpoint_path")]
    pub checkpoint_path: String,
    #[serde(default = "default_event_tx_channel_capacity")]
    pub event_tx_channel_capacity: usize,
    #[serde(default = "default_queue_pop_sleep_ms")]
    pub queue_pop_sleep_ms: u64,
}

fn default_config_history_path() -> String {
    "var/config_history".to_string()
}

fn default_checkpoint_path() -> String {
    "var/checkpoint".to_string()
}

fn default_event_tx_channel_capacity() -> usize {
    10000
}

fn default_queue_pop_sleep_ms() -> u64 {
    10
}

impl Default for QueueConfig {
    fn default() -> Self {
        QueueConfig {
            capacity: default_queue_capacity(),
            max_memory_mb: default_queue_max_memory_mb(),
        }
    }
}

impl Default for BatchConfig {
    fn default() -> Self {
        BatchConfig {
            size: default_batch_size(),
            max_memory_mb: default_batch_max_memory_mb(),
            flush_interval_ms: default_batch_flush_interval_ms(),
        }
    }
}

impl Default for MetricsConfig {
    fn default() -> Self {
        MetricsConfig {
            bind_address: default_metrics_bind_address(),
            port: default_metrics_port(),
            enabled: default_metrics_enabled(),
        }
    }
}

impl Default for FileWatcherConfig {
    fn default() -> Self {
        FileWatcherConfig {
            delay_ms: default_file_watcher_delay_ms(),
            enabled: default_file_watcher_enabled(),
        }
    }
}

impl Default for HealthCheckConfig {
    fn default() -> Self {
        HealthCheckConfig {
            interval_secs: default_health_check_interval_secs(),
            error_threshold: default_health_check_threshold(),
        }
    }
}

impl Default for FileInputRuntimeConfig {
    fn default() -> Self {
        FileInputRuntimeConfig {
            paused_sleep_ms: default_paused_sleep_ms(),
            throttled_sleep_ms: default_throttled_sleep_ms(),
            normal_sleep_ms: default_normal_sleep_ms(),
        }
    }
}

impl Default for SystemConfig {
    fn default() -> Self {
        SystemConfig {
            queue: QueueConfig::default(),
            batch: BatchConfig::default(),
            metrics: MetricsConfig::default(),
            file_watcher: FileWatcherConfig::default(),
            health_check: HealthCheckConfig::default(),
            file_input: FileInputRuntimeConfig::default(),
            config_history_path: default_config_history_path(),
            checkpoint_path: default_checkpoint_path(),
            event_tx_channel_capacity: default_event_tx_channel_capacity(),
            queue_pop_sleep_ms: default_queue_pop_sleep_ms(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FormatDetectionConfig {
    #[serde(default = "default_format_detection_enabled")]
    pub enabled: bool,
    #[serde(default = "default_format_detection_cache_size")]
    pub cache_size: usize,
    #[serde(default = "default_format_detection_re_eval_interval")]
    pub re_evaluation_interval: u64,
    #[serde(default = "default_format_detection_confidence_threshold")]
    pub confidence_threshold: f64,
    #[serde(default)]
    pub priority_order: Vec<String>,
}

fn default_format_detection_enabled() -> bool {
    true
}

fn default_format_detection_cache_size() -> usize {
    10000
}

fn default_format_detection_re_eval_interval() -> u64 {
    10000
}

fn default_format_detection_confidence_threshold() -> f64 {
    0.8
}

impl Default for FormatDetectionConfig {
    fn default() -> Self {
        FormatDetectionConfig {
            enabled: default_format_detection_enabled(),
            cache_size: default_format_detection_cache_size(),
            re_evaluation_interval: default_format_detection_re_eval_interval(),
            confidence_threshold: default_format_detection_confidence_threshold(),
            priority_order: vec![
                "json".to_string(),
                "syslog".to_string(),
                "apache_access".to_string(),
                "windows_security".to_string(),
            ],
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ParserFallbackConfig {
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub when_unknown: String,
    #[serde(default)]
    pub sampling_rate: f64,
}

impl Default for ParserFallbackConfig {
    fn default() -> Self {
        ParserFallbackConfig {
            enabled: true,
            when_unknown: "store_as_raw".to_string(),
            sampling_rate: 0.01,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub inputs: Vec<InputConfig>,
    pub outputs: Vec<OutputConfig>,
    #[serde(default)]
    pub indexers: Vec<IndexerConfig>,
    #[serde(default)]
    pub graceful_shutdown: Option<GracefulShutdownConfig>,
    #[serde(default)]
    pub disk_monitoring: Option<serde_yaml::Value>,
    #[serde(default)]
    pub system: SystemConfig,
    #[serde(default)]
    pub format_detection: FormatDetectionConfig,
    #[serde(default)]
    pub parser_fallback: ParserFallbackConfig,
}

impl Config {
    pub fn from_yaml<P: AsRef<std::path::Path>>(path: P) -> Result<Self, ConfigError> {
        let content = std::fs::read_to_string(path)?;
        let config = serde_yaml::from_str::<Config>(&content)?;
        config.validate_all()?;
        Ok(config)
    }

    pub fn validate(&self) -> Result<(), ConfigError> {
        self.validate_level(ValidationLevel::Syntax)?;
        self.validate_level(ValidationLevel::Semantic)?;
        Ok(())
    }

    pub fn validate_all(&self) -> Result<(), ConfigError> {
        self.validate_level(ValidationLevel::Syntax)?;
        self.validate_level(ValidationLevel::Semantic)?;
        self.validate_level(ValidationLevel::Runtime)?;
        Ok(())
    }

    pub fn validate_level(&self, level: ValidationLevel) -> Result<(), ConfigError> {
        match level {
            ValidationLevel::Syntax => self.validate_syntax(),
            ValidationLevel::Semantic => self.validate_semantic(),
            ValidationLevel::Runtime => self.validate_runtime(),
        }
    }

    fn validate_syntax(&self) -> Result<(), ConfigError> {
        if self.inputs.is_empty() {
            return Err(ConfigError::Invalid("No inputs configured".to_string()));
        }
        if self.outputs.is_empty() {
            return Err(ConfigError::Invalid("No outputs configured".to_string()));
        }

        for (idx, input) in self.inputs.iter().enumerate() {
            match input {
                InputConfig::File(cfg) => {
                    if cfg.name.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: name cannot be empty",
                            idx
                        )));
                    }
                    if cfg.path.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: path cannot be empty",
                            idx
                        )));
                    }
                    if cfg.sourcetype.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: sourcetype cannot be empty",
                            idx
                        )));
                    }
                    if cfg.index.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: index cannot be empty",
                            idx
                        )));
                    }
                }
                InputConfig::Tcp(cfg) => {
                    if cfg.name.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: name cannot be empty",
                            idx
                        )));
                    }
                    if cfg.port == 0 {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: port cannot be 0",
                            idx
                        )));
                    }
                    if cfg.sourcetype.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: sourcetype cannot be empty",
                            idx
                        )));
                    }
                    if cfg.index.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: index cannot be empty",
                            idx
                        )));
                    }
                }
                InputConfig::Udp(cfg) => {
                    if cfg.name.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: name cannot be empty",
                            idx
                        )));
                    }
                    if cfg.port == 0 {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: port cannot be 0",
                            idx
                        )));
                    }
                    if cfg.sourcetype.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: sourcetype cannot be empty",
                            idx
                        )));
                    }
                    if cfg.index.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: index cannot be empty",
                            idx
                        )));
                    }
                }
                InputConfig::Script(cfg) => {
                    if cfg.name.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: name cannot be empty",
                            idx
                        )));
                    }
                    if cfg.command.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: command cannot be empty",
                            idx
                        )));
                    }
                    if cfg.sourcetype.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: sourcetype cannot be empty",
                            idx
                        )));
                    }
                    if cfg.index.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: index cannot be empty",
                            idx
                        )));
                    }
                }
                InputConfig::WindowsEventLog(cfg) => {
                    if cfg.name.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: name cannot be empty",
                            idx
                        )));
                    }
                    if cfg.channels.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: channels cannot be empty",
                            idx
                        )));
                    }
                    if cfg.index.is_empty() {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: index cannot be empty",
                            idx
                        )));
                    }
                }
            }
        }

        for (idx, output) in self.outputs.iter().enumerate() {
            if output.name.is_empty() {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: name cannot be empty",
                    idx
                )));
            }
            if output.url.is_empty() {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: URL cannot be empty",
                    idx
                )));
            }
            if output.token.is_empty() {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: token cannot be empty",
                    idx
                )));
            }
        }

        Ok(())
    }

    fn validate_semantic(&self) -> Result<(), ConfigError> {
        for (idx, input) in self.inputs.iter().enumerate() {
            match input {
                InputConfig::File(cfg) => {
                    if cfg.encoding.parse::<String>().is_err() {
                        tracing::warn!(
                            "Input[{}]: Invalid encoding '{}', using utf-8",
                            idx,
                            cfg.encoding
                        );
                    }
                }
                InputConfig::Tcp(cfg) => {
                    if cfg.port < 1024 {
                        tracing::warn!(
                            "Input[{}]: TCP port {} requires elevated privileges",
                            idx,
                            cfg.port
                        );
                    }
                }
                InputConfig::Udp(cfg) => {
                    if cfg.port < 1024 {
                        tracing::warn!(
                            "Input[{}]: UDP port {} requires elevated privileges",
                            idx,
                            cfg.port
                        );
                    }
                }
                InputConfig::Script(cfg) => {
                    if cfg.interval_secs == 0 {
                        return Err(ConfigError::Validation(format!(
                            "Input[{}]: interval_secs cannot be 0",
                            idx
                        )));
                    }
                }
                InputConfig::WindowsEventLog(_) => {}
            }
        }

        for (idx, output) in self.outputs.iter().enumerate() {
            if output.url != "console" && !output.url.starts_with("http://") && !output.url.starts_with("https://") {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: URL must start with http:// or https://",
                    idx
                )));
            }

            if output.url != "console" && output.url.parse::<std::net::SocketAddr>().is_err() && !output.url.contains("://") {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: Invalid URL format",
                    idx
                )));
            }

            if output.compression_level > 22 {
                return Err(ConfigError::Validation(format!(
                    "Output[{}]: compression_level must be 0-22, got {}",
                    idx, output.compression_level
                )));
            }
        }

        Ok(())
    }

    fn validate_runtime(&self) -> Result<(), ConfigError> {
        for (idx, input) in self.inputs.iter().enumerate() {
            if let InputConfig::File(cfg) = input {
                let path = std::path::Path::new(&cfg.path);

                if !cfg.path.contains('*') && !cfg.path.contains('?') {
                    if !path.exists() {
                        tracing::warn!("Input[{}]: Path does not exist: {}", idx, cfg.path);
                    }
                } else if let Some(parent) = path.parent() {
                    if !parent.exists() {
                        tracing::warn!(
                            "Input[{}]: Parent directory does not exist: {:?}",
                            idx,
                            parent
                        );
                    }
                }
            }
        }

        Ok(())
    }
}

pub struct ConfigManager {
    current: ArcSwap<Config>,
    previous: Arc<std::sync::Mutex<Option<Arc<Config>>>>,
}

impl ConfigManager {
    pub fn new(config: Config) -> Self {
        ConfigManager {
            current: ArcSwap::new(Arc::new(config)),
            previous: Arc::new(std::sync::Mutex::new(None)),
        }
    }

    pub fn get(&self) -> Arc<Config> {
        self.current.load_full()
    }

    pub fn reload(&self, config: Config) -> Result<(), ConfigError> {
        config.validate()?;

        let current = self.current.load_full();
        let mut prev = self.previous.lock()
            .map_err(|e| ConfigError::Invalid(format!("Failed to acquire lock on previous config: {}", e)))?;
        *prev = Some(current);

        self.current.store(Arc::new(config));
        Ok(())
    }

    pub fn reload_from_path<P: AsRef<std::path::Path>>(&self, path: P) -> Result<(), ConfigError> {
        let config = Config::from_yaml(path)?;
        self.reload(config)?;
        Ok(())
    }

    pub fn rollback(&self) -> Result<(), ConfigError> {
        let mut previous = self.previous.lock()
            .map_err(|e| ConfigError::Invalid(format!("Failed to acquire lock for rollback: {}", e)))?;

        if let Some(prev_config) = previous.take() {
            *previous = Some(self.current.load_full());
            self.current.store(prev_config);
            tracing::info!("Configuration rolled back to previous version");
            Ok(())
        } else {
            Err(ConfigError::Invalid(
                "No previous configuration available for rollback".to_string(),
            ))
        }
    }

    pub fn get_previous(&self) -> Option<Arc<Config>> {
        self.previous.lock().ok().and_then(|guard| guard.clone())
    }

    pub fn reload_validate(&self, config: Config) -> Result<(), ConfigError> {
        config.validate_all()?;
        self.reload(config)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn test_config_parse() -> Result<(), Box<dyn std::error::Error>> {
        let yaml_content = r#"
inputs:
  - type: file
    name: apache_logs
    path: /var/log/apache2/*.log
    sourcetype: apache_access
    index: web_logs
    recursive: false
    follow_tail: false
    encoding: utf-8
    batch_size: 1000
    read_buffer_kb: 256
    checkpoint_interval_ms: 5000

outputs:
  - name: splunk_hec
    url: https://splunk.example.com:8088/services/collector
    token: "Splunk token"
    tls_verify: true
    batch_size: 100
    flush_interval_ms: 1000
    retry_count: 3
"#;

        let mut temp = NamedTempFile::new()?;
        temp.write_all(yaml_content.as_bytes())?;
        temp.flush()?;

        let config = Config::from_yaml(temp.path())?;
        assert_eq!(config.inputs.len(), 1);
        assert_eq!(config.outputs.len(), 1);
        if let InputConfig::File(file_cfg) = &config.inputs[0] {
            assert_eq!(file_cfg.name, "apache_logs");
        } else {
            panic!("Expected file input");
        }
        assert_eq!(config.outputs[0].name, "splunk_hec");

        Ok(())
    }

    #[test]
    fn test_config_manager() -> Result<(), Box<dyn std::error::Error>> {
        let config = Config {
            inputs: vec![InputConfig::File(FileInputConfig {
                name: "test".to_string(),
                path: "/tmp/*.log".to_string(),
                sourcetype: "json".to_string(),
                index: "main".to_string(),
                recursive: false,
                follow_tail: false,
                encoding: "utf-8".to_string(),
                batch_size: 100,
                read_buffer_kb: 128,
                checkpoint_interval_ms: 5000,
                exclude: None,
            })],
            outputs: vec![OutputConfig {
                name: "test_output".to_string(),
                url: "http://localhost:8088".to_string(),
                token: "test".to_string(),
                api_key: "".to_string(),
                forwarder_id: "test-forwarder".to_string(),
                tls_verify: false,
                batch_size: 50,
                flush_interval_ms: 1000,
                retry_count: 3,
                compression_enabled: false,
                compression_level: 3,
                compression_adaptive: true,
                compression_use_dict: false,
                protocol: "grpc".to_string(),
            }],
            indexers: vec![],
            graceful_shutdown: None,
            disk_monitoring: None,
            system: SystemConfig::default(),
            format_detection: FormatDetectionConfig::default(),
            parser_fallback: ParserFallbackConfig::default(),
        };

        let manager = ConfigManager::new(config.clone());
        let loaded = manager.get();
        if let InputConfig::File(file_cfg) = &loaded.inputs[0] {
            assert_eq!(file_cfg.name, "test");
        } else {
            panic!("Expected file input");
        }

        Ok(())
    }
}
