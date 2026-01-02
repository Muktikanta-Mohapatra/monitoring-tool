package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

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
