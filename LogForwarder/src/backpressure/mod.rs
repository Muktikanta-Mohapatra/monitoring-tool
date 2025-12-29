use std::sync::atomic::{AtomicU8, Ordering};
use std::sync::Arc;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(u8)]
pub enum BackpressureState {
    Green = 0,
    Yellow = 1,
    Orange = 2,
    Red = 3,
}

impl BackpressureState {
    pub fn from_u8(val: u8) -> Self {
        match val {
            0 => BackpressureState::Green,
            1 => BackpressureState::Yellow,
            2 => BackpressureState::Orange,
            3 => BackpressureState::Red,
            _ => BackpressureState::Green,
        }
    }

    pub fn is_critical(&self) -> bool {
        matches!(self, BackpressureState::Red | BackpressureState::Orange)
    }
}

pub struct BackpressureMonitor {
    state: Arc<AtomicU8>,
    thresholds: BackpressureThresholds,
}

#[derive(Debug, Clone, Copy)]
pub struct BackpressureThresholds {
    pub green_max: f64,
    pub yellow_min: f64,
    pub yellow_max: f64,
    pub orange_min: f64,
    pub orange_max: f64,
    pub red_min: f64,
}

impl Default for BackpressureThresholds {
    fn default() -> Self {
        BackpressureThresholds {
            green_max: 50.0,
            yellow_min: 50.0,
            yellow_max: 80.0,
            orange_min: 80.0,
            orange_max: 90.0,
            red_min: 90.0,
        }
    }
}

impl BackpressureMonitor {
    pub fn new() -> Self {
        BackpressureMonitor::with_thresholds(BackpressureThresholds::default())
    }

    pub fn with_thresholds(thresholds: BackpressureThresholds) -> Self {
        BackpressureMonitor {
            state: Arc::new(AtomicU8::new(BackpressureState::Green as u8)),
            thresholds,
        }
    }

    pub fn update(&self, fill_percentage: f64) {
        let new_state = match fill_percentage {
            p if p <= self.thresholds.green_max => BackpressureState::Green,
            p if p <= self.thresholds.yellow_max => BackpressureState::Yellow,
            p if p <= self.thresholds.orange_max => BackpressureState::Orange,
            _ => BackpressureState::Red,
        };

        let current = BackpressureState::from_u8(self.state.load(Ordering::Relaxed));
        if current != new_state {
            self.state.store(new_state as u8, Ordering::Release);
        }
    }

    pub fn get_state(&self) -> BackpressureState {
        BackpressureState::from_u8(self.state.load(Ordering::Acquire))
    }

    pub fn batch_timeout_ms(&self) -> u64 {
        match self.get_state() {
            BackpressureState::Green => 5000,
            BackpressureState::Yellow => 2000,
            BackpressureState::Orange => 500,
            BackpressureState::Red => 100,
        }
    }

    pub fn batch_size_multiplier(&self) -> f64 {
        match self.get_state() {
            BackpressureState::Green => 1.0,
            BackpressureState::Yellow => 0.8,
            BackpressureState::Orange => 0.5,
            BackpressureState::Red => 0.25,
        }
    }

    pub fn should_throttle_producers(&self) -> bool {
        self.get_state() == BackpressureState::Red
    }

    pub fn should_reduce_compression_threads(&self) -> bool {
        matches!(
            self.get_state(),
            BackpressureState::Orange | BackpressureState::Red
        )
    }
}

impl Clone for BackpressureMonitor {
    fn clone(&self) -> Self {
        BackpressureMonitor {
            state: Arc::clone(&self.state),
            thresholds: self.thresholds,
        }
    }
}

impl Default for BackpressureMonitor {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_backpressure_state_transitions() {
        let monitor = BackpressureMonitor::new();

        monitor.update(25.0);
        assert_eq!(monitor.get_state(), BackpressureState::Green);

        monitor.update(60.0);
        assert_eq!(monitor.get_state(), BackpressureState::Yellow);

        monitor.update(85.0);
        assert_eq!(monitor.get_state(), BackpressureState::Orange);

        monitor.update(95.0);
        assert_eq!(monitor.get_state(), BackpressureState::Red);

        monitor.update(10.0);
        assert_eq!(monitor.get_state(), BackpressureState::Green);
    }

    #[test]
    fn test_batch_timeout_scaling() {
        let monitor = BackpressureMonitor::new();

        monitor.update(25.0);
        assert_eq!(monitor.batch_timeout_ms(), 5000);

        monitor.update(60.0);
        assert_eq!(monitor.batch_timeout_ms(), 2000);

        monitor.update(85.0);
        assert_eq!(monitor.batch_timeout_ms(), 500);

        monitor.update(95.0);
        assert_eq!(monitor.batch_timeout_ms(), 100);
    }

    #[test]
    fn test_batch_size_multiplier() {
        let monitor = BackpressureMonitor::new();

        monitor.update(25.0);
        assert_eq!(monitor.batch_size_multiplier(), 1.0);

        monitor.update(60.0);
        assert_eq!(monitor.batch_size_multiplier(), 0.8);

        monitor.update(85.0);
        assert_eq!(monitor.batch_size_multiplier(), 0.5);

        monitor.update(95.0);
        assert_eq!(monitor.batch_size_multiplier(), 0.25);
    }

    #[test]
    fn test_throttle_indicators() {
        let monitor = BackpressureMonitor::new();

        monitor.update(25.0);
        assert!(!monitor.should_throttle_producers());
        assert!(!monitor.should_reduce_compression_threads());

        monitor.update(95.0);
        assert!(monitor.should_throttle_producers());
        assert!(monitor.should_reduce_compression_threads());

        monitor.update(85.0);
        assert!(!monitor.should_throttle_producers());
        assert!(monitor.should_reduce_compression_threads());
    }

    #[test]
    fn test_backpressure_clone() {
        let monitor = BackpressureMonitor::new();
        monitor.update(60.0);

        let cloned = monitor.clone();
        assert_eq!(cloned.get_state(), BackpressureState::Yellow);
    }
}
