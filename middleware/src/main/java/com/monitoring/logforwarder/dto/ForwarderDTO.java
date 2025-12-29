package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for log forwarder information.
 *
 * <p><b>Purpose:</b> Represents a log forwarder agent including its registration details,
 * connection status, resource usage, and configuration settings.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwarderDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    private String hostname;
    private String ipAddress;
    private String version;
    private String status;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long totalEventsProcessed;
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Integer queueDepth;
    private Long processId;
    private String apiKey;
    private Boolean enabled;
    private String description;
    private String configPath;
    private Integer maxBatchSize;
    private Integer batchTimeoutMs;
}
