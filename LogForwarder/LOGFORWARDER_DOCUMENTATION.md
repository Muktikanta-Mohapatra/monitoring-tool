# LogForwarder - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Key Components](#key-components)
4. [Configuration](#configuration)
5. [Input Types](#input-types)
6. [Output & Protocols](#output--protocols)
7. [Data Processing Pipeline](#data-processing-pipeline)
8. [Features](#features)
9. [Testing](#testing)
10. [Running the Project](#running-the-project)
11. [API Reference](#api-reference)

---

## Overview

LogForwarder is a high-performance, Rust-based log forwarding system designed for enterprise-grade log collection, processing, and forwarding. It provides Splunk-like functionality with support for multiple input sources, real-time parsing, enrichment, masking, and efficient delivery to various outputs.

### Technology Stack
- **Language**: Rust (Edition 2021)
- **Async Runtime**: Tokio (Full features)
- **Serialization**: Serde (YAML, JSON)
- **Compression**: Zstd
- **Metrics**: Prometheus
- **gRPC**: Tonic + Prost
- **TLS**: Rustls

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           LogForwarder                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  File Input  │  │  TCP Input   │  │  UDP Input   │  │Script Input  │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘ │
│         │                  │                  │                 │        │
│         └──────────────────┴──────────────────┴─────────────────┘        │
│                                    │                                      │
│                           ┌────────▼────────┐                            │
│                           │   Event Queue   │                            │
│                           │  (Lock-Free)    │                            │
│                           └────────┬────────┘                            │
│                                    │                                      │
│         ┌──────────────────────────┼──────────────────────────┐          │
│         │                          │                          │          │
│  ┌──────▼──────┐  ┌───────────────▼──────────────┐  ┌────────▼───────┐  │
│  │Format Detect│  │     Enrichment Pipeline      │  │  Masking Engine│  │
│  │  (Parsers)  │  │  (GeoIP, Lookup, Custom)     │  │  (PII, Custom) │  │
│  └──────┬──────┘  └───────────────┬──────────────┘  └────────┬───────┘  │
│         │                          │                          │          │
│         └──────────────────────────┼──────────────────────────┘          │
│                                    │                                      │
│                           ┌────────▼────────┐                            │
│                           │  Batch Buffer   │                            │
│                           │  (Configurable) │                            │
│                           └────────┬────────┘                            │
│                                    │                                      │
│         ┌──────────────────────────┼──────────────────────────┐          │
│         │                          │                          │          │
│  ┌──────▼──────┐  ┌───────────────▼──────────────┐  ┌────────▼───────┐  │
│  │HTTP Output  │  │      gRPC Output             │  │Console Output  │  │
│  │(Splunk HEC) │  │  (Load Balanced, Circuit)    │  │   (Debug)      │  │
│  └─────────────┘  └──────────────────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### 1. Main Entry Point (`src/main.rs`)

The `Forwarder` struct is the central orchestrator that:
- Initializes configuration from YAML
- Sets up checkpoint store for resumability
- Creates event queue (lock-free ring buffer)
- Initializes output pool
- Manages graceful shutdown
- Handles hot configuration reload

**Key Functions:**
- `Forwarder::new()` - Initialize forwarder instance
- `Forwarder::run()` - Main event loop
- `spawn_input_tasks()` - Start all configured inputs
- `handle_config_reload()` - Hot reload configuration

### 2. Configuration Module (`src/config/mod.rs`)

**Config Structures:**
| Structure | Purpose |
|-----------|---------|
| `Config` | Root configuration |
| `InputConfig` | Input type enum (File, TCP, UDP, Script, WindowsEventLog) |
| `FileInputConfig` | File monitoring settings |
| `TcpInputConfig` | TCP listener settings |
| `UdpInputConfig` | UDP listener settings |
| `ScriptedInputConfig` | Script execution settings |
| `OutputConfig` | Output destination settings |
| `SystemConfig` | Queue, batch, metrics settings |
| `FormatDetectionConfig` | Parser auto-detection settings |

**Validation Levels:**
1. **Syntax** - Basic structure validation
2. **Semantic** - Logical validation (port ranges, URL formats)
3. **Runtime** - File existence checks

### 3. Event Module (`src/event/mod.rs`)

The `Event` struct represents a log entry:

```rust
pub struct Event {
    raw_data: Arc<[u8]>,        // Zero-copy data storage
    timestamp: i64,              // Nanosecond precision
    offset: u64,                 // File position
    source_id: u32,             // FNV hash of source
    sourcetype: SourceType,     // Enum (Json, Syslog, CSV, etc.)
    host_id: u32,               // Host identifier
    index_id: u16,              // Target index
    flags: u8,                  // Processing flags
    enriched_metadata: Option<Arc<EnrichedEventMetadata>>,
}
```

**Source Types:**
- `Unknown`, `ApacheAccess`, `ApacheError`, `Json`, `Syslog`, `Csv`

### 4. Queue Module (`src/queue/mod.rs`)

**Lock-Free Ring Buffer:**
- Cache-line padded for optimal performance
- Atomic operations for thread-safety
- Zero allocation during push/pop
- Memory-bounded operation

**Key Methods:**
```rust
EventQueue::new(capacity: usize, max_size: usize)
queue.push(event)       // Non-blocking push
queue.pop()             // Non-blocking pop
queue.try_pop_batch()   // Batch retrieval
queue.fill_percentage() // Backpressure metric
```

### 5. Checkpoint Module (`src/checkpoint/mod.rs`)

Memory-mapped file for crash recovery:

**Record Structure:**
```rust
pub struct CheckpointRecord {
    pub source_id_hash: u64,
    pub inode: u64,
    pub position: u64,
    pub file_size: u64,
    pub crc32: u32,
    pub last_modified: i64,
    pub last_updated: i64,
}
```

**Features:**
- 64KB bucket-based index
- O(1) lookup by source hash
- Atomic flush to disk
- Survives process crashes

### 6. Parser Module (`src/parser/`)

**Available Parsers:**

| Parser | File | Description |
|--------|------|-------------|
| `JsonParser` | `json_parser.rs` | JSON log parsing with field mapping |
| `SyslogParser` | `syslog_parser.rs` | RFC3164/RFC5424 syslog |
| `CsvParser` | `csv_parser.rs` | CSV with configurable delimiter |
| `GrokParser` | `grok_parser.rs` | Grok pattern matching |
| `RegexParser` | `regex_parser.rs` | Custom regex extraction |
| `RawParser` | `raw_parser.rs` | Passthrough parser |

**Format Detection (`format_detector.rs`):**
- LRU cache for format decisions
- Confidence-based selection (threshold configurable)
- Periodic re-evaluation
- Parallel parser execution

### 7. Enrichment Module (`src/enrichment/`)

**Enrichers:**
| Enricher | Purpose |
|----------|---------|
| `GeoIpEnricher` | IP to geo-location |
| `LookupTableEnricher` | Static lookup tables |
| `EnrichmentPipeline` | Chain multiple enrichers |

**LRU Cache:**
- Configurable size
- TTL-based expiration
- Thread-safe access

### 8. Masking Module (`src/masking/`)

**Masking Strategies:**
- `Preserve` - No masking
- `MaskFull` - Replace with asterisks
- `MaskStart` - Mask first third
- `MaskEnd` - Mask last third
- `MaskMiddle` - Mask middle portion
- `Hash` - SHA256 hash
- `Custom` - Custom replacement

**PII Patterns (`patterns.rs`):**
- Credit card detection
- SSN detection
- Email detection
- Custom regex patterns

### 9. Output Module (`src/outputs/`)

**HTTP Output (`mod.rs`):**
- Splunk HEC compatible
- Retry with exponential backoff
- Zstd compression support
- TLS verification configurable

**gRPC Output (`grpc_sender.rs`):**
- Protobuf serialization
- Connection pooling
- Load balancing (Round Robin, Weighted)
- Circuit breaker pattern

**Proto Definition (`proto/forwarder.proto`):**
```protobuf
service ForwarderService {
  rpc SendEvents(stream EventBatch) returns (stream AckResponse);
  rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);
  rpc GetCapacity(CapacityRequest) returns (CapacityResponse);
}
```

### 10. Compression Module (`src/compression/`)

**Features:**
- Zstd compression (levels 1-22)
- Adaptive compression based on CPU
- Dictionary training for better ratios
- Buffer pooling for allocation efficiency

**Configuration:**
```rust
CompressionConfig {
    level: 3,           // 1=fast, 9=max
    adaptive: true,     // Auto-adjust based on CPU
    use_dictionary: true,
    batch_size_bytes: 1MB,
}
```

### 11. Backpressure Module (`src/backpressure/mod.rs`)

**States:**
| State | Fill % | Batch Timeout | Size Multiplier |
|-------|--------|---------------|-----------------|
| Green | 0-50% | 5000ms | 1.0x |
| Yellow | 50-80% | 2000ms | 0.8x |
| Orange | 80-90% | 500ms | 0.5x |
| Red | 90%+ | 100ms | 0.25x |

### 12. Disk Monitor (`src/disk_monitor.rs`)

**Thresholds:**
- Warning: 90% used
- Critical: 95% used (throttle inputs)
- Emergency: 98% used (pause all inputs)

**Input Controller:**
- `pause_all()` - Stop all inputs
- `resume_all()` - Resume inputs
- `throttle()` / `unthrottle()` - Slow down inputs

### 13. Graceful Shutdown (`src/graceful_shutdown.rs`)

**Phases:**
1. `StoppingInputs` - Stop file watchers, close connections
2. `DrainingPipeline` - Flush remaining events
3. `SavingState` - Persist checkpoints
4. `Cleanup` - Release resources

### 14. Metrics Module (`src/metrics/`)

**Prometheus Metrics:**
```
forwarder_events_read_total{source, sourcetype}
forwarder_events_sent_total{destination}
forwarder_events_dropped_total{reason}
forwarder_bytes_read_total{source}
forwarder_bytes_sent_total{destination}
forwarder_queue_depth{queue}
forwarder_event_latency_seconds{stage}
forwarder_errors_total{component, error_type}
forwarder_files_monitored_total
forwarder_checkpoint_saves_total
```

---

## Configuration

### Sample Configuration (`config/inputs.yaml`)

```yaml
system:
  metrics:
    bind_address: "127.0.0.1"
    port: 9090
    enabled: true

  queue:
    capacity: 65536
    max_memory_mb: 50

  batch:
    size: 1000
    max_memory_mb: 10
    flush_interval_ms: 1000

  file_watcher:
    delay_ms: 500
    enabled: true

  health_check:
    interval_secs: 30
    error_threshold: 10

  config_history_path: "var/config_history"
  checkpoint_path: "var/checkpoint"

format_detection:
  enabled: true
  cache_size: 1000
  confidence_threshold: 0.8
  re_evaluation_interval: 10000

inputs:
  - type: file
    name: app_logs
    path: "/var/log/app/*.log"
    sourcetype: json
    index: main
    recursive: false
    follow_tail: true
    encoding: utf-8
    batch_size: 100
    read_buffer_kb: 128
    checkpoint_interval_ms: 5000

  - type: tcp
    name: syslog_tcp
    port: 514
    bind_address: "0.0.0.0"
    sourcetype: syslog
    index: syslog
    max_connections: 1000
    connection_timeout_secs: 300

  - type: udp
    name: syslog_udp
    port: 514
    bind_address: "0.0.0.0"
    sourcetype: syslog
    index: syslog
    max_datagram_size: 65536

  - type: script
    name: metrics_collector
    command: "/usr/local/bin/collect_metrics.sh"
    args: ["--format", "json"]
    interval_secs: 60
    timeout_secs: 30
    sourcetype: json
    index: metrics

outputs:
  - name: splunk_hec
    url: "https://splunk.example.com:8088/services/collector"
    token: "your-hec-token"
    tls_verify: true
    batch_size: 1000
    flush_interval_ms: 1000
    retry_count: 3
    compression_enabled: true
    compression_level: 3
    protocol: http

  - name: grpc_indexer
    url: "grpc://indexer.example.com:50051"
    token: "grpc-token"
    protocol: grpc

indexers:
  - id: indexer-01
    host: indexer1.example.com
    port: 50051
    weight: 100
  - id: indexer-02
    host: indexer2.example.com
    port: 50051
    weight: 100

graceful_shutdown:
  enabled: true
  timeout_secs: 30

disk_monitoring:
  enabled: true
  check_interval_secs: 60
  warning_threshold_percent: 90.0
  critical_threshold_percent: 95.0
  emergency_threshold_percent: 98.0
```

---

## Input Types

### 1. File Input
- Glob pattern support (`*.log`, `**/*.log`)
- Inode tracking for rotation handling
- Checkpoint-based resumption
- Configurable read buffer size
- Follow tail mode

### 2. TCP Input
- Multi-threaded connection handling
- Connection pooling
- BSD/RFC5424 syslog parsing
- Configurable timeouts
- Max connections limit

### 3. UDP Input
- High-throughput datagram processing
- Configurable buffer sizes
- Syslog format support

### 4. Scripted Input
- Execute external commands
- JSON/text output parsing
- Environment variable support
- Resource limits (memory, CPU)
- Timeout handling

### 5. Windows Event Log Input
- Windows Event channels
- XPath query support
- Real-time subscription

---

## Output & Protocols

### HTTP (Splunk HEC)
```
POST /services/collector HTTP/1.1
Authorization: Splunk <token>
Content-Type: application/json
Content-Encoding: zstd  (if compression enabled)
```

### gRPC
- Bidirectional streaming
- Acknowledgment-based delivery
- Health checks
- Capacity negotiation

---

## Data Processing Pipeline

```
Input → Queue → Format Detection → Parsing → Enrichment → Masking → Routing → Batching → Output
```

1. **Input**: Read from sources
2. **Queue**: Lock-free ring buffer
3. **Format Detection**: Auto-detect log format
4. **Parsing**: Extract fields
5. **Enrichment**: Add geo, lookups
6. **Masking**: Redact PII
7. **Routing**: Conditional output selection
8. **Batching**: Group events
9. **Output**: Send to destinations

---

## Features

| Feature | Description |
|---------|-------------|
| **Hot Reload** | Configuration changes without restart |
| **Checkpointing** | Resume from last position after crash |
| **Format Detection** | Auto-detect JSON, Syslog, CSV, etc. |
| **Enrichment** | GeoIP, lookup tables |
| **PII Masking** | Credit cards, SSN, emails |
| **Compression** | Zstd with adaptive levels |
| **Backpressure** | Automatic throttling under load |
| **Disk Monitoring** | Pause on low disk space |
| **Graceful Shutdown** | Complete in-flight processing |
| **Prometheus Metrics** | Full observability |
| **gRPC Streaming** | Efficient bulk transfer |
| **Load Balancing** | Multiple indexer support |
| **Circuit Breaker** | Fault tolerance |

---

## Testing

### Run Unit Tests
```bash
cd LogForwarder
cargo test
```

### Run Integration Tests
```bash
cargo test --test integration_tests
cargo test --test integration_format_detection
cargo test --test integration_log_processing
```

### Run Chaos Tests
```bash
cargo test --test chaos_tests
```

### Run Benchmarks
```bash
cargo bench
```

### Test Coverage
```bash
cargo install cargo-tarpaulin
cargo tarpaulin --out Html
```

### Manual Testing

**1. Console Output Mode:**
```bash
export FORWARDER_CONSOLE_OUTPUT=1
cargo run
```

**2. Create Test Logs:**
```bash
# Create test directory
mkdir -p test/logs

# Generate test data
for i in {1..100}; do
  echo '{"level":"INFO","message":"Test log '$i'","timestamp":"2024-01-01T00:00:00Z"}' >> test/logs/test.log
done
```

**3. TCP Input Test:**
```bash
# Start forwarder with TCP input configured
cargo run

# Send test syslog
echo "<34>Oct 11 22:14:15 mymachine su: test message" | nc localhost 514
```

**4. UDP Input Test:**
```bash
echo "<34>Oct 11 22:14:15 mymachine su: test message" | nc -u localhost 514
```

---

## Running the Project

### Prerequisites
- Rust 1.70+ (`rustup update stable`)
- Cargo
- (Optional) Docker for containerized deployment

### Development Build
```bash
cd LogForwarder
cargo build
```

### Release Build
```bash
cargo build --release
```

### Run with Default Config
```bash
cargo run
```

### Run with Custom Config
```bash
FORWARDER_CONFIG=/path/to/config.yaml cargo run
```

### Run with Custom Checkpoint Path
```bash
FORWARDER_CHECKPOINT=/path/to/checkpoint cargo run
```

### Docker Build
```bash
docker build -t logforwarder:latest .
docker run -v /var/log:/var/log -v ./config:/app/config logforwarder:latest
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FORWARDER_CONFIG` | `config/inputs.yaml` | Config file path |
| `FORWARDER_CHECKPOINT` | From config | Checkpoint directory |
| `FORWARDER_CONSOLE_OUTPUT` | Not set | Enable console output |
| `RUST_LOG` | `info` | Log level |

---

## API Reference

### Prometheus Metrics Endpoint
```
GET http://localhost:9090/metrics
```

### gRPC Services
```protobuf
// Send events
rpc SendEvents(stream EventBatch) returns (stream AckResponse);

// Health check
rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);

// Get capacity
rpc GetCapacity(CapacityRequest) returns (CapacityResponse);
```

---

## File Structure

```
LogForwarder/
├── Cargo.toml              # Dependencies
├── build.rs                # Build script
├── config/
│   ├── inputs.yaml         # Main configuration
│   ├── inputs_grpc.yaml    # gRPC configuration
│   └── inputs_http.yaml    # HTTP configuration
├── proto/
│   └── forwarder.proto     # gRPC definitions
├── src/
│   ├── main.rs             # Entry point
│   ├── lib.rs              # Module exports
│   ├── backpressure/       # Backpressure handling
│   ├── batching/           # Adaptive batching
│   ├── checkpoint/         # State persistence
│   ├── compression/        # Zstd compression
│   ├── config/             # Configuration parsing
│   ├── crc/                # CRC32 calculations
│   ├── enrichment/         # Data enrichment
│   ├── event/              # Event structures
│   ├── file_registry/      # File tracking
│   ├── graceful_shutdown.rs
│   ├── disk_monitor.rs
│   ├── file_watcher.rs
│   ├── config_diff.rs
│   ├── config_health_checker.rs
│   ├── config_history.rs
│   ├── inputs/             # Input handlers
│   │   ├── mod.rs          # FileInput
│   │   ├── tcp.rs          # TCP input
│   │   ├── udp.rs          # UDP input
│   │   ├── scripted.rs     # Script input
│   │   └── windows_eventlog.rs
│   ├── logging/            # Structured logging
│   ├── masking/            # PII masking
│   ├── metrics/            # Prometheus metrics
│   ├── monitor/            # File system monitor
│   ├── network/            # Network clients
│   ├── outputs/            # Output handlers
│   │   ├── mod.rs          # HTTP output
│   │   └── grpc_sender.rs  # gRPC output
│   ├── parser/             # Log parsers
│   ├── pattern/            # Glob patterns
│   ├── pipeline/           # Processing pipeline
│   ├── queue/              # Lock-free queue
│   ├── rotation/           # Log rotation detection
│   └── routing/            # Conditional routing
├── tests/
│   ├── integration_tests.rs
│   ├── integration_format_detection.rs
│   ├── integration_log_processing.rs
│   └── chaos_tests.rs
├── benches/
│   └── performance_benchmarks.rs
└── var/
    ├── checkpoint/         # Checkpoint storage
    └── config_history/     # Config versions
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| High memory usage | Reduce `queue.max_memory_mb` |
| Slow processing | Increase `batch.size`, enable compression |
| Missed logs after restart | Check checkpoint file integrity |
| Connection refused | Verify `bind_address` and firewall |
| High CPU | Enable adaptive compression, reduce parsers |
| Disk full | Enable disk monitoring, lower thresholds |
