CREATE DATABASE IF NOT EXISTS logforwarder;

-- =============================================================================
-- ALL TABLES (ClickHouse)
-- =============================================================================
-- All data is stored in ClickHouse including transactional tables
-- =============================================================================

-- =============================================================================
-- TRANSACTIONAL TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS logforwarder.users (
    id UInt64,
    username String,
    email String,
    password String,
    first_name Nullable(String),
    last_name Nullable(String),
    role String DEFAULT 'VIEWER',
    is_active UInt8 DEFAULT 1,
    is_locked UInt8 DEFAULT 0,
    last_login Nullable(DateTime64(3)),
    last_password_change Nullable(DateTime64(3)),
    failed_login_attempts UInt32 DEFAULT 0,
    mfa_enabled UInt8 DEFAULT 0,
    mfa_secret Nullable(String),
    permissions Nullable(String),
    preferences Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    version UInt32 DEFAULT 1,
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.forwarders (
    id UInt64,
    forwarder_id String,
    name String,
    ip Nullable(String),
    hostname Nullable(String),
    os Nullable(String),
    version Nullable(String),
    status Nullable(String),
    last_heartbeat Nullable(DateTime64(3)),
    started_at Nullable(DateTime64(3)),
    uptime_seconds Nullable(UInt64),
    events_processed UInt64 DEFAULT 0,
    events_dropped UInt64 DEFAULT 0,
    bytes_processed UInt64 DEFAULT 0,
    cpu_usage_percent Nullable(Float64),
    memory_usage_percent Nullable(Float64),
    memory_mb Nullable(UInt32),
    queue_depth Nullable(UInt32),
    open_files Nullable(UInt32),
    max_queue_size_mb Nullable(UInt32),
    batch_timeout_ms Nullable(UInt32),
    compression_enabled UInt8 DEFAULT 0,
    compression_level Nullable(UInt32),
    checkpointing_enabled UInt8 DEFAULT 0,
    checkpointing_interval_ms Nullable(UInt32),
    inputs_count UInt32 DEFAULT 0,
    outputs_count UInt32 DEFAULT 0,
    configuration Nullable(String),
    health_status Nullable(String),
    health_checks Nullable(String),
    recent_errors Nullable(String),
    api_key Nullable(String),
    enabled UInt8 DEFAULT 1,
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.forwarder_api_keys (
    id UInt64,
    forwarder_id String,
    api_key_hash String,
    description Nullable(String),
    status String DEFAULT 'ACTIVE',
    last_used_at Nullable(DateTime64(3)),
    expires_at Nullable(DateTime64(3)),
    created_by Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.alerts (
    id UInt64,
    rule_id Nullable(UInt64),
    rule_name Nullable(String),
    status String DEFAULT 'ACTIVE',
    severity Nullable(String),
    title Nullable(String),
    description Nullable(String),
    query Nullable(String),
    threshold_value Nullable(Float64),
    current_value Nullable(Float64),
    triggered_at Nullable(DateTime64(3)),
    resolved_at Nullable(DateTime64(3)),
    acknowledged_at Nullable(DateTime64(3)),
    acknowledged_by Nullable(String),
    triggered_by_event_count Nullable(UInt32),
    condition Nullable(String),
    actions Nullable(String),
    metadata Nullable(String),
    notification_sent UInt8 DEFAULT 0,
    notification_timestamp Nullable(DateTime64(3)),
    trigger_message Nullable(String),
    resolution_message Nullable(String),
    notification_channel Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.alert_rules (
    id UInt64,
    name String,
    description Nullable(String),
    enabled UInt8 DEFAULT 1,
    condition_type String,
    condition_json String,
    severity String DEFAULT 'MEDIUM',
    notifications_json Nullable(String),
    notification_channels Nullable(String),
    created_by Nullable(UInt64),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.checkpoints (
    id UInt64,
    forwarder_id String,
    source_id UInt32,
    source_name Nullable(String),
    file_path Nullable(String),
    file_offset Nullable(UInt64),
    file_size Nullable(UInt64),
    inode Nullable(UInt64),
    last_update Nullable(DateTime64(3)),
    line_count UInt64 DEFAULT 0,
    bytes_read UInt64 DEFAULT 0,
    last_modified Nullable(DateTime64(3)),
    rotation_detected UInt8 DEFAULT 0,
    rotation_timestamp Nullable(DateTime64(3)),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS logforwarder.notifications (
    id UInt64,
    alert_id Nullable(UInt64),
    channel String,
    recipient String,
    subject Nullable(String),
    body Nullable(String),
    status String DEFAULT 'PENDING',
    sent_at Nullable(DateTime64(3)),
    error_message Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- =============================================================================
-- ANALYTICAL TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS logforwarder.events (
    id UInt64,
    timestamp DateTime64(3),
    source_id UInt32,
    source_name Nullable(String),
    sourcetype Nullable(String),
    raw_data Nullable(String),
    raw_message Nullable(String),
    severity Nullable(String),
    host_id Nullable(UInt32),
    index_id Nullable(UInt16),
    batch_id Nullable(UInt64),
    parsed_fields Nullable(String),
    enriched_fields Nullable(String),
    indexed_fields Nullable(String),
    parse_duration_us Nullable(UInt64),
    detected_format Nullable(String),
    elasticsearch_id Nullable(String),
    is_indexed UInt8 DEFAULT 0,
    is_enriched UInt8 DEFAULT 0,
    forwarder_id Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3),
    updated_at Nullable(DateTime64(3)),
    _version UInt32 DEFAULT 1
) ENGINE = ReplacingMergeTree(_version)
PARTITION BY toYYYYMM(timestamp)
PRIMARY KEY (id)
ORDER BY (id, timestamp, source_id)
TTL toDateTime(timestamp) + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;



CREATE TABLE IF NOT EXISTS logforwarder.audit_logs (
    id UInt64,
    user_id Nullable(UInt64),
    username Nullable(String),
    action String,
    resource_type String,
    resource_id Nullable(String),
    description Nullable(String),
    old_value Nullable(String),
    new_value Nullable(String),
    status String DEFAULT 'SUCCESS',
    ip_address Nullable(String),
    user_agent Nullable(String),
    details Nullable(String),
    created_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (created_at, id)
TTL toDateTime(created_at) + INTERVAL 365 DAY;



CREATE TABLE IF NOT EXISTS logforwarder.forwarder_metrics (
    id UInt64,
    forwarder_id UInt64,
    cpu_usage Nullable(Float64),
    memory_usage Nullable(Float64),
    memory_total Nullable(UInt64),
    thread_count Nullable(UInt32),
    gc_count Nullable(UInt32),
    gc_time Nullable(UInt64),
    events_per_second Nullable(Float64),
    average_latency_ms Nullable(Float64),
    network_in_rate Nullable(UInt64),
    network_out_rate Nullable(UInt64),
    disk_usage_percent Nullable(Float64),
    uptime_seconds Nullable(UInt64),
    collected_at DateTime64(3),
    created_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(collected_at)
ORDER BY (collected_at, forwarder_id, id)
TTL toDateTime(collected_at) + INTERVAL 30 DAY;

-- =============================================================================
-- SEED DATA
-- =============================================================================

INSERT INTO logforwarder.users (id, username, email, password, first_name, last_name, role, is_active, created_at)
VALUES 
(1, 'admin', 'admin@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Admin', 'User', 'ADMIN', 1, now64(3)),
(2, 'operator', 'operator@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Operator', 'User', 'OPERATOR', 1, now64(3)),
(3, 'viewer', 'viewer@logforwarder.local', '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi', 'Viewer', 'User', 'VIEWER', 1, now64(3));

INSERT INTO logforwarder.forwarder_api_keys (id, forwarder_id, api_key_hash, description, status, created_by, created_at)
VALUES 
(1, 'forwarder-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Default forwarder API key', 'ACTIVE', 'admin', now64(3)),
(2, 'forwarder-admin-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Admin forwarder API key', 'ACTIVE', 'admin', now64(3)),
(3, 'forwarder-operator-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Operator forwarder API key', 'ACTIVE', 'operator', now64(3)),
(4, 'forwarder-viewer-001', '$2a$12$pV9KGDtBpKBwO8lK0jtha.mxtan7vCPnL91THZirU43vwhphpH4Ki', 'Viewer forwarder API key', 'ACTIVE', 'viewer', now64(3));

INSERT INTO logforwarder.forwarders (id, forwarder_id, name, ip, hostname, os, version, status, last_heartbeat, started_at, uptime_seconds, events_processed, events_dropped, bytes_processed, cpu_usage_percent, memory_usage_percent, health_status, enabled, created_at)
VALUES 
(1, 'forwarder-001', 'Primary Log Forwarder', '192.168.1.10', 'log-forwarder-01', 'Linux', '1.0.0', 'ACTIVE', now64(3), now64(3), 86400, 150000, 50, 2048000000, 15.5, 42.3, 'healthy', 1, now64(3)),
(2, 'forwarder-admin-001', 'Admin Forwarder', '192.168.1.11', 'log-forwarder-02', 'Linux', '1.0.0', 'ACTIVE', now64(3), now64(3), 72000, 85000, 25, 1024000000, 12.2, 38.7, 'healthy', 1, now64(3)),
(3, 'forwarder-operator-001', 'Operator Forwarder', '192.168.1.12', 'log-forwarder-03', 'Windows', '1.0.0', 'WARNING', now64(3), now64(3), 43200, 45000, 150, 512000000, 45.8, 72.1, 'degraded', 1, now64(3)),
(4, 'forwarder-viewer-001', 'Viewer Forwarder', '192.168.1.13', 'log-forwarder-04', 'Linux', '1.0.0', 'INACTIVE', now64(3), now64(3), 0, 0, 0, 0, 0.0, 0.0, 'offline', 0, now64(3));
