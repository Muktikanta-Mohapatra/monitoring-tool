package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message object for WebSocket acknowledgment responses.
 *
 * <p><b>Purpose:</b> Represents an acknowledgment of a client request
 * with status and optional processed count information.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AckMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String originalRequestId;
    private String status;
    private Long processedCount;
    private String message;
}
