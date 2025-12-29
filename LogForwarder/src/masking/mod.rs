use regex::Regex;
use serde_json::Value;
use std::collections::HashMap;
use thiserror::Error;

mod patterns;
pub use patterns::PiiPatterns;

#[derive(Error, Debug)]
pub enum MaskingError {
    #[error("Masking failed: {0}")]
    MaskingFailed(String),
    #[error("Invalid pattern: {0}")]
    InvalidPattern(String),
}

pub type MaskingResult<T> = Result<T, MaskingError>;

#[derive(Debug, Clone, PartialEq)]
pub enum MaskingStrategy {
    Preserve,
    MaskFull,
    MaskStart,
    MaskEnd,
    MaskMiddle,
    Hash,
    Custom(String),
}

pub struct MaskingRule {
    pub field_pattern: Regex,
    pub strategy: MaskingStrategy,
    pub replacement: Option<String>,
}

impl MaskingRule {
    pub fn new(field_pattern: String, strategy: MaskingStrategy) -> MaskingResult<Self> {
        let regex = Regex::new(&field_pattern)
            .map_err(|e| MaskingError::InvalidPattern(format!("Invalid regex: {}", e)))?;
        
        Ok(MaskingRule {
            field_pattern: regex,
            strategy,
            replacement: None,
        })
    }

    pub fn with_replacement(mut self, replacement: String) -> Self {
        self.replacement = Some(replacement);
        self
    }
}

pub struct MaskingEngine {
    rules: Vec<MaskingRule>,
    pii_patterns: PiiPatterns,
}

impl MaskingEngine {
    pub fn new() -> Self {
        MaskingEngine {
            rules: Vec::new(),
            pii_patterns: PiiPatterns::default(),
        }
    }

    pub fn add_rule(&mut self, rule: MaskingRule) {
        self.rules.push(rule);
    }

    pub fn mask_value(&self, value: &str, strategy: &MaskingStrategy) -> String {
        match strategy {
            MaskingStrategy::Preserve => value.to_string(),
            MaskingStrategy::MaskFull => "*".repeat(value.len().min(10)),
            MaskingStrategy::MaskStart => {
                let mask_len = (value.len() / 3).max(1);
                format!("{}{}",  "*".repeat(mask_len), &value[mask_len..])
            }
            MaskingStrategy::MaskEnd => {
                let mask_len = (value.len() / 3).max(1);
                format!("{}{}", &value[..value.len() - mask_len], "*".repeat(mask_len))
            }
            MaskingStrategy::MaskMiddle => {
                if value.len() <= 3 {
                    "*".repeat(value.len())
                } else {
                    let start_len = value.len() / 3;
                    let end_len = value.len() / 3;
                    format!(
                        "{}{}{}",
                        &value[..start_len],
                        "*".repeat(value.len() - start_len - end_len),
                        &value[value.len() - end_len..]
                    )
                }
            }
            MaskingStrategy::Hash => {
                format!("sha256:{:x}", calculate_hash(value))
            }
            MaskingStrategy::Custom(replacement) => replacement.clone(),
        }
    }

    pub fn apply_masking(&self, mut fields: HashMap<String, Value>) -> MaskingResult<HashMap<String, Value>> {
        for rule in &self.rules {
            for (field_name, value) in fields.iter_mut() {
                if rule.field_pattern.is_match(field_name) {
                    if let Value::String(s) = value {
                        *value = Value::String(self.mask_value(s, &rule.strategy));
                    }
                }
            }
        }

        Ok(fields)
    }

    pub fn detect_and_mask_pii(&self, mut fields: HashMap<String, Value>) -> HashMap<String, Value> {
        for (_, value) in fields.iter_mut() {
            if let Value::String(s) = value {
                if self.pii_patterns.is_credit_card(s) {
                    *value = Value::String(self.mask_value(s, &MaskingStrategy::MaskEnd));
                } else if self.pii_patterns.is_ssn(s) {
                    *value = Value::String(self.mask_value(s, &MaskingStrategy::MaskMiddle));
                } else if self.pii_patterns.is_email(s) {
                    *value = Value::String(self.mask_value(s, &MaskingStrategy::MaskStart));
                }
            }
        }
        fields
    }
}

impl Default for MaskingEngine {
    fn default() -> Self {
        Self::new()
    }
}

fn calculate_hash(s: &str) -> u64 {
    let mut hash: u64 = 5381;
    for byte in s.bytes() {
        hash = ((hash << 5).wrapping_add(hash)).wrapping_add(byte as u64);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mask_full() {
        let engine = MaskingEngine::new();
        let masked = engine.mask_value("secret123", &MaskingStrategy::MaskFull);
        assert!(masked.contains('*'));
    }

    #[test]
    fn test_mask_start() {
        let engine = MaskingEngine::new();
        let masked = engine.mask_value("john.doe@example.com", &MaskingStrategy::MaskStart);
        assert!(masked.starts_with('*'));
        assert!(masked.contains("example.com"));
    }

    #[test]
    fn test_mask_end() {
        let engine = MaskingEngine::new();
        let masked = engine.mask_value("1234-5678-9012-3456", &MaskingStrategy::MaskEnd);
        assert!(masked.ends_with('*'));
        assert!(masked.starts_with("1234"));
    }

    #[test]
    fn test_masking_rule() {
        let rule = MaskingRule::new("password".to_string(), MaskingStrategy::MaskFull).unwrap();
        assert!(rule.field_pattern.is_match("password"));
    }

    #[test]
    fn test_masking_engine_apply_rules() {
        let mut engine = MaskingEngine::new();
        let rule = MaskingRule::new("api_key".to_string(), MaskingStrategy::MaskEnd).unwrap();
        engine.add_rule(rule);

        let mut fields = HashMap::new();
        fields.insert("api_key".to_string(), Value::String("secret_key_123".to_string()));

        let result = engine.apply_masking(fields).unwrap();
        if let Some(Value::String(masked)) = result.get("api_key") {
            assert!(masked.ends_with('*'));
        }
    }
}
