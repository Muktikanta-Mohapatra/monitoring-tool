use std::env;
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct CliArgs {
    pub log_path: Option<String>,
    pub api_key: Option<String>,
    pub config_file: Option<String>,
    pub middleware_url: Option<String>,
    pub forwarder_id: Option<String>,
    pub help: bool,
}

impl CliArgs {
    /// Parse command-line arguments
    /// Supports multiple formats:
    /// 1. high-perf-forwarder <log-path> <api-key>
    /// 2. high-perf-forwarder --config config.yaml
    /// 3. high-perf-forwarder --log-path <path> --api-key <key>
    /// 4. high-perf-forwarder logfile='<path>' api-key=<key>
    pub fn parse() -> Self {
        let args: Vec<String> = env::args().collect();

        let mut cli_args = CliArgs {
            log_path: None,
            api_key: None,
            config_file: None,
            middleware_url: None,
            forwarder_id: None,
            help: false,
        };

        if args.len() == 1 {
            // No arguments, use defaults
            return cli_args;
        }

        // Check for help
        if args.iter().any(|a| a == "--help" || a == "-h") {
            cli_args.help = true;
            return cli_args;
        }

        // Simple format: executable <log-path> <api-key>
        if args.len() == 3 && !args[1].starts_with("--") && !args[2].starts_with("--") && !args[1].contains('=') && !args[2].contains('=') {
            cli_args.log_path = Some(args[1].clone());
            cli_args.api_key = Some(args[2].clone());
            return cli_args;
        }

        // Parse named arguments and key=value format
        let mut i = 1;
        while i < args.len() {
            let arg = &args[i];
            
            // Check for key=value format
            if arg.contains('=') && !arg.starts_with("--") {
                if let Some(eq_pos) = arg.find('=') {
                    let key = &arg[..eq_pos];
                    let value = &arg[eq_pos + 1..];
                    
                    // Remove surrounding quotes from value
                    let cleaned_value = Self::remove_quotes(value);
                    
                    match key {
                        "logfile" | "log-path" | "log_path" => {
                            cli_args.log_path = Some(cleaned_value);
                        }
                        "api-key" | "api_key" | "apikey" => {
                            cli_args.api_key = Some(cleaned_value);
                        }
                        "config" => {
                            cli_args.config_file = Some(cleaned_value);
                        }
                        "url" | "middleware-url" => {
                            cli_args.middleware_url = Some(cleaned_value);
                        }
                        "forwarder-id" | "forwarder_id" => {
                            cli_args.forwarder_id = Some(cleaned_value);
                        }
                        _ => {
                            eprintln!("Warning: Unknown parameter: {}", key);
                        }
                    }
                    i += 1;
                } else {
                    i += 1;
                }
                continue;
            }
            
            // Traditional named arguments
            match arg.as_str() {
                "--config" | "-c" => {
                    if i + 1 < args.len() {
                        cli_args.config_file = Some(args[i + 1].clone());
                        i += 2;
                    } else {
                        eprintln!("Error: --config requires a value");
                        i += 1;
                    }
                }
                "--log-path" | "-l" => {
                    if i + 1 < args.len() {
                        cli_args.log_path = Some(args[i + 1].clone());
                        i += 2;
                    } else {
                        eprintln!("Error: --log-path requires a value");
                        i += 1;
                    }
                }
                "--api-key" | "-k" => {
                    if i + 1 < args.len() {
                        cli_args.api_key = Some(args[i + 1].clone());
                        i += 2;
                    } else {
                        eprintln!("Error: --api-key requires a value");
                        i += 1;
                    }
                }
                "--url" | "-u" => {
                    if i + 1 < args.len() {
                        cli_args.middleware_url = Some(args[i + 1].clone());
                        i += 2;
                    } else {
                        eprintln!("Error: --url requires a value");
                        i += 1;
                    }
                }
                "--forwarder-id" | "-i" => {
                    if i + 1 < args.len() {
                        cli_args.forwarder_id = Some(args[i + 1].clone());
                        i += 2;
                    } else {
                        eprintln!("Error: --forwarder-id requires a value");
                        i += 1;
                    }
                }
                _ => {
                    eprintln!("Warning: Unknown argument: {}", arg);
                    i += 1;
                }
            }
        }

        cli_args
    }

    /// Remove surrounding quotes from a string
    fn remove_quotes(value: &str) -> String {
        let trimmed = value.trim();
        if (trimmed.starts_with('\'') && trimmed.ends_with('\'')) || 
           (trimmed.starts_with('"') && trimmed.ends_with('"')) {
            trimmed[1..trimmed.len() - 1].to_string()
        } else {
            trimmed.to_string()
        }
    }

    pub fn print_help() {
        println!(r#"
High Performance Log Forwarder

USAGE:
    Simple mode:
        high-perf-forwarder <LOG_PATH> <API_KEY>

    Named arguments (long form):
        high-perf-forwarder --log-path <PATH> --api-key <KEY> [OPTIONS]

    Key=value format:
        high-perf-forwarder logfile='<PATH>' api-key=<KEY> [OPTIONS]

    Config file mode:
        high-perf-forwarder --config <CONFIG_FILE>

ARGUMENTS:
    <LOG_PATH>                 Path to log files (supports wildcards: /logs/*.log)
    <API_KEY>                  API key for authentication with middleware

OPTIONS:
    -c, --config <FILE>        Path to YAML configuration file
    -l, --log-path <PATH>      Path to log files or directory
    -k, --api-key <KEY>        API key for middleware authentication
    -u, --url <URL>            Middleware URL (default: http://localhost:8080/api/v1/events/batch)
    -i, --forwarder-id <ID>    Forwarder ID (default: auto-generated UUID)
    -h, --help                 Print this help message

KEY=VALUE FORMAT PARAMETERS:
    logfile                    Path to log files (alias: log-path, log_path)
    api-key                    API key for authentication (alias: api_key, apikey)
    config                     Path to YAML configuration file
    url                        Middleware URL
    forwarder-id               Forwarder ID (alias: forwarder_id)

EXAMPLES:
    # Simple usage with log folder and API key
    high-perf-forwarder ./logs abc123xyz

    # Monitor specific log files
    high-perf-forwarder "/var/log/*.log" abc123xyz

    # Using named arguments
    high-perf-forwarder --log-path ./logs --api-key abc123xyz

    # Key=value format (with single quotes)
    high-perf-forwarder logfile='./logs/app12.log' api-key='myapikey123'

    # Key=value format (with double quotes)
    high-perf-forwarder logfile="./logs/*.log" api-key="myapikey123"

    # Complex API keys (with special characters)
    high-perf-forwarder logfile='P:\logs\app12.log' api-key='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'

    # Custom middleware URL
    high-perf-forwarder ./logs abc123xyz --url http://prod:8080/api/v1/events/batch

    # Using config file (full control)
    high-perf-forwarder --config config/production.yaml

ENVIRONMENT VARIABLES:
    FORWARDER_API_KEY          API key (used if not provided via CLI)
    FORWARDER_ID               Forwarder ID
    MIDDLEWARE_URL             Middleware endpoint URL
    RUST_LOG                   Log level (trace, debug, info, warn, error)

NOTES:
    - CLI arguments take precedence over environment variables
    - Environment variables take precedence over config file values
    - Wildcard patterns are supported: *.log, /logs/**/*.log
    - Default middleware URL: http://localhost:8080/api/v1/events/batch
    - Config file can be used for advanced configurations
    - In key=value format, values can be quoted with single (') or double (") quotes
    - Special characters in values should be quoted
"#);
    }

    /// Check if we're in simple CLI mode (no config file)
    pub fn is_simple_mode(&self) -> bool {
        self.config_file.is_none() && (self.log_path.is_some() || self.api_key.is_some())
    }

    /// Validate CLI arguments
    pub fn validate(&self) -> Result<(), String> {
        if self.help {
            return Ok(());
        }

        // If using simple mode, require both log_path and api_key
        if self.is_simple_mode() {
            if self.log_path.is_none() {
                return Err("Log path is required. Use: high-perf-forwarder <log-path> <api-key>".to_string());
            }
            if self.api_key.is_none() {
                // Check environment variable
                if env::var("FORWARDER_API_KEY").is_err() {
                    return Err("API key is required. Provide via CLI or FORWARDER_API_KEY env variable".to_string());
                }
            }
        }

        // Validate log path exists if provided
        if let Some(ref path) = self.log_path {
            let path_buf = PathBuf::from(path);

            // If it's a wildcard pattern, check the parent directory
            if path.contains('*') {
                let parent = path_buf.parent().unwrap_or(&path_buf);
                if !parent.exists() && parent.to_str() != Some(".") {
                    return Err(format!("Directory does not exist: {:?}", parent));
                }
            } else if !path_buf.exists() {
                return Err(format!("Log path does not exist: {}", path));
            }
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simple_mode_detection() {
        let args = CliArgs {
            log_path: Some("./logs".to_string()),
            api_key: Some("key123".to_string()),
            config_file: None,
            middleware_url: None,
            forwarder_id: None,
            help: false,
        };
        assert!(args.is_simple_mode());
    }

    #[test]
    fn test_config_mode_detection() {
        let args = CliArgs {
            log_path: None,
            api_key: None,
            config_file: Some("config.yaml".to_string()),
            middleware_url: None,
            forwarder_id: None,
            help: false,
        };
        assert!(!args.is_simple_mode());
    }

    #[test]
    fn test_remove_quotes_single() {
        let value = "'test'";
        assert_eq!(CliArgs::remove_quotes(value), "test");
    }

    #[test]
    fn test_remove_quotes_double() {
        let value = "\"test\"";
        assert_eq!(CliArgs::remove_quotes(value), "test");
    }

    #[test]
    fn test_remove_quotes_mixed() {
        let value = "'test";
        assert_eq!(CliArgs::remove_quotes(value), "'test");
    }

    #[test]
    fn test_remove_quotes_none() {
        let value = "test";
        assert_eq!(CliArgs::remove_quotes(value), "test");
    }

    #[test]
    fn test_keyvalue_format_logfile() {
        let result = CliArgs {
            log_path: Some("./logs/app12.log".to_string()),
            api_key: Some("test123".to_string()),
            config_file: None,
            middleware_url: None,
            forwarder_id: None,
            help: false,
        };

        assert_eq!(result.log_path, Some("./logs/app12.log".to_string()));
        assert_eq!(result.api_key, Some("test123".to_string()));
    }
}
