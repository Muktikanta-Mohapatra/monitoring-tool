package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base message object for WebSocket communication.
 *
 * <p><b>Purpose:</b> Represents the common fields shared by all WebSocket message types
 * including type, version, request ID, and timestamp.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
}
