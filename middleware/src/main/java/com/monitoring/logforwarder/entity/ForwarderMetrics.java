package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing forwarder performance metrics.
 *
 * <p><b>Purpose:</b> Stores time-series metrics for forwarders including CPU, memory,
 * throughput, and latency measurements for performance monitoring.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: forwarder_metrics</li>
 *   <li>Used for historical trend analysis and alerting</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "forwarder_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwarderMetrics {

    @Id
    private Long id;

    @Column(name = "forwarder_id", nullable = false)
    private Long forwarderId;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "gc_count")
    private Integer gcCount;

    @Column(name = "gc_time")
    private Long gcTime;

    @Column(name = "events_per_second")
    private Double eventsPerSecond;

    @Column(name = "average_latency_ms")
    private Double averageLatencyMs;

    @Column(name = "network_in_rate")
    private Long networkInRate;

    @Column(name = "network_out_rate")
    private Long networkOutRate;

    @Column(name = "disk_usage_percent")
    private Double diskUsagePercent;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = System.currentTimeMillis() * 1000000L + System.nanoTime() % 1000000L;
        }
        createdAt = LocalDateTime.now();
        if (collectedAt == null) {
            collectedAt = LocalDateTime.now();
        }
    }
}
