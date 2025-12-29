# CLI Key=Value Format Support

## Overview
The forwarder now supports a modern key=value argument format alongside the traditional positional and named arguments.

## Command Format Examples

### 1. **New Key=Value Format** (Recommended)
```bash
.\high-perf-forwarder.exe logfile='P:\Web\monitoring-tool\LogForwarder\test\generators\test\logs\app_1.log' api-key='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'
```

### 2. Traditional Positional Format
```bash
.\high-perf-forwarder.exe 'P:\logs\app.log' 'myapikey123'
```

### 3. Traditional Named Arguments Format
```bash
.\high-perf-forwarder.exe --log-path 'P:\logs\app.log' --api-key 'myapikey123'
```

### 4. Mixed Format
```bash
.\high-perf-forwarder.exe logfile='P:\logs\app.log' api-key='myapikey123' --url 'http://prod:8080/api/v1/events/batch'
```

## Key=Value Parameters

### Available Keys

| Key | Aliases | Description |
|-----|---------|-------------|
| `logfile` | `log-path`, `log_path` | Path to log files (supports wildcards) |
| `api-key` | `api_key`, `apikey` | API key for authentication |
| `config` | - | Path to YAML configuration file |
| `url` | `middleware-url` | Middleware endpoint URL |
| `forwarder-id` | `forwarder_id` | Forwarder identifier |

### Quoting Values

**Single Quotes (Recommended for Windows):**
```bash
logfile='P:\path\to\logs\*.log'
api-key='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'
```

**Double Quotes:**
```bash
logfile="C:\path\to\logs\*.log"
api-key="myapikey123"
```

**No Quotes (for simple values):**
```bash
logfile=C:\logs\app.log
api-key=simplekey123
```

## Real-World Examples

### Example 1: Monitor a single log file
```bash
.\high-perf-forwarder.exe logfile='P:\Web\monitoring-tool\LogForwarder\test\generators\test\logs\app_1.log' api-key='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'
```

### Example 2: Monitor multiple log files with wildcard
```bash
.\high-perf-forwarder.exe logfile='P:\logs\*.log' api-key='myapikey123'
```

### Example 3: Custom middleware URL
```bash
.\high-perf-forwarder.exe logfile='.\logs\app.log' api-key='mykey' url='http://prod-api:8080/api/v1/events/batch'
```

### Example 4: With forwarder ID
```bash
.\high-perf-forwarder.exe logfile='.\logs\app.log' api-key='mykey' forwarder-id='my-forwarder-01'
```

### Example 5: Using underscores instead of dashes
```bash
.\high-perf-forwarder.exe logfile='.\logs\app.log' api_key='mykey' forwarder_id='my-forwarder'
```

## Special Characters in API Keys

If your API key contains special characters (like `$`, `@`, `#`, `&`), make sure to quote it:

```bash
# ✅ Correct - quoted
api-key='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'

# ❌ Incorrect - unquoted (shell might interpret special chars)
api-key=$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u
```

## Command-Line Priority

1. **Key=Value Format** - Highest priority
2. **Named Arguments** (`--option value`) - Medium priority
3. **Positional Arguments** (`value1 value2`) - Lowest priority
4. **Environment Variables** - Used only if not provided via CLI
5. **Config File** - Used if no CLI arguments provided

## Help

Display full help message:
```bash
.\high-perf-forwarder.exe --help
```

## Implementation Details

- Values can be quoted with single (`'`) or double (`"`) quotes
- Quotes are automatically stripped during parsing
- Parameter names support dash (`-`), underscore (`_`), or no separator (dash-separated keys have aliases)
- The parser is case-sensitive
- Unknown parameters generate a warning but don't cause failure
