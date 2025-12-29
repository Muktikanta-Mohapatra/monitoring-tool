use chrono::Utc;
use high_perf_forwarder::{
    checkpoint::CheckpointStore,
    config::{ConfigManager, InputConfig, OutputConfig},
    event::Event,
    input_batch_manager::BatchManagerConfig,
    inputs::FileInput,
    logging::StructuredLogger,
    outputs::OutputPool,
    pattern::GlobPattern,
    queue::EventQueue,
    AuditStatus, CliArgs, ConfigAction, ConfigAuditEntry, ConfigDiff, ConfigHealthChecker, ConfigHistory,
    DiskMonitor, DiskMonitoringConfig, FileWatchEvent, FileWatcher, GracefulShutdown,
    InputController, MetricsExporter, PipelineMetrics, ScriptedInput, ShutdownSignal, TcpInput,
    UdpInput, WindowsEventLogInput,
};
use std::path::Path;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc;
use tracing::Level;

pub struct Forwarder {
    config_manager: Arc<ConfigManager>,
    config_paths: Vec<String>,
    checkpoint_store: Arc<CheckpointStore>,
    event_queue: Arc<EventQueue<Event>>,
    output_pool: Arc<OutputPool>,
    _logger: Arc<StructuredLogger>,
    config_history: Arc<ConfigHistory>,
    health_checker: Arc<ConfigHealthChecker>,
    metrics: Arc<PipelineMetrics>,
    shutdown_signal: ShutdownSignal,
    disk_monitor: Option<Arc<DiskMonitor>>,
    input_controller: Arc<InputController>,
    _checkpoint_path: String,
}

fn find_matching_files(glob_pattern: &str, recursive: bool) -> Vec<String> {
    let mut matching_files = Vec::new();
    let path = Path::new(glob_pattern);

    tracing::debug!(
        "find_matching_files: pattern='{}', recursive={}",
        glob_pattern,
        recursive
    );

    if let Ok(metadata) = std::fs::metadata(path) {
        if metadata.is_file() {
            if let Some(path_str) = path.to_str() {
                tracing::debug!("Pattern matches a file directly: {}", path_str);
                return vec![path_str.to_string()];
            }
        } else if metadata.is_dir() {
            if let Ok(entries) = std::fs::read_dir(path) {
                if let Ok(pattern) = GlobPattern::new(vec!["*"], vec![]) {
                    collect_files_recursive(entries, &pattern, &mut matching_files, recursive);
                }
            }
            tracing::debug!(
                "Pattern is a directory, found {} files",
                matching_files.len()
            );
            return matching_files;
        }
    }

    if glob_pattern.contains('*') || glob_pattern.contains('?') {
        tracing::debug!("Pattern contains wildcard");
        if let Ok(pattern) = GlobPattern::new(vec![glob_pattern], vec![]) {
            if let Some(base_path) = extract_base_path(glob_pattern) {
                tracing::debug!("Base path extracted: '{}'", base_path);
                if let Ok(entries) = std::fs::read_dir(&base_path) {
                    tracing::debug!("Successfully opened directory for reading");
                    collect_files_recursive(entries, &pattern, &mut matching_files, recursive);
                    tracing::debug!("Found {} matching files", matching_files.len());
                } else {
                    tracing::debug!("Failed to open directory: '{}'", base_path);
                }
            } else {
                tracing::debug!("Failed to extract base path from pattern");
            }
        } else {
            tracing::debug!("Failed to create GlobPattern");
        }
    }

    matching_files
}

fn extract_base_path(pattern: &str) -> Option<String> {
    let normalized_pattern = pattern.replace('\\', "/");
    let parts: Vec<&str> = normalized_pattern.split('/').collect();

    let mut base_parts = Vec::new();
    for (i, part) in parts.iter().enumerate() {
        if part.contains('*') || part.contains('?') {
            tracing::debug!("Found wildcard at index {}: '{}'", i, part);
            if base_parts.is_empty() {
                return Some(".".to_string());
            }
            let base = base_parts.join(std::path::MAIN_SEPARATOR_STR);
            tracing::debug!("Extracted base path: '{}'", base);
            return Some(base);
        }
        base_parts.push(*part);
    }

    let base = base_parts.join(std::path::MAIN_SEPARATOR_STR);
    tracing::debug!(
        "No wildcard found, returning full pattern as base: '{}'",
        base
    );
    Some(base)
}

fn collect_files_recursive(
    entries: std::fs::ReadDir,
    pattern: &GlobPattern,
    matching_files: &mut Vec<String>,
    recursive: bool,
) {
    for entry in entries.flatten() {
        if let Ok(metadata) = entry.metadata() {
            let path = entry.path();
            if metadata.is_file() && pattern.matches(&path) {
                if let Some(path_str) = path.to_str() {
                    matching_files.push(path_str.to_string());
                }
            } else if metadata.is_dir() && recursive {
                if let Ok(sub_entries) = std::fs::read_dir(&path) {
                    collect_files_recursive(sub_entries, pattern, matching_files, recursive);
                }
            }
        }
    }
}

impl Forwarder {
    pub async fn new(
        config_path: &str,
        checkpoint_path: &str,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let config = high_perf_forwarder::Config::from_yaml(config_path)?;
        let config_manager = Arc::new(ConfigManager::new(config.clone()));

        let checkpoint_store = Arc::new(CheckpointStore::open(checkpoint_path, 100000)?);

        let queue_config = &config.system.queue;
        let event_queue: Arc<EventQueue<Event>> = Arc::new(EventQueue::new(
            queue_config.capacity,
            queue_config.max_memory_mb * 1024 * 1024,
        ));

        let output_pool = Arc::new(OutputPool::new(
            config.outputs.clone(),
            config.indexers.clone(),
        ));

        let logger = Arc::new(StructuredLogger::new(Level::INFO));
        logger.init();

        let health_config = &config.system.health_check;
        let config_history = Arc::new(ConfigHistory::new(&config.system.config_history_path)?);
        let health_checker = Arc::new(ConfigHealthChecker::new(
            Duration::from_secs(health_config.interval_secs),
            health_config.error_threshold as u64,
        ));
        let metrics = Arc::new(PipelineMetrics::new());

        let audit_entry = ConfigAuditEntry {
            timestamp: Utc::now(),
            version: config_history.get_current_version_number(),
            action: ConfigAction::Initialize,
            user: "system".to_string(),
            source: config_path.to_string(),
            status: AuditStatus::Success,
            error_message: None,
            changes: None,
        };
        let _ = config_history.log_audit(audit_entry);

        let shutdown_signal = ShutdownSignal::new();

        let disk_monitor = if let Some(disk_config_value) = &config.disk_monitoring {
            if let Ok(disk_config) =
                serde_yaml::from_value::<DiskMonitoringConfig>(disk_config_value.clone())
            {
                if disk_config.enabled {
                    Some(Arc::new(DiskMonitor::new(
                        std::path::PathBuf::from(checkpoint_path),
                        disk_config,
                    )))
                } else {
                    None
                }
            } else {
                None
            }
        } else {
            Some(Arc::new(DiskMonitor::new(
                std::path::PathBuf::from(checkpoint_path),
                DiskMonitoringConfig::default(),
            )))
        };

        let input_controller = Arc::new(InputController::new());

        Ok(Forwarder {
            config_manager,
            config_paths: vec![config_path.to_string()],
            checkpoint_store,
            event_queue,
            output_pool,
            _logger: logger,
            config_history,
            health_checker,
            metrics,
            shutdown_signal,
            disk_monitor,
            input_controller,
            _checkpoint_path: checkpoint_path.to_string(),
        })
    }

    pub async fn run(&self) -> Result<(), Box<dyn std::error::Error>> {
        tracing::info!("Starting high-performance forwarder with graceful shutdown support");

        let shutdown_signal = self.shutdown_signal.clone();
        let shutdown_listener = shutdown_signal.clone();

        tokio::spawn(async move {
            shutdown_listener.wait_for_signal().await;
        });

        let (watch_tx, mut watch_rx) = mpsc::channel::<FileWatchEvent>(100);

        let file_watcher_config = &self.config_manager.get().system.file_watcher;
        let file_watcher = FileWatcher::new(
            self.config_paths.clone(),
            watch_tx,
            file_watcher_config.delay_ms,
        );

        let _watcher_handle = if file_watcher_config.enabled {
            tokio::spawn(async move {
                if let Err(e) = file_watcher.start().await {
                    tracing::error!("File watcher error: {}", e);
                }
            })
        } else {
            tokio::spawn(async {
                tracing::info!("File watcher disabled via configuration");
            })
        };

        let config = self.config_manager.get();
        tracing::info!(
            input_count = config.inputs.len(),
            output_count = config.outputs.len(),
            "Configuration loaded"
        );

        let metrics_config = &config.system.metrics;
        if metrics_config.enabled {
            let metrics_exporter = MetricsExporter::new(
                self.metrics.get_registry(),
                &metrics_config.bind_address,
                metrics_config.port,
            );
            let _exporter_handle = metrics_exporter.start();
            tracing::info!(
                "Prometheus metrics exporter started on {}:{}",
                metrics_config.bind_address,
                metrics_config.port
            );
        } else {
            tracing::info!("Prometheus metrics exporter disabled via configuration");
        }

        if let Some(disk_monitor) = &self.disk_monitor {
            let disk_monitor_clone = Arc::clone(disk_monitor);
            let input_controller = Arc::clone(&self.input_controller);
            tokio::spawn(async move {
                disk_monitor_clone
                    .monitor_loop((*input_controller).clone())
                    .await;
            });
            tracing::info!("Disk space monitoring started");
        }

        let _current_input_tasks = self.spawn_input_tasks(&config).await;

        let queue_clone = Arc::clone(&self.event_queue);
        let output_pool = Arc::clone(&self.output_pool);

        let batch_config = self.config_manager.get().system.batch.clone();
        let queue_pop_sleep_ms = self.config_manager.get().system.queue_pop_sleep_ms;

        let format_detection_config = self.config_manager.get().format_detection.clone();

        let mut output_task = tokio::spawn(async move {
            let pipeline = if format_detection_config.enabled {
                let mut parsers: Vec<Arc<dyn high_perf_forwarder::parser::LogParser>> = vec![
                    Arc::new(high_perf_forwarder::parser::JsonParser::new("json".to_string(), None)),
                ];

                if let Ok(syslog_parser) = high_perf_forwarder::parser::SyslogParser::new(
                    "syslog".to_string(),
                    high_perf_forwarder::parser::SyslogFormat::RFC5424,
                ) {
                    parsers.push(Arc::new(syslog_parser));
                }

                parsers.push(Arc::new(high_perf_forwarder::parser::CsvParser::new(
                    "csv".to_string(),
                    vec![],
                    ',',
                    None,
                )));

                if let Ok(grok_parser) = high_perf_forwarder::parser::GrokParser::new(
                    "grok".to_string(),
                    "%{COMBINEDAPACHELOG}".to_string(),
                    None,
                ) {
                    parsers.push(Arc::new(grok_parser));
                }

                if let Ok(regex_parser) = high_perf_forwarder::parser::RegexParser::new(
                    "regex".to_string(),
                    r"(?P<message>.*)".to_string(),
                    vec!["message".to_string()],
                    None,
                ) {
                    parsers.push(Arc::new(regex_parser));
                }

                parsers.push(Arc::new(high_perf_forwarder::parser::RawParser::new("raw".to_string())));

                Some(Arc::new(high_perf_forwarder::parser::LogPipeline::new(
                    Arc::new(high_perf_forwarder::parser::FormatDetector::new(
                        parsers,
                        format_detection_config.cache_size,
                        format_detection_config.confidence_threshold,
                        format_detection_config.re_evaluation_interval,
                    )),
                    Arc::new(high_perf_forwarder::enrichment::EnrichmentPipeline::new()),
                    Arc::new(high_perf_forwarder::masking::MaskingEngine::new()),
                    Arc::new(high_perf_forwarder::routing::ConditionalRouter::new("default".to_string())),
                )))
            } else {
                None
            };

            let mut batch_buffer = high_perf_forwarder::queue::BatchBuffer::new(
                batch_config.size,
                batch_config.max_memory_mb * 1024 * 1024,
            );
            let mut ticker = tokio::time::interval(tokio::time::Duration::from_millis(
                batch_config.flush_interval_ms,
            ));

            loop {
                tokio::select! {
                    _ = ticker.tick() => {
                        if !batch_buffer.is_empty() {
                            let events = batch_buffer.drain();
                            let batch = high_perf_forwarder::outputs::EventBatch::new(events);

                            match output_pool.send_to_all(batch).await {
                                Ok(_) => tracing::debug!("Batch sent successfully"),
                                Err(e) => tracing::error!("Failed to send batch: {}", e),
                            }
                        }
                    }
                    _ = async {
                        loop {
                            if let Some(mut event) = queue_clone.pop() {
                                if let Some(ref pipe) = pipeline {
                                    let line = String::from_utf8_lossy(event.raw_data()).to_string();
                                    match pipe.process(&line).await {
                                        Ok(enriched) => {
                                            let metadata = high_perf_forwarder::event::EnrichedEventMetadata {
                                                parsed_timestamp: enriched.parsed.timestamp,
                                                detected_format: enriched.parsed.parser_id.clone(),
                                                parse_duration_us: enriched.parsed.parse_duration_us,
                                                parsed_fields: enriched.parsed.fields.clone(),
                                                enriched_fields: enriched.fields,
                                            };
                                            event = event.with_enriched_metadata(metadata);
                                        }
                                        Err(e) => {
                                            tracing::debug!("Format detection error: {}", e);
                                        }
                                    }
                                }
                                batch_buffer.push(event);
                                if batch_buffer.is_full() {
                                    break;
                                }
                            } else {
                                tokio::time::sleep(tokio::time::Duration::from_millis(queue_pop_sleep_ms)).await;
                                break;
                            }
                        }
                    } => {}
                }
            }
        });

        loop {
            tokio::select! {
                Some(FileWatchEvent::ConfigurationChanged(changed_files)) = watch_rx.recv() => {
                    tracing::info!("Configuration files changed: {:?}", changed_files);
                    if !self.shutdown_signal.is_shutdown() {
                        self.handle_config_reload().await;
                    }
                }

                _ = &mut output_task => {
                    tracing::info!("Output task completed");
                    break;
                }

                _ = async {
                    loop {
                        if self.shutdown_signal.is_shutdown() {
                            break;
                        }
                        tokio::time::sleep(Duration::from_millis(100)).await;
                    }
                } => {
                    tracing::info!("Shutdown signal received, initiating graceful shutdown");
                    break;
                }
            }
        }

        let shutdown_timeout_secs = self
            .config_manager
            .get()
            .graceful_shutdown
            .as_ref()
            .map(|cfg| cfg.timeout_secs)
            .unwrap_or(10);

        let graceful_shutdown =
            GracefulShutdown::new(self.shutdown_signal.clone(), shutdown_timeout_secs);

        let checkpoint_store = Arc::clone(&self.checkpoint_store);
        let queue = Arc::clone(&self.event_queue);

        let result = graceful_shutdown
            .execute(
                async {
                    tracing::debug!("Stopping file inputs");
                    Ok(())
                },
                async {
                    let mut drained = 0u64;
                    let timeout = Duration::from_secs(3);
                    let start = std::time::Instant::now();
                    loop {
                        if let Some(_event) = queue.pop() {
                            drained += 1;
                        } else if start.elapsed() >= timeout {
                            break;
                        } else {
                            tokio::time::sleep(Duration::from_millis(10)).await;
                        }
                    }
                    Ok(drained)
                },
                async {
                    if let Err(e) = checkpoint_store.flush() {
                        return Err(format!("Failed to flush checkpoint store: {}", e));
                    }
                    Ok(())
                },
                async {
                    tracing::debug!("Closing output connections");
                    Ok(())
                },
            )
            .await;

        match result {
            Ok(_) => {
                tracing::info!("Graceful shutdown completed successfully");
                Ok(())
            }
            Err(e) => {
                tracing::error!("Graceful shutdown error: {}", e);
                Ok(())
            }
        }
    }

    async fn spawn_input_tasks(
        &self,
        config: &Arc<high_perf_forwarder::Config>,
    ) -> Vec<tokio::task::JoinHandle<()>> {
        let mut input_tasks = vec![];
        let event_tx_capacity = config.system.event_tx_channel_capacity;
        let (event_tx, mut event_rx) = mpsc::channel::<Event>(event_tx_capacity);

        let queue = Arc::clone(&self.event_queue);
        let health_checker = Arc::clone(&self.health_checker);
        let input_controller = Arc::clone(&self.input_controller);

        tokio::spawn(async move {
            while let Some(event) = event_rx.recv().await {
                if !input_controller.is_paused() {
                    let _ = queue.push(event);
                    health_checker.get_metrics().record_event();
                }
            }
        });

        for (idx, input_config) in config.inputs.iter().enumerate() {
            let index_id = idx as u16;
            let input_name = match input_config {
                InputConfig::File(cfg) => cfg.name.clone(),
                InputConfig::Tcp(cfg) => cfg.name.clone(),
                InputConfig::Udp(cfg) => cfg.name.clone(),
                InputConfig::Script(cfg) => cfg.name.clone(),
                InputConfig::WindowsEventLog(cfg) => cfg.name.clone(),
            };

            match input_config {
                InputConfig::File(file_cfg) => {
                    let matching_files = find_matching_files(&file_cfg.path, file_cfg.recursive);

                    if matching_files.is_empty() {
                        tracing::warn!("No files found matching pattern: {}", file_cfg.path);
                        continue;
                    }

                    for file_path in matching_files {
                        let queue = Arc::clone(&self.event_queue);
                        let file_cfg = file_cfg.clone();
                        let health_metrics = self.health_checker.get_metrics();
                        let pipeline_metrics = Arc::clone(&self.metrics);
                        let checkpoint_store = Arc::clone(&self.checkpoint_store);
                        let input_controller = Arc::clone(&self.input_controller);

                        let file_input_config = config.system.file_input.clone();
                        let input_task = tokio::spawn(async move {
                            let source_hash = fnv_hash(&file_cfg.name);

                            let checkpoint_position =
                                checkpoint_store.get(source_hash).ok().and_then(|record| {
                                    if record.inode == 0 || record.position < record.file_size {
                                        Some(record.position)
                                    } else {
                                        None
                                    }
                                });

                            if let Ok(mut file_input) = FileInput::open_from_position(
                                file_cfg.clone(),
                                &file_path,
                                checkpoint_position,
                            ) {
                                let batch_config = BatchManagerConfig::new(
                                    file_cfg.batch_size,
                                    5000,
                                );
                                let mut batch_accumulator = high_perf_forwarder::InputBatchAccumulator::new(batch_config);

                                loop {
                                    if input_controller.is_paused() {
                                        if let Some(pending_batch) = batch_accumulator.flush() {
                                            let batch_count = pending_batch.len();
                                            for event in pending_batch {
                                                let _ = queue.push(event);
                                                health_metrics.record_event();
                                            }
                                            tracing::info!(
                                                "Partial batch flushed due to pause: {} events from '{}' ({})",
                                                batch_count,
                                                file_path,
                                                file_cfg.name
                                            );
                                        }

                                        tokio::time::sleep(tokio::time::Duration::from_millis(
                                            file_input_config.paused_sleep_ms,
                                        ))
                                        .await;
                                        continue;
                                    }

                                    match file_input.read_events() {
                                        Ok(events) => {
                                            let has_events = !events.is_empty();
                                            let events_count = events.len();
                                            let mut total_bytes = 0u64;

                                            for event in &events {
                                                total_bytes += event.raw_data().len() as u64;
                                            }

                                            tracing::debug!(
                                                "Adding {} events to accumulator (current size: {})",
                                                events_count,
                                                batch_accumulator.current_batch_size()
                                            );

                                            match batch_accumulator.add_events(events) {
                                                Some(ready_batch) => {
                                                    let batch_size = ready_batch.len();
                                                    for event in ready_batch {
                                                        let _ = queue.push(event);
                                                        health_metrics.record_event();
                                                    }

                                                    tracing::info!(
                                                        "Batch ready and pushed to queue: {} events from '{}' ({}) | {} bytes",
                                                        batch_size,
                                                        file_path,
                                                        file_cfg.name,
                                                        total_bytes
                                                    );

                                                    pipeline_metrics.record_events_read(
                                                        batch_accumulator.get_batch_size() as u64,
                                                        "file",
                                                        &file_cfg.sourcetype,
                                                    );
                                                    pipeline_metrics
                                                        .record_bytes_read(total_bytes, "file");

                                                let file_metadata =
                                                    std::fs::metadata(&file_path).ok();
                                                let current_inode = file_metadata
                                                    .as_ref()
                                                    .map(get_inode_from_metadata)
                                                    .unwrap_or(0);

                                                let _ = checkpoint_store.put(
                                                    source_hash,
                                                    current_inode,
                                                    file_input.position(),
                                                    file_input.file_size(),
                                                    0,
                                                    file_metadata
                                                        .and_then(|m| m.modified().ok())
                                                        .and_then(|t| {
                                                            t.duration_since(std::time::UNIX_EPOCH)
                                                                .ok()
                                                        })
                                                        .map(|d| d.as_secs() as i64)
                                                        .unwrap_or(0),
                                                );

                                                pipeline_metrics.record_checkpoint_saved();
                                                }
                                                None => {
                                                    tracing::debug!(
                                                        "No batch ready yet - accumulated {} / {} events",
                                                        batch_accumulator.current_batch_size(),
                                                        batch_accumulator.get_batch_size()
                                                    );
                                                }
                                            }

                                            if !has_events && batch_accumulator.has_events() {
                                                if let Some(pending_batch) = batch_accumulator.flush() {
                                                    let batch_count = pending_batch.len();
                                                    for event in pending_batch {
                                                        let _ = queue.push(event);
                                                        health_metrics.record_event();
                                                    }

                                                    tracing::info!(
                                                        "Partial batch flushed (no new events): {} events from '{}' ({})",
                                                        batch_count,
                                                        file_path,
                                                        file_cfg.name
                                                    );

                                                    let file_metadata =
                                                        std::fs::metadata(&file_path).ok();
                                                    let current_inode = file_metadata
                                                        .as_ref()
                                                        .map(get_inode_from_metadata)
                                                        .unwrap_or(0);

                                                    let _ = checkpoint_store.put(
                                                        source_hash,
                                                        current_inode,
                                                        file_input.position(),
                                                        file_input.file_size(),
                                                        0,
                                                        file_metadata
                                                            .and_then(|m| m.modified().ok())
                                                            .and_then(|t| {
                                                                t.duration_since(std::time::UNIX_EPOCH)
                                                                    .ok()
                                                            })
                                                            .map(|d| d.as_secs() as i64)
                                                            .unwrap_or(0),
                                                    );

                                                    pipeline_metrics.record_checkpoint_saved();
                                                }
                                            }
                                        }
                                        Err(e) => {
                                            tracing::error!(
                                                "Error reading events from {}: {}",
                                                file_path,
                                                e
                                            );
                                            health_metrics.record_error();
                                            pipeline_metrics
                                                .record_error("file_reader", "read_error");

                                            if let Some(pending_batch) = batch_accumulator.flush() {
                                                let batch_count = pending_batch.len();
                                                for event in pending_batch {
                                                    let _ = queue.push(event);
                                                    health_metrics.record_event();
                                                }
                                                tracing::info!(
                                                    "Partial batch flushed due to error: {} events from '{}' ({})",
                                                    batch_count,
                                                    file_path,
                                                    file_cfg.name
                                                );
                                            }

                                            break;
                                        }
                                    }

                                    let sleep_duration = if input_controller.is_throttled() {
                                        tokio::time::Duration::from_millis(
                                            file_input_config.throttled_sleep_ms,
                                        )
                                    } else {
                                        tokio::time::Duration::from_millis(
                                            file_input_config.normal_sleep_ms,
                                        )
                                    };
                                    tokio::time::sleep(sleep_duration).await;
                                }

                                if let Some(pending_batch) = batch_accumulator.flush() {
                                    let batch_count = pending_batch.len();
                                    for event in pending_batch {
                                        let _ = queue.push(event);
                                        health_metrics.record_event();
                                    }
                                    tracing::info!(
                                        "Final partial batch flushed at loop exit: {} events from '{}' ({})",
                                        batch_count,
                                        file_path,
                                        file_cfg.name
                                    );
                                }
                            } else {
                                tracing::error!("Failed to open file: {}", file_path);
                                pipeline_metrics.record_error("file_reader", "open_failed");
                            }
                        });

                        input_tasks.push(input_task);
                    }
                }
                InputConfig::Tcp(tcp_cfg) => {
                    let tcp_input = TcpInput::new(tcp_cfg.clone(), index_id);
                    let event_tx = event_tx.clone();

                    let input_task = tokio::spawn(async move {
                        if let Err(e) = tcp_input.start(event_tx).await {
                            tracing::error!("TCP input {} failed: {}", input_name, e);
                        }
                    });

                    input_tasks.push(input_task);
                }
                InputConfig::Udp(udp_cfg) => {
                    let udp_input = UdpInput::new(udp_cfg.clone(), index_id);
                    let event_tx = event_tx.clone();

                    let input_task = tokio::spawn(async move {
                        if let Err(e) = udp_input.start(event_tx).await {
                            tracing::error!("UDP input {} failed: {}", input_name, e);
                        }
                    });

                    input_tasks.push(input_task);
                }
                InputConfig::Script(script_cfg) => {
                    let script_input = ScriptedInput::new(script_cfg.clone(), index_id);
                    let event_tx = event_tx.clone();

                    let input_task = tokio::spawn(async move {
                        if let Err(e) = script_input.start(event_tx).await {
                            tracing::error!("Scripted input {} failed: {}", input_name, e);
                        }
                    });

                    input_tasks.push(input_task);
                }
                InputConfig::WindowsEventLog(winevent_cfg) => {
                    let winevent_input = WindowsEventLogInput::new(winevent_cfg.clone(), index_id);
                    let event_tx = event_tx.clone();

                    let input_task = tokio::spawn(async move {
                        if let Err(e) = winevent_input.start(event_tx).await {
                            tracing::error!("Windows Event Log input {} failed: {}", input_name, e);
                        }
                    });

                    input_tasks.push(input_task);
                }
            }
        }

        input_tasks
    }

    async fn handle_config_reload(&self) {
        tracing::info!("Attempting configuration reload");

        for config_path in &self.config_paths {
            match high_perf_forwarder::Config::from_yaml(config_path) {
                Ok(new_config) => {
                    let old_config = self.config_manager.get();
                    let diff = ConfigDiff::compute(&old_config, &new_config);

                    diff.log_summary();

                    self.health_checker.reset();

                    match self.config_manager.reload(new_config.clone()) {
                        Ok(_) => {
                            let metrics = self.health_checker.get_metrics();
                            metrics.mark_config_applied();

                            let health_result = self.health_checker.run_health_check().await;

                            let audit_entry = ConfigAuditEntry {
                                timestamp: Utc::now(),
                                version: self.config_history.get_current_version_number(),
                                action: ConfigAction::Reload,
                                user: "system".to_string(),
                                source: config_path.clone(),
                                status: if health_result.healthy {
                                    AuditStatus::Success
                                } else {
                                    AuditStatus::Failed
                                },
                                error_message: if health_result.healthy {
                                    None
                                } else {
                                    Some(health_result.reason.clone())
                                },
                                changes: Some(format!(
                                    "+{} inputs, -{} inputs, +{} outputs, -{} outputs",
                                    diff.added_inputs.len(),
                                    diff.removed_inputs.len(),
                                    diff.added_outputs.len(),
                                    diff.removed_outputs.len()
                                )),
                            };
                            let _ = self.config_history.log_audit(audit_entry);

                            if !health_result.healthy {
                                tracing::error!("Health check failed: {}", health_result.reason);
                                if let Err(e) = self.config_manager.rollback() {
                                    tracing::error!("Rollback failed: {}", e);
                                } else {
                                    let rollback_entry = ConfigAuditEntry {
                                        timestamp: Utc::now(),
                                        version: self.config_history.get_current_version_number(),
                                        action: ConfigAction::Rollback,
                                        user: "system".to_string(),
                                        source: config_path.clone(),
                                        status: AuditStatus::RolledBack,
                                        error_message: Some(health_result.reason.clone()),
                                        changes: None,
                                    };
                                    let _ = self.config_history.log_audit(rollback_entry);
                                }
                            }
                        }
                        Err(e) => {
                            tracing::error!("Failed to reload configuration: {}", e);
                            let audit_entry = ConfigAuditEntry {
                                timestamp: Utc::now(),
                                version: self.config_history.get_current_version_number(),
                                action: ConfigAction::Reload,
                                user: "system".to_string(),
                                source: config_path.clone(),
                                status: AuditStatus::Failed,
                                error_message: Some(e.to_string()),
                                changes: None,
                            };
                            let _ = self.config_history.log_audit(audit_entry);
                        }
                    }
                }
                Err(e) => {
                    tracing::error!("Failed to parse configuration from {}: {}", config_path, e);
                }
            }
        }
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

fn get_inode_from_metadata(_metadata: &std::fs::Metadata) -> u64 {
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

/// Create a default configuration from CLI arguments in simple mode
fn create_config_from_cli(cli_args: &CliArgs) -> high_perf_forwarder::Config {
    use high_perf_forwarder::config::{SystemConfig, QueueConfig, BatchConfig, FileWatcherConfig, HealthCheckConfig, FileInputConfig, FileInputRuntimeConfig, MetricsConfig, FormatDetectionConfig, ParserFallbackConfig};

    // Load API key from CLI or environment
    let api_key = cli_args.api_key.clone()
        .or_else(|| std::env::var("FORWARDER_API_KEY").ok())
        .unwrap_or_default();

    let forwarder_id = cli_args.forwarder_id.clone()
        .or_else(|| std::env::var("FORWARDER_ID").ok())
        .unwrap_or_else(|| format!("forwarder-{}", uuid::Uuid::new_v4()));

    let middleware_url = cli_args.middleware_url.clone()
        .or_else(|| std::env::var("MIDDLEWARE_URL").ok())
        .unwrap_or_else(|| "http://localhost:8080/api/v1/events/batch".to_string());

    let log_path = cli_args.log_path.as_ref()
        .expect("Log path is required in simple mode");

    // Create file input configuration
    let file_input = FileInputConfig {
        name: "cli_logs".to_string(),
        path: log_path.clone(),
        sourcetype: "auto".to_string(),
        index: "main".to_string(),
        recursive: true,
        follow_tail: false,
        encoding: "utf-8".to_string(),
        batch_size: 100,
        read_buffer_kb: 128,
        checkpoint_interval_ms: 5000,
        exclude: None,
    };

    // Create HTTP output configuration
    let http_output = OutputConfig {
        name: "http_output".to_string(),
        url: middleware_url,
        token: api_key.clone(),
        api_key,
        forwarder_id,
        tls_verify: false,
        batch_size: 50,
        flush_interval_ms: 1000,
        retry_count: 3,
        compression_enabled: false,
        compression_level: 3,
        compression_adaptive: true,
        compression_use_dict: false,
        protocol: "http".to_string(),
    };

    // Create default system config
    let system = SystemConfig {
        queue: QueueConfig {
            capacity: 65536,
            max_memory_mb: 50,
        },
        batch: BatchConfig {
            size: 1000,
            max_memory_mb: 10,
            flush_interval_ms: 1000,
        },
        file_watcher: FileWatcherConfig {
            delay_ms: 500,
            enabled: false, // Disable file watcher in simple CLI mode
        },
        health_check: HealthCheckConfig {
            interval_secs: 30,
            error_threshold: 10,
        },
        file_input: FileInputRuntimeConfig {
            paused_sleep_ms: 500,
            throttled_sleep_ms: 500,
            normal_sleep_ms: 100,
        },
        checkpoint_path: "var/checkpoint".to_string(),
        config_history_path: "var/config_history".to_string(),
        event_tx_channel_capacity: 10000,
        queue_pop_sleep_ms: 10,
        metrics: MetricsConfig {
            bind_address: "0.0.0.0".to_string(),
            port: 9090,
            enabled: true,
        },
    };

    high_perf_forwarder::Config {
        system,
        inputs: vec![InputConfig::File(file_input)],
        outputs: vec![http_output],
        indexers: vec![],
        disk_monitoring: None,
        graceful_shutdown: None,
        format_detection: FormatDetectionConfig {
            enabled: false,
            cache_size: 1000,
            confidence_threshold: 0.8,
            re_evaluation_interval: 100,
            priority_order: vec![],
        },
        parser_fallback: ParserFallbackConfig::default(),
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Parse CLI arguments
    let cli_args = CliArgs::parse();

    // Handle help flag
    if cli_args.help {
        CliArgs::print_help();
        return Ok(());
    }

    // Validate CLI arguments
    if let Err(e) = cli_args.validate() {
        eprintln!("Error: {}", e);
        eprintln!("\nRun with --help for usage information");
        std::process::exit(1);
    }

    // Load environment variables if .env file exists
    if let Err(e) = high_perf_forwarder::env_loader::load_env_config() {
        tracing::warn!("Could not load .env file: {}", e);
    }

    // Determine config path and mode
    let (config_path, is_temp_config) = if cli_args.is_simple_mode() {
        // Simple CLI mode: create config from arguments
        tracing::info!("Running in simple CLI mode");
        let config = create_config_from_cli(&cli_args);

        // Write temporary config file
        let temp_config_path = "var/temp_config.yaml";
        std::fs::create_dir_all("var")?;
        let config_yaml = serde_yaml::to_string(&config)?;
        std::fs::write(temp_config_path, config_yaml)?;

        (temp_config_path.to_string(), true)
    } else if let Some(ref config_file) = cli_args.config_file {
        // Config file mode
        (config_file.clone(), false)
    } else {
        // Default config mode
        let default_config = std::env::var("FORWARDER_CONFIG")
            .unwrap_or_else(|_| "config/inputs.yaml".to_string());
        (default_config, false)
    };

    // Load configuration
    let mut config = high_perf_forwarder::Config::from_yaml(&config_path)?;

    // Apply environment variable overrides
    config.outputs = high_perf_forwarder::env_loader::apply_env_overrides(config.outputs);

    let checkpoint_path_str = std::env::var("FORWARDER_CHECKPOINT")
        .unwrap_or_else(|_| config.system.checkpoint_path.clone());

    let checkpoint_path = if std::path::Path::new(&checkpoint_path_str).is_absolute() {
        checkpoint_path_str.clone()
    } else {
        let current_dir = std::env::current_dir()?;
        current_dir
            .join(&checkpoint_path_str)
            .to_string_lossy()
            .to_string()
    };

    let checkpoint_file_path = if checkpoint_path_str.ends_with(".checkpoint") {
        checkpoint_path
    } else {
        format!("{}/forwarder.checkpoint", checkpoint_path)
    };

    std::fs::create_dir_all(
        std::path::Path::new(&checkpoint_file_path)
            .parent()
            .unwrap_or_else(|| std::path::Path::new(".")),
    )?;

    let forwarder = Forwarder::new(&config_path, &checkpoint_file_path).await?;
    let result = forwarder.run().await;

    // Clean up temporary config if created
    if is_temp_config {
        let _ = std::fs::remove_file(&config_path);
    }

    result
}
