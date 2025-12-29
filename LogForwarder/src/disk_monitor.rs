use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::time::Instant;
use sysinfo::Disks;
use tokio::sync::RwLock;
use tracing::{debug, error, info, warn};

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum DiskThresholdState {
    OK,
    Warning,
    Critical,
    Emergency,
}

#[derive(Debug, Clone)]
pub struct DiskSpace {
    pub total_bytes: u64,
    pub available_bytes: u64,
    pub used_percent: f64,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct DiskMonitoringConfig {
    pub enabled: bool,
    pub check_interval_secs: u64,
    pub warning_threshold_percent: f64,
    pub critical_threshold_percent: f64,
    pub emergency_threshold_percent: f64,
}

impl Default for DiskMonitoringConfig {
    fn default() -> Self {
        DiskMonitoringConfig {
            enabled: true,
            check_interval_secs: 60,
            warning_threshold_percent: 90.0,
            critical_threshold_percent: 95.0,
            emergency_threshold_percent: 98.0,
        }
    }
}

#[derive(Clone)]
pub struct InputController {
    paused: Arc<AtomicBool>,
    throttled: Arc<AtomicBool>,
}

impl Default for InputController {
    fn default() -> Self {
        Self::new()
    }
}

impl InputController {
    pub fn new() -> Self {
        InputController {
            paused: Arc::new(AtomicBool::new(false)),
            throttled: Arc::new(AtomicBool::new(false)),
        }
    }

    pub async fn pause_all(&self) {
        self.paused.store(true, Ordering::Release);
        info!("All inputs paused due to emergency disk condition");
    }

    pub async fn resume_all(&self) {
        self.paused.store(false, Ordering::Release);
        info!("All inputs resumed");
    }

    pub async fn throttle(&self) {
        self.throttled.store(true, Ordering::Release);
        debug!("Inputs throttled due to critical disk condition");
    }

    pub async fn unthrottle(&self) {
        self.throttled.store(false, Ordering::Release);
        debug!("Inputs unthrottled");
    }

    pub fn is_paused(&self) -> bool {
        self.paused.load(Ordering::Acquire)
    }

    pub fn is_throttled(&self) -> bool {
        self.throttled.load(Ordering::Acquire)
    }
}

pub struct DiskMonitor {
    checkpoint_path: PathBuf,
    config: DiskMonitoringConfig,
    current_state: Arc<RwLock<DiskThresholdState>>,
    disks: Arc<RwLock<Disks>>,
    last_refresh: Arc<RwLock<Instant>>,
}

impl DiskMonitor {
    pub fn new(checkpoint_path: PathBuf, config: DiskMonitoringConfig) -> Self {
        DiskMonitor {
            checkpoint_path,
            config,
            current_state: Arc::new(RwLock::new(DiskThresholdState::OK)),
            disks: Arc::new(RwLock::new(Disks::new_with_refreshed_list())),
            last_refresh: Arc::new(RwLock::new(Instant::now())),
        }
    }

    pub async fn get_current_state(&self) -> DiskThresholdState {
        *self.current_state.read().await
    }

    pub async fn check_space(&self) -> Result<DiskSpace, String> {
        let mut disks = self.disks.write().await;
        let mut last_refresh = self.last_refresh.write().await;

        if last_refresh.elapsed() > Duration::from_secs(5) {
            disks.refresh_list();
            *last_refresh = Instant::now();
        }
        drop(last_refresh);

        for disk in disks.iter() {
            if self.checkpoint_path.starts_with(disk.mount_point()) {
                let total = disk.total_space();
                let available = disk.available_space();
                let used_pct = if total > 0 {
                    (1.0 - (available as f64 / total as f64)) * 100.0
                } else {
                    0.0
                };

                debug!(
                    "Disk check: total={} bytes, available={} bytes, used={}%",
                    total, available, used_pct
                );

                return Ok(DiskSpace {
                    total_bytes: total,
                    available_bytes: available,
                    used_percent: used_pct,
                });
            }
        }

        Err(format!(
            "Disk not found for checkpoint path: {:?}",
            self.checkpoint_path
        ))
    }

    pub fn determine_state(&self, used_percent: f64) -> DiskThresholdState {
        if used_percent >= self.config.emergency_threshold_percent {
            DiskThresholdState::Emergency
        } else if used_percent >= self.config.critical_threshold_percent {
            DiskThresholdState::Critical
        } else if used_percent >= self.config.warning_threshold_percent {
            DiskThresholdState::Warning
        } else {
            DiskThresholdState::OK
        }
    }

    pub async fn monitor_loop(&self, input_controller: InputController) {
        if !self.config.enabled {
            debug!("Disk monitoring is disabled");
            return;
        }

        let mut interval =
            tokio::time::interval(Duration::from_secs(self.config.check_interval_secs));

        loop {
            interval.tick().await;

            match self.check_space().await {
                Ok(space) => {
                    let new_state = self.determine_state(space.used_percent);
                    let old_state = *self.current_state.read().await;

                    if new_state != old_state {
                        *self.current_state.write().await = new_state;
                        self.handle_state_transition(
                            old_state,
                            new_state,
                            &space,
                            &input_controller,
                        )
                        .await;
                    } else {
                        self.handle_state_action(new_state, &space, &input_controller)
                            .await;
                    }
                }
                Err(e) => {
                    error!("Failed to check disk space: {}", e);
                }
            }
        }
    }

    async fn handle_state_transition(
        &self,
        old_state: DiskThresholdState,
        new_state: DiskThresholdState,
        space: &DiskSpace,
        input_controller: &InputController,
    ) {
        match (old_state, new_state) {
            (DiskThresholdState::OK, DiskThresholdState::Warning) => {
                warn!(
                    "DISK WARNING: Used space {}% (threshold: {}%)",
                    space.used_percent, self.config.warning_threshold_percent
                );
            }
            (DiskThresholdState::Warning, DiskThresholdState::Critical) => {
                error!(
                    "DISK CRITICAL: Used space {}% (threshold: {}%)",
                    space.used_percent, self.config.critical_threshold_percent
                );
                input_controller.throttle().await;
            }
            (DiskThresholdState::Warning, DiskThresholdState::OK) => {
                info!("Disk space recovered below warning threshold");
                input_controller.unthrottle().await;
            }
            (DiskThresholdState::Critical, DiskThresholdState::Emergency) => {
                error!(
                    "DISK EMERGENCY: Used space {}% (threshold: {}%)",
                    space.used_percent, self.config.emergency_threshold_percent
                );
                input_controller.pause_all().await;
            }
            (DiskThresholdState::Critical, DiskThresholdState::OK) => {
                info!("Disk space recovered below critical threshold");
                input_controller.unthrottle().await;
            }
            (DiskThresholdState::Emergency, DiskThresholdState::Critical) => {
                warn!("Disk space improved from emergency to critical, still paused");
            }
            (DiskThresholdState::Emergency, DiskThresholdState::OK) => {
                info!("Disk space recovered, resuming all inputs");
                input_controller.resume_all().await;
            }
            _ => {
                debug!(
                    "Disk state transition: {:?} -> {:?} ({}%)",
                    old_state, new_state, space.used_percent
                );
            }
        }
    }

    async fn handle_state_action(
        &self,
        state: DiskThresholdState,
        space: &DiskSpace,
        input_controller: &InputController,
    ) {
        match state {
            DiskThresholdState::Emergency => {
                if !input_controller.is_paused() {
                    warn!(
                        "Enforcing pause due to persistent emergency condition ({}%)",
                        space.used_percent
                    );
                    input_controller.pause_all().await;
                }
            }
            DiskThresholdState::Critical => {
                if !input_controller.is_throttled() {
                    warn!(
                        "Enforcing throttle due to persistent critical condition ({}%)",
                        space.used_percent
                    );
                    input_controller.throttle().await;
                }
            }
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_input_controller_pause_resume() {
        let controller = InputController::new();
        assert!(!controller.is_paused());

        let rt = tokio::runtime::Runtime::new().expect("Failed to create tokio runtime for test");
        rt.block_on(async {
            controller.pause_all().await;
            assert!(controller.is_paused());

            controller.resume_all().await;
            assert!(!controller.is_paused());
        });
    }

    #[test]
    fn test_input_controller_throttle() {
        let controller = InputController::new();
        assert!(!controller.is_throttled());

        let rt = tokio::runtime::Runtime::new().expect("Failed to create tokio runtime for test");
        rt.block_on(async {
            controller.throttle().await;
            assert!(controller.is_throttled());

            controller.unthrottle().await;
            assert!(!controller.is_throttled());
        });
    }

    #[test]
    fn test_disk_monitor_state_determination() {
        let config = DiskMonitoringConfig {
            enabled: true,
            check_interval_secs: 60,
            warning_threshold_percent: 90.0,
            critical_threshold_percent: 95.0,
            emergency_threshold_percent: 98.0,
        };

        let monitor = DiskMonitor::new(PathBuf::from("/tmp"), config);

        assert_eq!(monitor.determine_state(50.0), DiskThresholdState::OK);
        assert_eq!(monitor.determine_state(92.0), DiskThresholdState::Warning);
        assert_eq!(monitor.determine_state(96.0), DiskThresholdState::Critical);
        assert_eq!(monitor.determine_state(99.0), DiskThresholdState::Emergency);
    }

    #[test]
    fn test_disk_monitoring_config_default() {
        let config = DiskMonitoringConfig::default();
        assert!(config.enabled);
        assert_eq!(config.check_interval_secs, 60);
        assert_eq!(config.warning_threshold_percent, 90.0);
        assert_eq!(config.critical_threshold_percent, 95.0);
        assert_eq!(config.emergency_threshold_percent, 98.0);
    }
}
