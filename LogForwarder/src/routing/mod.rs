use serde_json::Value;
use std::collections::HashMap;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum RoutingError {
    #[error("Routing failed: {0}")]
    RoutingFailed(String),
    #[error("Invalid condition: {0}")]
    InvalidCondition(String),
    #[error("No matching route")]
    NoMatchingRoute,
}

pub type RoutingResult<T> = Result<T, RoutingError>;

#[derive(Debug, Clone)]
pub struct RoutingRule {
    pub condition: String,
    pub output_index: String,
    pub severity: Option<String>,
    pub alert: bool,
}

impl RoutingRule {
    pub fn new(condition: String, output_index: String) -> Self {
        RoutingRule {
            condition,
            output_index,
            severity: None,
            alert: false,
        }
    }

    pub fn with_severity(mut self, severity: String) -> Self {
        self.severity = Some(severity);
        self
    }

    pub fn with_alert(mut self, alert: bool) -> Self {
        self.alert = alert;
        self
    }
}

pub struct ConditionalRouter {
    rules: Vec<RoutingRule>,
    default_index: String,
}

impl ConditionalRouter {
    pub fn new(default_index: String) -> Self {
        ConditionalRouter {
            rules: Vec::new(),
            default_index,
        }
    }

    pub fn add_rule(&mut self, rule: RoutingRule) {
        self.rules.push(rule);
    }

    pub fn evaluate_condition(&self, condition: &str, fields: &HashMap<String, Value>) -> bool {
        let condition = condition.trim();

        for (key, value) in fields.iter() {
            let field_pattern = format!("{}", key);
            if condition.contains(&field_pattern) {
                let value_str = match value {
                    Value::String(s) => s.clone(),
                    Value::Number(n) => n.to_string(),
                    Value::Bool(b) => b.to_string(),
                    _ => continue,
                };

                if condition.contains(">=") {
                    let parts: Vec<&str> = condition.split(">=").collect();
                    if parts.len() == 2 {
                        if let Ok(threshold) = parts[1].trim().parse::<f64>() {
                            if let Ok(val) = value_str.parse::<f64>() {
                                if val >= threshold {
                                    return true;
                                }
                            }
                        }
                    }
                } else if condition.contains(">") {
                    let parts: Vec<&str> = condition.split('>').collect();
                    if parts.len() == 2 {
                        if let Ok(threshold) = parts[1].trim().parse::<f64>() {
                            if let Ok(val) = value_str.parse::<f64>() {
                                if val > threshold {
                                    return true;
                                }
                            }
                        }
                    }
                } else if condition.contains("==") {
                    let parts: Vec<&str> = condition.split("==").collect();
                    if parts.len() == 2 && parts[1].trim() == value_str {
                        return true;
                    }
                }
            }
        }

        false
    }

    pub fn route(&self, fields: &HashMap<String, Value>) -> RoutingResult<RoutingRule> {
        for rule in &self.rules {
            if self.evaluate_condition(&rule.condition, fields) {
                return Ok(rule.clone());
            }
        }

        let default_rule = RoutingRule::new("default".to_string(), self.default_index.clone());
        Ok(default_rule)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_routing_rule_creation() {
        let rule = RoutingRule::new("response >= 500".to_string(), "errors".to_string())
            .with_severity("critical".to_string())
            .with_alert(true);

        assert_eq!(rule.output_index, "errors");
        assert_eq!(rule.severity, Some("critical".to_string()));
        assert!(rule.alert);
    }

    #[test]
    fn test_conditional_router_exact_match() {
        let mut router = ConditionalRouter::new("default_index".to_string());
        router.add_rule(RoutingRule::new("response >= 500".to_string(), "errors".to_string()));

        let mut fields = HashMap::new();
        fields.insert("response".to_string(), Value::Number(503.into()));

        let result = router.route(&fields);
        assert!(result.is_ok());
        assert_eq!(result.unwrap().output_index, "errors");
    }

    #[test]
    fn test_conditional_router_default() {
        let router = ConditionalRouter::new("default_index".to_string());

        let mut fields = HashMap::new();
        fields.insert("response".to_string(), Value::Number(200.into()));

        let result = router.route(&fields);
        assert!(result.is_ok());
        assert_eq!(result.unwrap().output_index, "default_index");
    }

    #[test]
    fn test_evaluate_condition_greater_than() {
        let router = ConditionalRouter::new("default".to_string());
        let mut fields = HashMap::new();
        fields.insert("response".to_string(), Value::Number(500.into()));

        let result = router.evaluate_condition("response >= 500", &fields);
        assert!(result);
    }
}
