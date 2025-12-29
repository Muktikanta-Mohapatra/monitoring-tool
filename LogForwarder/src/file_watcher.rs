use notify::{
    Event, EventHandler, RecommendedWatcher, RecursiveMode, Result as NotifyResult, Watcher,
};
use std::collections::HashMap;
use std::path::Path;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc;
use tokio::time::{sleep, Instant};

#[derive(Debug, Clone)]
pub enum FileWatchEvent {
    ConfigurationChanged(Vec<String>),
}

pub struct FileWatcher {
    watch_paths: Vec<String>,
    event_tx: mpsc::Sender<FileWatchEvent>,
    debounce_ms: u64,
}

impl FileWatcher {
    pub fn new(
        watch_paths: Vec<String>,
        event_tx: mpsc::Sender<FileWatchEvent>,
        debounce_ms: u64,
    ) -> Self {
        FileWatcher {
            watch_paths,
            event_tx,
            debounce_ms,
        }
    }

    pub async fn start(self) -> NotifyResult<()> {
        let (tx, mut rx) = mpsc::channel::<String>(100);
        let debounce_ms = self.debounce_ms;
        let event_tx = self.event_tx.clone();

        let watch_paths_clone = self.watch_paths.clone();

        tokio::spawn(async move {
            let mut pending_changes: HashMap<String, Instant> = HashMap::new();
            let mut last_debounce_trigger = Instant::now();

            loop {
                tokio::select! {
                    Some(changed_file) = rx.recv() => {
                        pending_changes.insert(changed_file, Instant::now());
                        last_debounce_trigger = Instant::now();
                    }
                    _ = sleep(Duration::from_millis(100)) => {
                        if !pending_changes.is_empty()
                            && last_debounce_trigger.elapsed()
                                >= Duration::from_millis(debounce_ms)
                        {
                            let changed_files: Vec<String> =
                                pending_changes.keys().cloned().collect();

                            tracing::info!(
                                "Configuration files changed after debounce: {:?}",
                                changed_files
                            );

                            let _ = event_tx
                                .send(FileWatchEvent::ConfigurationChanged(
                                    changed_files,
                                ))
                                .await;

                            pending_changes.clear();
                        }
                    }
                }
            }
        });

        let tx_clone = Arc::new(tokio::sync::Mutex::new(tx));

        struct ConfigChangeHandler {
            tx: Arc<tokio::sync::Mutex<mpsc::Sender<String>>>,
        }

        impl EventHandler for ConfigChangeHandler {
            fn handle_event(&mut self, res: Result<Event, notify::Error>) {
                match res {
                    Ok(event) => {
                        tracing::debug!("File watcher event: {:?}", event);
                        for path in &event.paths {
                            if let Some(path_str) = path.to_str() {
                                let tx = Arc::clone(&self.tx);
                                let path_str = path_str.to_string();
                                tokio::spawn(async move {
                                    let tx_guard = tx.lock().await;
                                    let _ = tx_guard.send(path_str).await;
                                });
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!("File watcher error: {}", e);
                    }
                }
            }
        }

        let handler = ConfigChangeHandler {
            tx: Arc::clone(&tx_clone),
        };

        let mut watcher: RecommendedWatcher = RecommendedWatcher::new(
            handler,
            notify::Config::default().with_poll_interval(Duration::from_millis(500)),
        )?;

        for watch_path in &watch_paths_clone {
            if Path::new(watch_path).exists() {
                watcher.watch(Path::new(watch_path), RecursiveMode::NonRecursive)?;
                tracing::info!("Watching configuration file: {}", watch_path);
            } else {
                tracing::warn!(
                    "Configuration file does not exist, will not watch: {}",
                    watch_path
                );
            }
        }

        std::mem::forget(watcher);
        Ok(())
    }
}
