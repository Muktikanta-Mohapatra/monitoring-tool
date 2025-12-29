package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
