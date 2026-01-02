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
