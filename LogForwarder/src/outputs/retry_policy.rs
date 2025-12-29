use std::time::Duration;

#[derive(Debug, Clone)]
pub struct RetryPolicy {
    pub base_delay_ms: u64,
    pub max_delay_ms: u64,
    pub max_attempts: u32,
    pub jitter_percent: f64,
}

impl RetryPolicy {
    pub fn new(base_delay_ms: u64, max_delay_ms: u64, max_attempts: u32, jitter_percent: f64) -> Self {
        RetryPolicy {
            base_delay_ms,
            max_delay_ms,
            max_attempts,
            jitter_percent,
        }
    }

    pub fn default() -> Self {
        RetryPolicy {
            base_delay_ms: 1000,
            max_delay_ms: 30000,
            max_attempts: 3,
            jitter_percent: 10.0,
        }
    }

    pub fn calculate_backoff(&self, attempt: u32) -> Duration {
        if attempt == 0 {
            return Duration::from_millis(0);
        }

        let exponent = (attempt - 1) as u32;
        let base_delay = self.base_delay_ms as f64;
        let exponential_delay = base_delay * 2_f64.powi(exponent as i32);
        let capped_delay = exponential_delay.min(self.max_delay_ms as f64);

        let jitter = if self.jitter_percent > 0.0 {
            let jitter_range = capped_delay * (self.jitter_percent / 100.0);
            let random_jitter = rand::random::<f64>() * jitter_range * 2.0 - jitter_range;
            random_jitter
        } else {
            0.0
        };

        let final_delay = (capped_delay + jitter).max(0.0) as u64;
        Duration::from_millis(final_delay)
    }

    pub fn can_retry(&self, attempt: u32) -> bool {
        attempt < self.max_attempts
    }

    pub fn should_retry_on_error(&self, error: &str) -> bool {
        let retryable_errors = [
            "connection refused",
            "connection timeout",
            "temporary failure",
            "service unavailable",
            "deadline exceeded",
            "resource exhausted",
        ];

        let error_lower = error.to_lowercase();
        retryable_errors.iter().any(|&err| error_lower.contains(err))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_retry_policy_creation() {
        let policy = RetryPolicy::new(1000, 30000, 3, 10.0);
        assert_eq!(policy.base_delay_ms, 1000);
        assert_eq!(policy.max_delay_ms, 30000);
        assert_eq!(policy.max_attempts, 3);
        assert_eq!(policy.jitter_percent, 10.0);
    }

    #[test]
    fn test_default_retry_policy() {
        let policy = RetryPolicy::default();
        assert_eq!(policy.base_delay_ms, 1000);
        assert_eq!(policy.max_delay_ms, 30000);
        assert_eq!(policy.max_attempts, 3);
        assert_eq!(policy.jitter_percent, 10.0);
    }

    #[test]
    fn test_backoff_calculation() {
        let policy = RetryPolicy::new(1000, 30000, 3, 0.0);

        let delay_attempt_0 = policy.calculate_backoff(0);
        assert_eq!(delay_attempt_0.as_millis(), 0);

        let delay_attempt_1 = policy.calculate_backoff(1);
        assert_eq!(delay_attempt_1.as_millis(), 1000);

        let delay_attempt_2 = policy.calculate_backoff(2);
        assert_eq!(delay_attempt_2.as_millis(), 2000);

        let delay_attempt_3 = policy.calculate_backoff(3);
        assert_eq!(delay_attempt_3.as_millis(), 4000);
    }

    #[test]
    fn test_backoff_max_cap() {
        let policy = RetryPolicy::new(1000, 30000, 10, 0.0);

        let delay_attempt_10 = policy.calculate_backoff(10);
        assert_eq!(delay_attempt_10.as_millis(), 30000);

        let delay_attempt_20 = policy.calculate_backoff(20);
        assert_eq!(delay_attempt_20.as_millis(), 30000);
    }

    #[test]
    fn test_can_retry() {
        let policy = RetryPolicy::default();
        assert!(policy.can_retry(0));
        assert!(policy.can_retry(1));
        assert!(policy.can_retry(2));
        assert!(!policy.can_retry(3));
        assert!(!policy.can_retry(4));
    }

    #[test]
    fn test_should_retry_on_error() {
        let policy = RetryPolicy::default();

        assert!(policy.should_retry_on_error("Connection refused"));
        assert!(policy.should_retry_on_error("Connection timeout"));
        assert!(policy.should_retry_on_error("Temporary failure"));
        assert!(policy.should_retry_on_error("Service unavailable"));
        assert!(policy.should_retry_on_error("Deadline exceeded"));
        assert!(policy.should_retry_on_error("Resource exhausted"));

        assert!(!policy.should_retry_on_error("Invalid input"));
        assert!(!policy.should_retry_on_error("Authentication failed"));
    }

    #[test]
    fn test_backoff_with_jitter() {
        let policy = RetryPolicy::new(1000, 30000, 3, 10.0);

        for _ in 0..10 {
            let delay = policy.calculate_backoff(2);
            let delay_ms = delay.as_millis() as f64;
            assert!(delay_ms >= 1800.0 && delay_ms <= 2200.0, "Delay {} out of expected range [1800, 2200]", delay_ms);
        }
    }

    #[test]
    fn test_backoff_no_jitter() {
        let policy = RetryPolicy::new(1000, 30000, 3, 0.0);

        for _ in 0..10 {
            let delay = policy.calculate_backoff(2);
            assert_eq!(delay.as_millis(), 2000);
        }
    }
}
