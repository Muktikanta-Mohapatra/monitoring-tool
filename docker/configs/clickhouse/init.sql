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
