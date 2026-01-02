package com.monitoring.logforwarder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String errorCode;
    private String message;
    private String severity;
    private LocalDateTime timestamp;
    private String requestId;
    private String originalRequestId;
    private String context;
    private Map<String, Object> details;
    private List<String> errors;
    private Map<String, String> fieldErrors;

    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse of(String errorCode, String message, String severity) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .severity(severity)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse withDetails(String errorCode, String message, Map<String, Object> details) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse withFieldErrors(String errorCode, String message, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse withErrors(String errorCode, String message, List<String> errors) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse forWebSocket(String errorCode, String message, String severity, 
                                              String requestId, String originalRequestId, String context) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .severity(severity)
                .requestId(requestId)
                .originalRequestId(originalRequestId)
                .context(context)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
