package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Message object for WebSocket metrics updates.
 *
 * <p><b>Purpose:</b> Represents system or forwarder metrics broadcast to WebSocket clients
 * including metric type, value, tags, and aggregated statistics.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String metricType;
    private LocalDateTime collectedAt;
    private Double value;
    private Map<String, Object> tags;
    private Map<String, Double> aggregatedMetrics;
}
