-- =============================================================================
-- POSTGRESQL DATABASE INITIALIZATION SCRIPT
-- =============================================================================
-- This script initializes the PostgreSQL database schema for LogForwarder.
-- PostgreSQL is used for transactional data that requires ACID compliance.
--
-- Usage:
-- This script is automatically executed when the PostgreSQL container starts
-- with an empty data directory. It is mounted to:
-- /docker-entrypoint-initdb.d/init.sql
--
-- Tables:
-- - users: User accounts and authentication
-- - audit_logs: Security audit trail
-- - alert_rules: Alert rule definitions
-- - alerts: Triggered alert instances
-- - notifications: Notification history
-- - forwarder_api_keys: API keys for forwarder authentication
-- - forwarders: Forwarder registration and status
-- - checkpoints: File reading checkpoints
-- =============================================================================

-- =============================================================================
-- NOTE: Database and user are created automatically by Docker via environment
-- variables: POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
-- This script runs connected to the POSTGRES_DB database automatically.
-- =============================================================================

SET search_path TO public;

-- =============================================================================
-- USERS TABLE
-- =============================================================================
-- Stores user accounts with authentication and authorization data
-- Password is stored as bcrypt hash (12 rounds recommended)
-- =============================================================================
CREATE TABLE users (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    username VARCHAR(100) NOT NULL UNIQUE,              -- Login username
    email VARCHAR(256) NOT NULL UNIQUE,                 -- Email address
    password TEXT NOT NULL,                             -- bcrypt hashed password
    first_name VARCHAR(100),                            -- User's first name
    last_name VARCHAR(100),                             -- User's last name
    role VARCHAR(32) NOT NULL DEFAULT 'VIEWER',         -- Role: ADMIN, OPERATOR, VIEWER
    is_active BOOLEAN DEFAULT true,                     -- Account active flag
    is_locked BOOLEAN DEFAULT false,                    -- Account locked flag
    last_login TIMESTAMP,                               -- Last successful login
    last_password_change TIMESTAMP,                     -- Password change timestamp
    failed_login_attempts INTEGER DEFAULT 0,            -- Failed login counter
    mfa_enabled BOOLEAN DEFAULT false,                  -- MFA enabled flag
    mfa_secret VARCHAR(255),                            -- TOTP secret key
    permissions TEXT,                                   -- JSON permissions array
    preferences TEXT,                                   -- JSON user preferences
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_is_active ON users (is_active);

-- Default admin user (username: admin, password: admin)
-- IMPORTANT: Change this password after first login!
INSERT INTO users (id, username, email, password, first_name, last_name, role, is_active, is_locked, failed_login_attempts, mfa_enabled, created_at)
VALUES (
    1,
    'admin',
    'admin@logforwarder.local',
    '$2a$12$bqAhNbnw6K3dGPr5vTKvP.qG/C4O4pKcG3vBCPZvCCZc98j0LVcMi',  -- 'admin'
    'System',
    'Administrator',
    'ADMIN',
    true,
    false,
    0,
    false,
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING;

-- =============================================================================
-- AUDIT LOGS TABLE
-- =============================================================================
-- Stores security audit trail for compliance and forensics
-- Records all significant user actions
-- =============================================================================
CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    user_id BIGINT,                                     -- User who performed action
    username VARCHAR(100),                              -- Username at action time
    action VARCHAR(64),                                 -- Action type (CREATE, UPDATE, DELETE, LOGIN, etc.)
    resource_type VARCHAR(64),                          -- Resource type (USER, FORWARDER, ALERT, etc.)
    resource_id VARCHAR(256),                           -- Resource identifier
    description TEXT,                                   -- Human-readable description
    old_value TEXT,                                     -- Previous value (JSON)
    new_value TEXT,                                     -- New value (JSON)
    status VARCHAR(32) DEFAULT 'SUCCESS',               -- SUCCESS or FAILURE
    ip_address VARCHAR(45),                             -- Client IP (supports IPv6)
    user_agent TEXT,                                    -- Client user agent
    details TEXT,                                       -- Additional JSON details
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit queries
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs (resource_type);
CREATE INDEX idx_audit_logs_status ON audit_logs (status);

-- =============================================================================
-- ALERT RULES TABLE
-- =============================================================================
-- Stores alert rule definitions for automated monitoring
-- =============================================================================
CREATE TABLE alert_rules (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    name VARCHAR(256) NOT NULL,                         -- Rule name
    description TEXT,                                   -- Rule description
    enabled BOOLEAN DEFAULT true,                       -- Rule enabled flag
    condition_type VARCHAR(64) NOT NULL,                -- Condition type (THRESHOLD, PATTERN, ANOMALY)
    condition_json TEXT NOT NULL,                       -- JSON condition configuration
    severity VARCHAR(32) DEFAULT 'MEDIUM',              -- Default: CRITICAL, HIGH, MEDIUM, LOW, INFO
    notifications_json TEXT,                            -- JSON notification configuration
    notification_channels TEXT,                         -- Comma-separated channels
    created_by BIGINT,                                  -- Creator user ID
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for alert rule queries
CREATE INDEX idx_alert_rules_enabled ON alert_rules (enabled);
CREATE INDEX idx_alert_rules_created_by ON alert_rules (created_by);

-- =============================================================================
-- ALERTS TABLE
-- =============================================================================
-- Stores triggered alert instances
-- =============================================================================
CREATE TABLE alerts (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    rule_id BIGINT,                                     -- Source alert rule
    rule_name VARCHAR(256),                             -- Rule name at trigger time
    status VARCHAR(32),                                 -- ACTIVE, ACKNOWLEDGED, RESOLVED
    severity VARCHAR(32),                               -- CRITICAL, HIGH, MEDIUM, LOW, INFO
    title TEXT,                                         -- Alert title
    description TEXT,                                   -- Alert description
    query TEXT,                                         -- Query that triggered alert
    threshold_value DOUBLE PRECISION,                   -- Configured threshold
    current_value DOUBLE PRECISION,                     -- Value that triggered
    triggered_at TIMESTAMP,                             -- When alert was triggered
    resolved_at TIMESTAMP,                              -- When alert was resolved
    acknowledged_at TIMESTAMP,                          -- When alert was acknowledged
    acknowledged_by VARCHAR(100),                       -- User who acknowledged
    triggered_by_event_count INTEGER,                   -- Event count at trigger
    condition TEXT,                                     -- Condition expression
    actions TEXT,                                       -- JSON actions taken
    metadata TEXT,                                      -- JSON metadata
    notification_sent BOOLEAN DEFAULT false,            -- Notification sent flag
    notification_timestamp TIMESTAMP,                   -- When notification sent
    trigger_message TEXT,                               -- Trigger message
    resolution_message TEXT,                            -- Resolution message
    notification_channel VARCHAR(64),                   -- Channel used
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for alert queries
CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_alerts_severity ON alerts (severity);
CREATE INDEX idx_alerts_triggered_at ON alerts (triggered_at DESC);
CREATE INDEX idx_alerts_rule_id ON alerts (rule_id);

-- =============================================================================
-- NOTIFICATIONS TABLE
-- =============================================================================
-- Stores notification history
-- =============================================================================
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    alert_id BIGINT,                                    -- Associated alert
    channel VARCHAR(64) NOT NULL,                       -- EMAIL, SLACK, WEBHOOK, PAGERDUTY
    recipient VARCHAR(256) NOT NULL,                    -- Recipient address/endpoint
    subject VARCHAR(512),                               -- Notification subject
    body TEXT,                                          -- Notification body
    status VARCHAR(32) DEFAULT 'PENDING',               -- PENDING, SENT, FAILED
    sent_at TIMESTAMP,                                  -- When notification was sent
    error_message TEXT,                                 -- Error message if failed
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for notification queries
CREATE INDEX idx_notifications_alert_id ON notifications (alert_id);
CREATE INDEX idx_notifications_status ON notifications (status);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

-- =============================================================================
-- FORWARDER API KEYS TABLE
-- =============================================================================
-- Stores API keys for forwarder authentication
-- =============================================================================
CREATE TABLE forwarder_api_keys (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    forwarder_id VARCHAR(100) NOT NULL,                 -- Associated forwarder ID
    api_key_hash VARCHAR(256) NOT NULL UNIQUE,          -- bcrypt hashed API key
    description VARCHAR(512),                           -- Key description
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE, REVOKED, EXPIRED
    last_used_at TIMESTAMP,                             -- Last usage timestamp
    expires_at TIMESTAMP,                               -- Expiration timestamp (NULL = no expiry)
    created_by VARCHAR(100),                            -- Creator username
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for API key queries
CREATE INDEX idx_forwarder_api_keys_forwarder_id ON forwarder_api_keys (forwarder_id);
CREATE INDEX idx_forwarder_api_keys_api_key_hash ON forwarder_api_keys (api_key_hash);
CREATE INDEX idx_forwarder_api_keys_status ON forwarder_api_keys (status);
CREATE INDEX idx_forwarder_api_keys_created_at ON forwarder_api_keys (created_at DESC);

-- =============================================================================
-- FORWARDERS TABLE
-- =============================================================================
-- Stores log forwarder agent registration and status
-- =============================================================================
CREATE TABLE forwarders (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    forwarder_id VARCHAR(100) NOT NULL UNIQUE,          -- Forwarder UUID
    name VARCHAR(256) NOT NULL,                         -- Display name
    ip VARCHAR(45),                                     -- IP address (supports IPv6)
    hostname VARCHAR(256),                              -- Hostname
    os VARCHAR(64),                                     -- Operating system
    version VARCHAR(32),                                -- Agent version
    status VARCHAR(32),                                 -- ACTIVE, INACTIVE, WARNING, ERROR
    last_heartbeat TIMESTAMP,                           -- Last heartbeat timestamp
    started_at TIMESTAMP,                               -- Agent start time
    uptime_seconds BIGINT,                              -- Uptime in seconds
    events_processed BIGINT DEFAULT 0,                  -- Total events processed
    events_dropped BIGINT DEFAULT 0,                    -- Total events dropped
    bytes_processed BIGINT DEFAULT 0,                   -- Total bytes processed
    cpu_usage_percent DOUBLE PRECISION,                 -- Current CPU usage %
    memory_usage_percent DOUBLE PRECISION,              -- Current memory usage %
    memory_mb INTEGER,                                  -- Memory in MB
    queue_depth INTEGER,                                -- Current queue depth
    open_files INTEGER,                                 -- Open file handles
    max_queue_size_mb INTEGER,                          -- Max queue size config
    batch_timeout_ms INTEGER,                           -- Batch timeout config
    compression_enabled BOOLEAN DEFAULT false,          -- Compression enabled
    compression_level INTEGER,                          -- Compression level (1-9)
    checkpointing_enabled BOOLEAN DEFAULT false,        -- Checkpointing enabled
    checkpointing_interval_ms INTEGER,                  -- Checkpoint interval
    inputs_count INTEGER DEFAULT 0,                     -- Number of input sources
    outputs_count INTEGER DEFAULT 0,                    -- Number of output destinations
    configuration TEXT,                                 -- JSON full configuration
    health_status VARCHAR(32),                          -- healthy, degraded, offline
    health_checks TEXT,                                 -- JSON health check results
    recent_errors TEXT,                                 -- JSON recent errors
    api_key VARCHAR(256),                               -- API key (deprecated, use forwarder_api_keys)
    enabled BOOLEAN DEFAULT true,                       -- Enabled flag
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for forwarder queries
CREATE UNIQUE INDEX idx_forwarder_id ON forwarders (forwarder_id);
CREATE INDEX idx_forwarder_status ON forwarders (status);
CREATE INDEX idx_last_heartbeat ON forwarders (last_heartbeat DESC);

-- =============================================================================
-- CHECKPOINTS TABLE
-- =============================================================================
-- Stores file reading checkpoints for reliable log collection
-- Enables resume after restart and rotation detection
-- =============================================================================
CREATE TABLE checkpoints (
    id BIGINT PRIMARY KEY,                              -- Unique identifier
    forwarder_id VARCHAR(100) NOT NULL,                 -- Associated forwarder
    source_id INTEGER NOT NULL,                         -- Source identifier
    source_name VARCHAR(256),                           -- Source display name
    file_path TEXT,                                     -- Full file path
    file_offset BIGINT,                                 -- Current byte offset
    file_size BIGINT,                                   -- File size at checkpoint
    inode BIGINT,                                       -- File inode (rotation detection)
    last_update TIMESTAMP,                              -- Last checkpoint update
    line_count BIGINT DEFAULT 0,                        -- Lines read
    bytes_read BIGINT DEFAULT 0,                        -- Bytes read
    last_modified TIMESTAMP,                            -- File modification time
    rotation_detected BOOLEAN DEFAULT false,            -- Rotation detected flag
    rotation_timestamp TIMESTAMP,                       -- When rotation detected
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes for checkpoint queries
CREATE UNIQUE INDEX idx_checkpoints_forwarder_id_source_id ON checkpoints (forwarder_id, source_id);
CREATE INDEX idx_checkpoints_last_update ON checkpoints (last_update DESC);

-- =============================================================================
-- GRANT PERMISSIONS
-- =============================================================================
GRANT ALL PRIVILEGES ON SCHEMA public TO logforwarder;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO logforwarder;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO logforwarder;

