package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for forwarder performance metrics.
 *
 * <p><b>Purpose:</b> Contains detailed metrics for a forwarder including throughput,
 * resource usage, error counts, and latency measurements.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwarderMetricsDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String forwarderId;
    private LocalDateTime timestamp;
    private Long eventsProcessed;
    private Long eventsDropped;
    private Double cpuUsagePercent;
    private Double memoryUsageBytes;
    private Long totalMemoryBytes;
    private Integer queueDepth;
    private Long uptime;
    private Double throughputEventsPerSecond;
    private Double averageLatencyMs;
    private Long errorCount;
    private Integer activeConnections;
    private Long diskSpaceUsedBytes;
    private Long diskSpaceAvailableBytes;
    private Double cpuCores;
}
