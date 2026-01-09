package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Generic WebSocket message wrapper for all message types.
 *
 * <p><b>Purpose:</b> Standard message envelope for WebSocket communication
 * containing type, action, data payload, and metadata.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type;
    private String action;
    private Object data;
    private String sender;
    private LocalDateTime timestamp;
    private String subscriptionId;
}
