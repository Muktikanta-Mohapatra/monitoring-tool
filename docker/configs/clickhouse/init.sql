-- =============================================================================
-- LOGFORWARDER DATABASE INITIALIZATION SCRIPT
-- =============================================================================
-- This script initializes the ClickHouse database schema for LogForwarder.
-- It creates all necessary tables for storing logs, metrics, and configuration.
--
-- Table Categories:
-- 1. TRANSACTIONAL TABLES: Users, Forwarders, API Keys, Alerts, etc.
--    - Use ReplacingMergeTree for ACID-like behavior
-- 2. ANALYTICAL TABLES: Events, Audit Logs, Metrics
--    - Use MergeTree with partitioning for time-series data
--
-- Note: All timestamps use DateTime64(3) for millisecond precision
-- =============================================================================

-- Create the main database
CREATE DATABASE IF NOT EXISTS logforwarder;

-- =============================================================================
-- TRANSACTIONAL TABLES
-- =============================================================================
-- These tables store configuration and state data that requires update semantics.
-- ReplacingMergeTree engine handles deduplication based on _version column.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- USERS TABLE
-- -----------------------------------------------------------------------------
-- Stores user accounts with authentication and authorization data
-- Password stored as bcrypt hash (12 rounds)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.users (
    id UInt64,                                              -- Primary identifier
    username String,                                        -- Unique login name
    email String,                                           -- Unique email address
    password String,                                        -- bcrypt hashed password
    first_name Nullable(String),                            -- User's first name
    last_name Nullable(String),                             -- User's last name
    role String DEFAULT 'VIEWER',                           -- ADMIN, OPERATOR, VIEWER
    is_active UInt8 DEFAULT 1,                              -- Account enabled flag
    is_locked UInt8 DEFAULT 0,                              -- Account locked flag
    last_login Nullable(DateTime64(3)),                     -- Last successful login
    last_password_change Nullable(DateTime64(3)),           -- Password change timestamp
    failed_login_attempts UInt32 DEFAULT 0,                 -- Failed login counter
    mfa_enabled UInt8 DEFAULT 0,                            -- Multi-factor auth flag
    mfa_secret Nullable(String),                            -- TOTP secret key
    permissions Nullable(String),                           -- JSON permissions array
    preferences Nullable(String),                           -- JSON user preferences
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    version UInt32 DEFAULT 1,                               -- Optimistic locking
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- FORWARDERS TABLE
-- -----------------------------------------------------------------------------
-- Stores log forwarder agent registration and status information
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.forwarders (
    id UInt64,                                              -- Primary identifier
    forwarder_id String,                                    -- Unique forwarder UUID
    name String,                                            -- Display name
    ip Nullable(String),                                    -- IP address
    hostname Nullable(String),                              -- Hostname
    os Nullable(String),                                    -- Operating system
    version Nullable(String),                               -- Agent version
    status Nullable(String),                                -- ACTIVE, INACTIVE, WARNING
    last_heartbeat Nullable(DateTime64(3)),                 -- Last heartbeat timestamp
    started_at Nullable(DateTime64(3)),                     -- Agent start time
    uptime_seconds Nullable(UInt64),                        -- Uptime in seconds
    events_processed UInt64 DEFAULT 0,                      -- Total events processed
    events_dropped UInt64 DEFAULT 0,                        -- Total events dropped
    bytes_processed UInt64 DEFAULT 0,                       -- Total bytes processed
    cpu_usage_percent Nullable(Float64),                    -- Current CPU usage
    memory_usage_percent Nullable(Float64),                 -- Current memory usage
    memory_mb Nullable(UInt32),                             -- Memory in MB
    queue_depth Nullable(UInt32),                           -- Current queue depth
    open_files Nullable(UInt32),                            -- Open file handles
    max_queue_size_mb Nullable(UInt32),                     -- Max queue size config
    batch_timeout_ms Nullable(UInt32),                      -- Batch timeout config
    compression_enabled UInt8 DEFAULT 0,                    -- Compression flag
    compression_level Nullable(UInt32),                     -- Compression level
    checkpointing_enabled UInt8 DEFAULT 0,                  -- Checkpointing flag
    checkpointing_interval_ms Nullable(UInt32),             -- Checkpoint interval
    inputs_count UInt32 DEFAULT 0,                          -- Number of inputs
    outputs_count UInt32 DEFAULT 0,                         -- Number of outputs
    configuration Nullable(String),                         -- JSON config
    health_status Nullable(String),                         -- healthy, degraded, offline
    health_checks Nullable(String),                         -- JSON health checks
    recent_errors Nullable(String),                         -- JSON recent errors
    api_key Nullable(String),                               -- API key for auth
    enabled UInt8 DEFAULT 1,                                -- Enabled flag
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- FORWARDER API KEYS TABLE
-- -----------------------------------------------------------------------------
-- Stores API keys for forwarder authentication
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.forwarder_api_keys (
    id UInt64,                                              -- Primary identifier
    forwarder_id String,                                    -- Associated forwarder
    api_key_hash String,                                    -- bcrypt hashed API key
    description Nullable(String),                           -- Key description
    status String DEFAULT 'ACTIVE',                         -- ACTIVE, REVOKED, EXPIRED
    last_used_at Nullable(DateTime64(3)),                   -- Last usage timestamp
    expires_at Nullable(DateTime64(3)),                     -- Expiration timestamp
    created_by Nullable(String),                            -- Creator username
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- ALERTS TABLE
-- -----------------------------------------------------------------------------
-- Stores triggered alert instances
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.alerts (
    id UInt64,                                              -- Primary identifier
    rule_id Nullable(UInt64),                               -- Source alert rule
    rule_name Nullable(String),                             -- Rule name at trigger time
    status String DEFAULT 'ACTIVE',                         -- ACTIVE, ACKNOWLEDGED, RESOLVED
    severity Nullable(String),                              -- CRITICAL, HIGH, MEDIUM, LOW
    title Nullable(String),                                 -- Alert title
    description Nullable(String),                           -- Alert description
    query Nullable(String),                                 -- Query that triggered alert
    threshold_value Nullable(Float64),                      -- Configured threshold
    current_value Nullable(Float64),                        -- Value that triggered
    triggered_at Nullable(DateTime64(3)),                   -- Trigger timestamp
    resolved_at Nullable(DateTime64(3)),                    -- Resolution timestamp
    acknowledged_at Nullable(DateTime64(3)),                -- Acknowledgement timestamp
    acknowledged_by Nullable(String),                       -- Acknowledging user
    triggered_by_event_count Nullable(UInt32),              -- Event count at trigger
    condition Nullable(String),                             -- Condition expression
    actions Nullable(String),                               -- JSON actions taken
    metadata Nullable(String),                              -- JSON metadata
    notification_sent UInt8 DEFAULT 0,                      -- Notification sent flag
    notification_timestamp Nullable(DateTime64(3)),         -- When notification sent
    trigger_message Nullable(String),                       -- Trigger message
    resolution_message Nullable(String),                    -- Resolution message
    notification_channel Nullable(String),                  -- Channel used
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- ALERT RULES TABLE
-- -----------------------------------------------------------------------------
-- Stores alert rule definitions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.alert_rules (
    id UInt64,                                              -- Primary identifier
    name String,                                            -- Rule name
    description Nullable(String),                           -- Rule description
    enabled UInt8 DEFAULT 1,                                -- Enabled flag
    condition_type String,                                  -- THRESHOLD, PATTERN, etc.
    condition_json String,                                  -- JSON condition config
    severity String DEFAULT 'MEDIUM',                       -- Default severity
    notifications_json Nullable(String),                    -- JSON notification config
    notification_channels Nullable(String),                 -- Channels list
    created_by Nullable(UInt64),                            -- Creator user ID
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- CHECKPOINTS TABLE
-- -----------------------------------------------------------------------------
-- Stores file reading checkpoints for resume capability
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.checkpoints (
    id UInt64,                                              -- Primary identifier
    forwarder_id String,                                    -- Associated forwarder
    source_id UInt32,                                       -- Source identifier
    source_name Nullable(String),                           -- Source name
    file_path Nullable(String),                             -- File being read
    file_offset Nullable(UInt64),                           -- Current byte offset
    file_size Nullable(UInt64),                             -- File size at checkpoint
    inode Nullable(UInt64),                                 -- File inode (rotation detection)
    last_update Nullable(DateTime64(3)),                    -- Checkpoint update time
    line_count UInt64 DEFAULT 0,                            -- Lines read
    bytes_read UInt64 DEFAULT 0,                            -- Bytes read
    last_modified Nullable(DateTime64(3)),                  -- File modification time
    rotation_detected UInt8 DEFAULT 0,                      -- Rotation detected flag
    rotation_timestamp Nullable(DateTime64(3)),             -- When rotation detected
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    updated_at Nullable(DateTime64(3)),                     -- Last update timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- NOTIFICATIONS TABLE
-- -----------------------------------------------------------------------------
-- Stores notification history
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.notifications (
    id UInt64,                                              -- Primary identifier
    alert_id Nullable(UInt64),                              -- Associated alert
    channel String,                                         -- EMAIL, SLACK, WEBHOOK, etc.
    recipient String,                                       -- Recipient address
    subject Nullable(String),                               -- Notification subject
    body Nullable(String),                                  -- Notification body
    status String DEFAULT 'PENDING',                        -- PENDING, SENT, FAILED
    sent_at Nullable(DateTime64(3)),                        -- Send timestamp
    error_message Nullable(String),                         -- Error if failed
    created_at DateTime64(3) DEFAULT now64(3),              -- Creation timestamp
    _version UInt32 DEFAULT 1                               -- ReplacingMergeTree version
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- =============================================================================
-- ANALYTICAL TABLES
-- =============================================================================
-- These tables store high-volume time-series data.
-- MergeTree engine with partitioning optimizes storage and queries.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- EVENTS TABLE
-- -----------------------------------------------------------------------------
-- Main log events storage - partitioned by month with TTL
-- This is the primary table for log ingestion and search
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.events (
    id UInt64,                                              -- Primary identifier
    timestamp DateTime64(3),                                -- Event timestamp
    source_id UInt32,                                       -- Source identifier
    source_name Nullable(String),                           -- Source name
    sourcetype Nullable(String),                            -- Source type (syslog, json, etc.)
    raw_data Nullable(String),                              -- Original raw data
    raw_message Nullable(String),                           -- Parsed message
    severity Nullable(String),                              -- Log severity level
    host_id Nullable(UInt32),                               -- Host identifier
    index_id Nullable(UInt16),                              -- Index identifier
    batch_id Nullable(UInt64),                              -- Batch identifier
    parsed_fields Nullable(String),                         -- JSON parsed fields
    enriched_fields Nullable(String),                       -- JSON enrichment data
    indexed_fields Nullable(String),                        -- JSON indexed fields
    parse_duration_us Nullable(UInt64),                     -- Parse time in microseconds
    detected_format Nullable(String),                       -- Auto-detected format
    elasticsearch_id Nullable(String),                      -- ES document ID
    is_indexed UInt8 DEFAULT 0,                             -- Indexed in ES flag
    is_enriched UInt8 DEFAULT 0,                            -- Enriched flag
    forwarder_id Nullable(String),                          -- Source forwarder
    created_at DateTime64(3) DEFAULT now64(3),              -- Ingestion timestamp
    updated_at Nullable(DateTime64(3)),                     -- Update timestamp
    _version UInt32 DEFAULT 1                               -- Version for updates
) ENGINE = ReplacingMergeTree(_version)
PARTITION BY toYYYYMM(timestamp)                            -- Monthly partitions
PRIMARY KEY (id)
ORDER BY (id, timestamp, source_id)
TTL toDateTime(timestamp) + INTERVAL 365 DAY                -- 1 year retention
SETTINGS index_granularity = 8192;

-- -----------------------------------------------------------------------------
-- AUDIT LOGS TABLE
-- -----------------------------------------------------------------------------
-- Stores security audit trail for compliance
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.audit_logs (
    id UInt64,                                              -- Primary identifier
    user_id Nullable(UInt64),                               -- User who performed action
    username Nullable(String),                              -- Username at action time
    action String,                                          -- Action type
    resource_type String,                                   -- Resource type affected
    resource_id Nullable(String),                           -- Resource identifier
    description Nullable(String),                           -- Action description
    old_value Nullable(String),                             -- Previous value (JSON)
    new_value Nullable(String),                             -- New value (JSON)
    status String DEFAULT 'SUCCESS',                        -- SUCCESS, FAILURE
    ip_address Nullable(String),                            -- Client IP address
    user_agent Nullable(String),                            -- Client user agent
    details Nullable(String),                               -- Additional JSON details
    created_at DateTime64(3) DEFAULT now64(3)               -- Action timestamp
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)                           -- Monthly partitions
ORDER BY (created_at, id)
TTL toDateTime(created_at) + INTERVAL 365 DAY;              -- 1 year retention

-- -----------------------------------------------------------------------------
-- FORWARDER METRICS TABLE
-- -----------------------------------------------------------------------------
-- Time-series metrics from forwarder agents
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logforwarder.forwarder_metrics (
    id UInt64,                                              -- Primary identifier
    forwarder_id UInt64,                                    -- Forwarder reference
    cpu_usage Nullable(Float64),                            -- CPU percentage
    memory_usage Nullable(Float64),                         -- Memory percentage
    memory_total Nullable(UInt64),                          -- Total memory bytes
    thread_count Nullable(UInt32),                          -- Active threads
    gc_count Nullable(UInt32),                              -- GC count (if applicable)
    gc_time Nullable(UInt64),                               -- GC time in ms
    events_per_second Nullable(Float64),                    -- Event throughput
    average_latency_ms Nullable(Float64),                   -- Processing latency
    network_in_rate Nullable(UInt64),                       -- Network in bytes/sec
    network_out_rate Nullable(UInt64),                      -- Network out bytes/sec
    disk_usage_percent Nullable(Float64),                   -- Disk usage percentage
    uptime_seconds Nullable(UInt64),                        -- Agent uptime
    collected_at DateTime64(3),                             -- Collection timestamp
    created_at DateTime64(3) DEFAULT now64(3)               -- Storage timestamp
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(collected_at)                         -- Monthly partitions
ORDER BY (collected_at, forwarder_id, id)
TTL toDateTime(collected_at) + INTERVAL 30 DAY;             -- 30 day retention

-- =============================================================================
-- SEED DATA
-- =============================================================================
-- Default data for initial setup
-- IMPORTANT: Change passwords after first login!
-- Default password for all users: admin
-- =============================================================================

-- Insert default users
-- Password hash is bcrypt of 'admin'
INSERT INTO logforwarder.users (id, username, email, password, first_name, last_name, role, is_active, created_at)
VALUES 
(1, 'admin', 'admin@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Admin', 'User', 'ADMIN', 1, now64(3)),
(2, 'operator', 'operator@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Operator', 'User', 'OPERATOR', 1, now64(3)),
(3, 'viewer', 'viewer@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Viewer', 'User', 'VIEWER', 1, now64(3));

-- Insert default API keys for forwarders
INSERT INTO logforwarder.forwarder_api_keys (id, forwarder_id, api_key_hash, description, status, created_by, created_at)
VALUES 
(1, 'forwarder-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Default forwarder API key', 'ACTIVE', 'admin', now64(3)),
(2, 'forwarder-admin-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Admin forwarder API key', 'ACTIVE', 'admin', now64(3)),
(3, 'forwarder-operator-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Operator forwarder API key', 'ACTIVE', 'operator', now64(3)),
(4, 'forwarder-viewer-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Viewer forwarder API key', 'ACTIVE', 'viewer', now64(3));

-- Insert sample forwarders
INSERT INTO logforwarder.forwarders (id, forwarder_id, name, ip, hostname, os, version, status, last_heartbeat, started_at, uptime_seconds, events_processed, events_dropped, bytes_processed, cpu_usage_percent, memory_usage_percent, health_status, enabled, created_at)
VALUES 
(1, 'forwarder-001', 'Primary Log Forwarder', '192.168.1.10', 'log-forwarder-01', 'Linux', '1.0.0', 'ACTIVE', now64(3), now64(3), 86400, 150000, 50, 2048000000, 15.5, 42.3, 'healthy', 1, now64(3)),
(2, 'forwarder-admin-001', 'Admin Forwarder', '192.168.1.11', 'log-forwarder-02', 'Linux', '1.0.0', 'ACTIVE', now64(3), now64(3), 72000, 85000, 25, 1024000000, 12.2, 38.7, 'healthy', 1, now64(3)),
(3, 'forwarder-operator-001', 'Operator Forwarder', '192.168.1.12', 'log-forwarder-03', 'Windows', '1.0.0', 'WARNING', now64(3), now64(3), 43200, 45000, 150, 512000000, 45.8, 72.1, 'degraded', 1, now64(3)),
(4, 'forwarder-viewer-001', 'Viewer Forwarder', '192.168.1.13', 'log-forwarder-04', 'Linux', '1.0.0', 'INACTIVE', now64(3), now64(3), 0, 0, 0, 0, 0.0, 0.0, 'offline', 0, now64(3));
