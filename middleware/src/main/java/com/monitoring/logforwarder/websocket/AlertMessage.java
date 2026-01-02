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
public class AlertMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String alertId;
    private String alertName;
    private String severity;
    private String status;
    private LocalDateTime triggeredAt;
    private String message;
    private Long eventCount;
    private Map<String, Object> details;
}
