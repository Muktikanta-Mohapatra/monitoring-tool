package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Message object for WebSocket alert notifications.
 *
 * <p><b>Purpose:</b> Represents an alert notification broadcast to WebSocket clients
 * including alert details, severity, status, and trigger information.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
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
