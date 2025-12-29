CREATE SCHEMA IF NOT EXISTS logforwarder;
SET search_path TO logforwarder;

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(256) NOT NULL UNIQUE,
    password TEXT NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
    is_active BOOLEAN DEFAULT true,
    is_locked BOOLEAN DEFAULT false,
    last_login TIMESTAMP,
    last_password_change TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    mfa_enabled BOOLEAN DEFAULT false,
    mfa_secret VARCHAR(255),
    permissions TEXT,
    preferences TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_is_active ON users (is_active);

-- Default admin user (username: admin, password: admin)
INSERT INTO users (id, username, email, password, first_name, last_name, role, is_active, is_locked, failed_login_attempts, mfa_enabled, created_at)
VALUES (
    1,
    'admin',
    'admin@logforwarder.local',
    '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi',
    'System',
    'Administrator',
    'ADMIN',
    true,
    false,
    0,
    false,
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING;

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    action VARCHAR(64),
    resource_type VARCHAR(64),
    resource_id VARCHAR(256),
    description TEXT,
    old_value TEXT,
    new_value TEXT,
    status VARCHAR(32) DEFAULT 'SUCCESS',
    ip_address VARCHAR(45),
    user_agent TEXT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs (resource_type);
CREATE INDEX idx_audit_logs_status ON audit_logs (status);

CREATE TABLE alert_rules (
    id BIGINT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    enabled BOOLEAN DEFAULT true,
    condition_type VARCHAR(64) NOT NULL,
    condition_json TEXT NOT NULL,
    severity VARCHAR(32) DEFAULT 'MEDIUM',
    notifications_json TEXT,
    notification_channels TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_alert_rules_enabled ON alert_rules (enabled);
CREATE INDEX idx_alert_rules_created_by ON alert_rules (created_by);

CREATE TABLE alerts (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT,
    rule_name VARCHAR(256),
    status VARCHAR(32),
    severity VARCHAR(32),
    title TEXT,
    description TEXT,
    query TEXT,
    threshold_value DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    triggered_at TIMESTAMP,
    resolved_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    triggered_by_event_count INTEGER,
    condition TEXT,
    actions TEXT,
    metadata TEXT,
    notification_sent BOOLEAN DEFAULT false,
    notification_timestamp TIMESTAMP,
    trigger_message TEXT,
    resolution_message TEXT,
    notification_channel VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_alerts_severity ON alerts (severity);
CREATE INDEX idx_alerts_triggered_at ON alerts (triggered_at DESC);
CREATE INDEX idx_alerts_rule_id ON alerts (rule_id);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    alert_id BIGINT,
    channel VARCHAR(64) NOT NULL,
    recipient VARCHAR(256) NOT NULL,
    subject VARCHAR(512),
    body TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_alert_id ON notifications (alert_id);
CREATE INDEX idx_notifications_status ON notifications (status);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

CREATE TABLE forwarder_api_keys (
    id BIGINT PRIMARY KEY,
    forwarder_id VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(256) NOT NULL UNIQUE,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_forwarder_api_keys_forwarder_id ON forwarder_api_keys (forwarder_id);
CREATE INDEX idx_forwarder_api_keys_api_key_hash ON forwarder_api_keys (api_key_hash);
CREATE INDEX idx_forwarder_api_keys_status ON forwarder_api_keys (status);
CREATE INDEX idx_forwarder_api_keys_created_at ON forwarder_api_keys (created_at DESC);

CREATE TABLE forwarders (
    id BIGINT PRIMARY KEY,
    forwarder_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    ip VARCHAR(45),
    hostname VARCHAR(256),
    os VARCHAR(64),
    version VARCHAR(32),
    status VARCHAR(32),
    last_heartbeat TIMESTAMP,
    started_at TIMESTAMP,
    uptime_seconds BIGINT,
    events_processed BIGINT DEFAULT 0,
    events_dropped BIGINT DEFAULT 0,
    bytes_processed BIGINT DEFAULT 0,
    cpu_usage_percent DOUBLE PRECISION,
    memory_usage_percent DOUBLE PRECISION,
    memory_mb INTEGER,
    queue_depth INTEGER,
    open_files INTEGER,
    max_queue_size_mb INTEGER,
    batch_timeout_ms INTEGER,
    compression_enabled BOOLEAN DEFAULT false,
    compression_level INTEGER,
    checkpointing_enabled BOOLEAN DEFAULT false,
    checkpointing_interval_ms INTEGER,
    inputs_count INTEGER DEFAULT 0,
    outputs_count INTEGER DEFAULT 0,
    configuration TEXT,
    health_status VARCHAR(32),
    health_checks TEXT,
    recent_errors TEXT,
    api_key VARCHAR(256),
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_forwarder_id ON forwarders (forwarder_id);
CREATE INDEX idx_forwarder_status ON forwarders (status);
CREATE INDEX idx_last_heartbeat ON forwarders (last_heartbeat DESC);

CREATE TABLE checkpoints (
    id BIGINT PRIMARY KEY,
    forwarder_id VARCHAR(100) NOT NULL,
    source_id INTEGER NOT NULL,
    source_name VARCHAR(256),
    file_path TEXT,
    file_offset BIGINT,
    file_size BIGINT,
    inode BIGINT,
    last_update TIMESTAMP,
    line_count BIGINT DEFAULT 0,
    bytes_read BIGINT DEFAULT 0,
    last_modified TIMESTAMP,
    rotation_detected BOOLEAN DEFAULT false,
    rotation_timestamp TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_checkpoints_forwarder_id_source_id ON checkpoints (forwarder_id, source_id);
CREATE INDEX idx_checkpoints_last_update ON checkpoints (last_update DESC);

GRANT ALL PRIVILEGES ON SCHEMA logforwarder TO logforwarder;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA logforwarder TO logforwarder;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA logforwarder TO logforwarder;
