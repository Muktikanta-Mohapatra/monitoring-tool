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
