use regex::Regex;

pub struct PiiPatterns {
    credit_card: Regex,
    ssn: Regex,
    email: Regex,
    phone: Regex,
    ip_address: Regex,
}

impl PiiPatterns {
    pub fn new() -> Self {
        PiiPatterns {
            credit_card: Regex::new(r"\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b").unwrap(),
            ssn: Regex::new(r"\b\d{3}-\d{2}-\d{4}\b").unwrap(),
            email: Regex::new(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b").unwrap(),
            phone: Regex::new(r"(?:\+?1[-.]?)?(?:\()?[0-9]{3}(?:\))?[-. ]?[0-9]{3}[-. ]?[0-9]{4}").unwrap(),
            ip_address: Regex::new(r"\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b").unwrap(),
        }
    }

    pub fn is_credit_card(&self, value: &str) -> bool {
        self.credit_card.is_match(value)
    }

    pub fn is_ssn(&self, value: &str) -> bool {
        self.ssn.is_match(value)
    }

    pub fn is_email(&self, value: &str) -> bool {
        self.email.is_match(value)
    }

    pub fn is_phone(&self, value: &str) -> bool {
        self.phone.is_match(value)
    }

    pub fn is_ip_address(&self, value: &str) -> bool {
        self.ip_address.is_match(value)
    }

    pub fn detect_pii_type(&self, value: &str) -> Option<String> {
        if self.is_credit_card(value) {
            Some("credit_card".to_string())
        } else if self.is_ssn(value) {
            Some("ssn".to_string())
        } else if self.is_email(value) {
            Some("email".to_string())
        } else if self.is_phone(value) {
            Some("phone".to_string())
        } else if self.is_ip_address(value) {
            Some("ip_address".to_string())
        } else {
            None
        }
    }
}

impl Default for PiiPatterns {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_credit_card() {
        let patterns = PiiPatterns::new();
        assert!(patterns.is_credit_card("1234-5678-9012-3456"));
        assert!(patterns.is_credit_card("1234 5678 9012 3456"));
        assert!(!patterns.is_credit_card("1234-5678-9012-345"));
    }

    #[test]
    fn test_detect_ssn() {
        let patterns = PiiPatterns::new();
        assert!(patterns.is_ssn("123-45-6789"));
        assert!(!patterns.is_ssn("123456789"));
    }

    #[test]
    fn test_detect_email() {
        let patterns = PiiPatterns::new();
        assert!(patterns.is_email("user@example.com"));
        assert!(patterns.is_email("test.user+tag@domain.co.uk"));
        assert!(!patterns.is_email("not_an_email"));
    }

    #[test]
    fn test_detect_phone() {
        let patterns = PiiPatterns::new();
        assert!(patterns.is_phone("123-456-7890"));
        assert!(patterns.is_phone("(123) 456-7890"));
        assert!(patterns.is_phone("+1-123-456-7890"));
    }

    #[test]
    fn test_detect_ip() {
        let patterns = PiiPatterns::new();
        assert!(patterns.is_ip_address("192.168.1.1"));
        assert!(patterns.is_ip_address("255.255.255.255"));
        assert!(!patterns.is_ip_address("256.256.256.256"));
    }

    #[test]
    fn test_detect_pii_type() {
        let patterns = PiiPatterns::new();
        assert_eq!(patterns.detect_pii_type("user@example.com"), Some("email".to_string()));
        assert_eq!(patterns.detect_pii_type("1234-5678-9012-3456"), Some("credit_card".to_string()));
    }
}
