use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, SystemTime};
use tracing::{debug, warn};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CircuitBreakerState {
    Closed,
    Open,
    HalfOpen,
}

pub struct IndexerBreaker {
    state: RwLock<CircuitBreakerState>,
    failure_count: AtomicU32,
    success_count: AtomicU32,
    last_failure_time: RwLock<SystemTime>,
    total_failures: AtomicU64,
    total_successes: AtomicU64,
}

impl IndexerBreaker {
    fn new() -> Self {
        IndexerBreaker {
            state: RwLock::new(CircuitBreakerState::Closed),
            failure_count: AtomicU32::new(0),
            success_count: AtomicU32::new(0),
            last_failure_time: RwLock::new(SystemTime::now()),
            total_failures: AtomicU64::new(0),
            total_successes: AtomicU64::new(0),
        }
    }

    pub fn record_success(&self) {
        let current_state = *self.state.read();
        self.total_successes.fetch_add(1, Ordering::Relaxed);

        match current_state {
            CircuitBreakerState::HalfOpen => {
                self.success_count.fetch_add(1, Ordering::Release);
                if self.success_count.load(Ordering::Acquire) >= 3 {
                    debug!("Circuit breaker transitioning to Closed");
                    *self.state.write() = CircuitBreakerState::Closed;
                    self.failure_count.store(0, Ordering::Release);
                    self.success_count.store(0, Ordering::Release);
                }
            }
            CircuitBreakerState::Closed => {
                self.failure_count.store(0, Ordering::Release);
            }
            CircuitBreakerState::Open => {}
        }
    }

    pub fn record_failure(&self) {
        let current_state = *self.state.read();
        self.total_failures.fetch_add(1, Ordering::Relaxed);
        *self.last_failure_time.write() = SystemTime::now();

        match current_state {
            CircuitBreakerState::Closed => {
                self.failure_count.fetch_add(1, Ordering::Release);
                if self.failure_count.load(Ordering::Acquire) >= 5 {
                    warn!("Circuit breaker transitioning to Open (5 failures)");
                    *self.state.write() = CircuitBreakerState::Open;
                }
            }
            CircuitBreakerState::HalfOpen => {
                warn!("Circuit breaker transitioning to Open (failure in HalfOpen)");
                *self.state.write() = CircuitBreakerState::Open;
                self.failure_count.store(0, Ordering::Release);
                self.success_count.store(0, Ordering::Release);
            }
            CircuitBreakerState::Open => {}
        }
    }

    pub fn check_state(&self, timeout: Duration) -> CircuitBreakerState {
        let current_state = *self.state.read();

        if current_state == CircuitBreakerState::Open {
            if let Ok(elapsed) = self.last_failure_time.read().elapsed() {
                if elapsed >= timeout {
                    debug!("Circuit breaker transitioning to HalfOpen (timeout)");
                    *self.state.write() = CircuitBreakerState::HalfOpen;
                    self.failure_count.store(0, Ordering::Release);
                    self.success_count.store(0, Ordering::Release);
                    return CircuitBreakerState::HalfOpen;
                }
            }
        }

        current_state
    }

    pub fn is_available(&self, timeout: Duration) -> bool {
        self.check_state(timeout) != CircuitBreakerState::Open
    }

    pub fn stats(&self) -> CircuitBreakerStats {
        CircuitBreakerStats {
            state: *self.state.read(),
            failure_count: self.failure_count.load(Ordering::Relaxed),
            success_count: self.success_count.load(Ordering::Relaxed),
            total_failures: self.total_failures.load(Ordering::Relaxed),
            total_successes: self.total_successes.load(Ordering::Relaxed),
        }
    }
}

#[derive(Debug, Clone)]
pub struct CircuitBreakerStats {
    pub state: CircuitBreakerState,
    pub failure_count: u32,
    pub success_count: u32,
    pub total_failures: u64,
    pub total_successes: u64,
}

pub struct CircuitBreaker {
    breakers: Arc<RwLock<HashMap<String, Arc<IndexerBreaker>>>>,
    open_timeout: Duration,
}

impl CircuitBreaker {
    pub fn new(indexer_ids: Vec<String>) -> Self {
        let mut breakers = HashMap::new();
        for id in indexer_ids {
            breakers.insert(id, Arc::new(IndexerBreaker::new()));
        }

        CircuitBreaker {
            breakers: Arc::new(RwLock::new(breakers)),
            open_timeout: Duration::from_secs(60),
        }
    }

    pub fn record_success(&self, indexer_id: &str) {
        if let Some(breaker) = self.breakers.read().get(indexer_id) {
            breaker.record_success();
        }
    }

    pub fn record_failure(&self, indexer_id: &str) {
        if let Some(breaker) = self.breakers.read().get(indexer_id) {
            breaker.record_failure();
        }
    }

    pub fn is_available(&self, indexer_id: &str) -> bool {
        self.breakers
            .read()
            .get(indexer_id)
            .map(|b| b.is_available(self.open_timeout))
            .unwrap_or(false)
    }

    pub fn get_available_indexers(&self) -> Vec<String> {
        self.breakers
            .read()
            .iter()
            .filter(|(_, breaker)| breaker.is_available(self.open_timeout))
            .map(|(id, _)| id.clone())
            .collect()
    }

    pub fn stats(&self, indexer_id: &str) -> Option<CircuitBreakerStats> {
        self.breakers.read().get(indexer_id).map(|b| b.stats())
    }

    pub fn all_stats(&self) -> HashMap<String, CircuitBreakerStats> {
        self.breakers
            .read()
            .iter()
            .map(|(id, breaker)| (id.clone(), breaker.stats()))
            .collect()
    }

    pub fn set_open_timeout(&mut self, timeout: Duration) {
        self.open_timeout = timeout;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_circuit_breaker_closed_to_open() {
        let breaker = IndexerBreaker::new();
        assert_eq!(*breaker.state.read(), CircuitBreakerState::Closed);

        for _ in 0..5 {
            breaker.record_failure();
        }

        assert_eq!(*breaker.state.read(), CircuitBreakerState::Open);
    }

    #[test]
    fn test_circuit_breaker_open_to_half_open() {
        let breaker = IndexerBreaker::new();

        for _ in 0..5 {
            breaker.record_failure();
        }
        assert_eq!(*breaker.state.read(), CircuitBreakerState::Open);

        std::thread::sleep(Duration::from_millis(100));
        let state = breaker.check_state(Duration::from_millis(50));
        assert_eq!(state, CircuitBreakerState::HalfOpen);
    }

    #[test]
    fn test_circuit_breaker_half_open_to_closed() {
        let breaker = IndexerBreaker::new();

        for _ in 0..5 {
            breaker.record_failure();
        }

        std::thread::sleep(Duration::from_millis(100));
        breaker.check_state(Duration::from_millis(50));

        for _ in 0..3 {
            breaker.record_success();
        }

        assert_eq!(*breaker.state.read(), CircuitBreakerState::Closed);
    }
}
