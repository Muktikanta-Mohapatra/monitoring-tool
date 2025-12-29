pub mod backpressure;
pub mod batching;
pub mod checkpoint;
pub mod cli;
pub mod compression;
pub mod config;
pub mod config_diff;
pub mod config_health_checker;
pub mod config_history;
pub mod crc;
pub mod disk_monitor;
pub mod enrichment;
pub mod env_loader;
pub mod error;
pub mod event;
pub mod file_registry;
pub mod file_watcher;
pub mod graceful_shutdown;
pub mod input_batch_manager;
pub mod inputs;
pub mod logging;
pub mod masking;
pub mod metrics;
pub mod monitor;
pub mod network;
pub mod outputs;
pub mod parser;
pub mod pattern;
pub mod pipeline;
pub mod queue;
pub mod routing;
pub mod rotation;

pub use backpressure::{BackpressureMonitor, BackpressureState};
pub use batching::{AdaptiveBatcher, BatchingConfig};
pub use checkpoint::CheckpointStore;
pub use cli::CliArgs;
pub use input_batch_manager::{BatchManagerConfig, InputBatchAccumulator, ThreadSafeBatchAccumulator};
pub use compression::dictionary::DictionaryManager;
pub use compression::{CompressionConfig, CompressionEngine};
pub use config::{
    Config, ConfigManager, FileInputConfig, GracefulShutdownConfig, InputConfig, OutputConfig,
    ScriptedInputConfig, TcpInputConfig, UdpInputConfig, ValidationLevel, WindowsEventLogConfig,
};
pub use config_diff::ConfigDiff;
pub use config_health_checker::{ConfigHealthChecker, HealthCheckMetrics, HealthCheckResult};
pub use config_history::{AuditStatus, ConfigAction, ConfigAuditEntry, ConfigHistory};
pub use crc::{calculate_crc32, calculate_file_crc32};
pub use disk_monitor::{
    DiskMonitor, DiskMonitoringConfig, DiskSpace, DiskThresholdState, InputController,
};
pub use enrichment::{Enricher, EnrichmentError, EnrichmentPipeline, GeoIpEnricher, LookupTableEnricher, LruCache};
pub use error::{ForwarderError, ForwarderResult};
pub use event::{Event, EnrichedEventMetadata};
pub use file_registry::{FileMetadata, FileRegistry};
pub use file_watcher::{FileWatchEvent, FileWatcher};
pub use graceful_shutdown::{GracefulShutdown, ShutdownPhase, ShutdownSignal};
pub use inputs::plugin::{HttpPollerPlugin, InputPlugin};
pub use inputs::scripted::ScriptedInput;
pub use inputs::tcp::TcpInput;
pub use inputs::udp::UdpInput;
pub use inputs::windows_eventlog::WindowsEventLogInput;
pub use logging::StructuredLogger;
pub use masking::{MaskingEngine, MaskingError, MaskingStrategy, PiiPatterns};
pub use metrics::PipelineMetrics;
pub use monitor::metrics_exporter::MetricsExporter;
pub use monitor::{FileChangeEvent, FileEvent, FileSystemMonitor};
pub use network::{IndexerConfig, NetworkClient};
pub use parser::{CsvParser, FormatDetector, FormatStats, GrokParser, JsonParser, LogParser, LogPipeline, ParsedEvent, ParserRegistry, ParseResult, RawParser, RegexParser, SyslogFormat, SyslogParser};
pub use pattern::GlobPattern;
pub use pipeline::{PipelineConfig, ProcessedEvent, ProcessingPipeline};
pub use queue::EventQueue;
pub use rotation::{RotationDetector, RotationResult, RotationType};
pub use routing::{ConditionalRouter, RoutingRule};

pub use prometheus::TextEncoder;
