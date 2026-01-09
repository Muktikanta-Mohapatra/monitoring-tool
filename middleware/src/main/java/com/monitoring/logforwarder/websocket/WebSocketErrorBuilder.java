package com.monitoring.logforwarder.websocket;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Builder utility for creating standardized WebSocket error messages.
 *
 * <p><b>Purpose:</b> Provides factory methods for creating ErrorMessage objects
 * with consistent structure for WebSocket error responses.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public final class WebSocketErrorBuilder {

    private WebSocketErrorBuilder() {
    }

    public static ErrorMessage error(String errorCode, String message) {
        return ErrorMessage.builder()
                .type("ERROR")
                .version(WebSocketMessageTypes.VERSION)
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(message)
                .severity(ErrorSeverity.ERROR)
                .build();
    }

    public static ErrorMessage error(String errorCode, String message, String severity) {
        return ErrorMessage.builder()
                .type("ERROR")
                .version(WebSocketMessageTypes.VERSION)
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(message)
                .severity(severity)
                .build();
    }

    public static ErrorMessage error(String errorCode, String message, String severity, String requestId) {
        return ErrorMessage.builder()
                .type("ERROR")
                .version(WebSocketMessageTypes.VERSION)
                .requestId(requestId != null ? requestId : "unknown")
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(message)
                .severity(severity)
                .originalRequestId(requestId)
                .build();
    }

    public static ErrorMessage error(String errorCode, String message, String severity, 
                                      String requestId, String context) {
        return ErrorMessage.builder()
                .type("ERROR")
                .version(WebSocketMessageTypes.VERSION)
                .requestId(requestId != null ? requestId : "unknown")
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(message)
                .severity(severity)
                .originalRequestId(requestId)
                .context(context)
                .build();
    }

    public static ErrorMessage errorWithDetails(String errorCode, String message, String severity,
                                                  String requestId, Map<String, Object> details) {
        return ErrorMessage.builder()
                .type("ERROR")
                .version(WebSocketMessageTypes.VERSION)
                .requestId(requestId != null ? requestId : "unknown")
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(message)
                .severity(severity)
                .originalRequestId(requestId)
                .details(details)
                .build();
    }

    public static ErrorMessage validationError(String message, String requestId) {
        return error(ErrorCode.VALIDATION_FAILED, message, ErrorSeverity.ERROR, requestId, "Message validation");
    }

    public static ErrorMessage queryError(String message, String requestId) {
        return error(ErrorCode.QUERY_INVALID, message, ErrorSeverity.ERROR, requestId, "Query validation");
    }

    public static ErrorMessage subscriptionLimitError(int currentCount, int maxCount, String requestId) {
        return errorWithDetails(
                ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED,
                "Subscription limit exceeded: maximum " + maxCount + " active subscriptions allowed, currently at " + currentCount,
                ErrorSeverity.WARNING,
                requestId,
                Map.of(
                        "currentCount", currentCount,
                        "maxCount", maxCount,
                        "limitExceededBy", currentCount - maxCount + 1
                )
        );
    }

    public static ErrorMessage unauthorizedError(String message, String requestId) {
        return error(ErrorCode.UNAUTHORIZED, message, ErrorSeverity.ERROR, requestId);
    }

    public static ErrorMessage internalError(String message, String requestId) {
        return error(ErrorCode.INTERNAL_ERROR, message, ErrorSeverity.CRITICAL, requestId);
    }

    public static ErrorMessage sqlInjectionError(String requestId) {
        return error(ErrorCode.SQL_INJECTION_DETECTED, "Query contains potentially malicious patterns", 
                ErrorSeverity.CRITICAL, requestId, "Query validation");
    }

    public static ErrorMessage invalidMessageFormat(String message, String requestId) {
        return error(ErrorCode.INVALID_MESSAGE_FORMAT, message, ErrorSeverity.ERROR, requestId);
    }
}
