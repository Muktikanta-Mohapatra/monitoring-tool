package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a log forwarder agent.
 *
 * <p><b>Purpose:</b> Persists forwarder registration, configuration, status, and
 * performance metrics. Tracks agent lifecycle from registration through decommissioning.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: forwarders</li>
 *   <li>Indexed on forwarder_id (unique), status, last_heartbeat</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "forwarders", indexes = {
    @Index(name = "idx_forwarder_id", columnList = "forwarder_id", unique = true),
    @Index(name = "idx_forwarder_status", columnList = "status"),
    @Index(name = "idx_last_heartbeat", columnList = "last_heartbeat DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Forwarder {

    @Id
    private Long id;

    @Column(name = "forwarder_id", unique = true, nullable = false, length = 100)
    private String forwarderId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "hostname", length = 256)
    private String hostname;

    @Column(name = "os", length = 64)
    private String os;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "events_processed")
    private Long eventsProcessed = 0L;

    @Column(name = "events_dropped")
    private Long eventsDropped = 0L;

    @Column(name = "bytes_processed")
    private Long bytesProcessed = 0L;

    @Column(name = "cpu_usage_percent")
    private Double cpuUsagePercent;

    @Column(name = "memory_usage_percent")
    private Double memoryUsagePercent;

    @Column(name = "memory_mb")
    private Integer memoryMb;

    @Column(name = "queue_depth")
    private Integer queueDepth;

    @Column(name = "open_files")
    private Integer openFiles;

    @Column(name = "max_queue_size_mb")
    private Integer maxQueueSizeMb;

    @Column(name = "batch_timeout_ms")
    private Integer batchTimeoutMs;

    @Column(name = "compression_enabled")
    private Boolean compressionEnabled = false;

    @Column(name = "compression_level")
    private Integer compressionLevel;

    @Column(name = "checkpointing_enabled")
    private Boolean checkpointingEnabled = false;

    @Column(name = "checkpointing_interval_ms")
    private Integer checkpointingIntervalMs;

    @Column(name = "inputs_count")
    private Integer inputsCount = 0;

    @Column(name = "outputs_count")
    private Integer outputsCount = 0;

    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;

    @Column(name = "health_status", length = 32)
    private String healthStatus;

    @Column(name = "health_checks", columnDefinition = "TEXT")
    private String healthChecks;

    @Column(name = "recent_errors", columnDefinition = "TEXT")
    private String recentErrors;

    @Column(name = "api_key", length = 256)
    private String apiKey;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = generateId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}
