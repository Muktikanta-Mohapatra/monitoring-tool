use std::fmt;

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Severity {
    Unknown = 0,
    Debug = 1,
    Info = 2,
    Warning = 3,
    Error = 4,
    Critical = 5,
}

impl Severity {
    pub fn from_u8(val: u8) -> Self {
        match val {
            1 => Severity::Debug,
            2 => Severity::Info,
            3 => Severity::Warning,
            4 => Severity::Error,
            5 => Severity::Critical,
            _ => Severity::Unknown,
        }
    }

    pub fn as_u8(&self) -> u8 {
        *self as u8
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Severity::Unknown => "UNKNOWN",
            Severity::Debug => "DEBUG",
            Severity::Info => "INFO",
            Severity::Warning => "WARNING",
            Severity::Error => "ERROR",
            Severity::Critical => "CRITICAL",
        }
    }

    pub fn from_str(s: &str) -> Self {
        match s.to_uppercase().as_str() {
            "DEBUG" => Severity::Debug,
            "INFO" => Severity::Info,
            "WARNING" => Severity::Warning,
            "ERROR" => Severity::Error,
            "CRITICAL" => Severity::Critical,
            _ => Severity::Unknown,
        }
    }
}

impl From<u8> for Severity {
    fn from(val: u8) -> Self {
        Severity::from_u8(val)
    }
}

impl From<Severity> for u8 {
    fn from(severity: Severity) -> Self {
        severity.as_u8()
    }
}

impl fmt::Display for Severity {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_severity_from_u8() {
        assert_eq!(Severity::from_u8(0), Severity::Unknown);
        assert_eq!(Severity::from_u8(1), Severity::Debug);
        assert_eq!(Severity::from_u8(2), Severity::Info);
        assert_eq!(Severity::from_u8(3), Severity::Warning);
        assert_eq!(Severity::from_u8(4), Severity::Error);
        assert_eq!(Severity::from_u8(5), Severity::Critical);
        assert_eq!(Severity::from_u8(99), Severity::Unknown);
    }

    #[test]
    fn test_severity_as_u8() {
        assert_eq!(Severity::Unknown.as_u8(), 0);
        assert_eq!(Severity::Debug.as_u8(), 1);
        assert_eq!(Severity::Critical.as_u8(), 5);
    }

    #[test]
    fn test_severity_as_str() {
        assert_eq!(Severity::Unknown.as_str(), "UNKNOWN");
        assert_eq!(Severity::Debug.as_str(), "DEBUG");
        assert_eq!(Severity::Error.as_str(), "ERROR");
    }

    #[test]
    fn test_severity_from_str() {
        assert_eq!(Severity::from_str("debug"), Severity::Debug);
        assert_eq!(Severity::from_str("INFO"), Severity::Info);
        assert_eq!(Severity::from_str("critical"), Severity::Critical);
        assert_eq!(Severity::from_str("unknown"), Severity::Unknown);
    }

    #[test]
    fn test_severity_display() {
        assert_eq!(Severity::Error.to_string(), "ERROR");
        assert_eq!(Severity::Info.to_string(), "INFO");
    }

    #[test]
    fn test_severity_from_trait() {
        let severity: Severity = 3u8.into();
        assert_eq!(severity, Severity::Warning);
        
        let val: u8 = Severity::Critical.into();
        assert_eq!(val, 5u8);
    }
}
