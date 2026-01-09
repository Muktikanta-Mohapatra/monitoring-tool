package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Message object for WebSocket error responses.
 *
 * <p><b>Purpose:</b> Represents an error condition in WebSocket communication
 * with error code, message, severity, and optional details.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String errorCode;
    private String errorMessage;
    private String severity;
    private Map<String, Object> details;
    private String originalRequestId;
    private String context;
}
