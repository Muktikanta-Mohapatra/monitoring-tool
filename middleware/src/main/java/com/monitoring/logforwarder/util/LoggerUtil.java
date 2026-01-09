package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for structured logging with MDC context support.
 *
 * <p><b>Purpose:</b> Provides logging helper methods with MDC (Mapped Diagnostic Context)
 * support for adding contextual information to log entries.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@link #logInfo}, {@link #logDebug}, {@link #logWarn}, {@link #logError} - Level-specific logging</li>
 *   <li>{@link #addContext} - Add MDC context key-value pair</li>
 *   <li>{@link #clearContext} - Clear all MDC context</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class LoggerUtil {

    private static final Map<String, Object> MDC_CONTEXT = new ConcurrentHashMap<>();

    public static void logInfo(String message) {
        log.info(message);
    }

    public static void logInfo(String message, Object... args) {
        log.info(message, args);
    }

    public static void logDebug(String message) {
        log.debug(message);
    }

    public static void logDebug(String message, Object... args) {
        log.debug(message, args);
    }

    public static void logWarn(String message) {
        log.warn(message);
    }

    public static void logWarn(String message, Object... args) {
        log.warn(message, args);
    }

    public static void logError(String message) {
        log.error(message);
    }

    public static void logError(String message, Throwable throwable) {
        log.error(message, throwable);
    }

    public static void logError(String message, Object... args) {
        log.error(message, args);
    }

    public static void logEventIngestion(String forwarderId, int eventCount) {
        log.info("Event batch ingested - ForwarderId: {}, EventCount: {}, Timestamp: {}", 
            forwarderId, eventCount, DateFormatUtil.now());
    }

    public static void logSearchQuery(String userId, String query, long executionTimeMs) {
        log.info("Search query executed - UserId: {}, Query: {}, ExecutionTime: {}ms, Timestamp: {}", 
            userId, query, executionTimeMs, DateFormatUtil.now());
    }

    public static void logAuthenticationAttempt(String username, boolean success) {
        if (success) {
            log.info("Authentication successful - Username: {}, Timestamp: {}", 
                username, DateFormatUtil.now());
        } else {
            log.warn("Authentication failed - Username: {}, Timestamp: {}", 
                username, DateFormatUtil.now());
        }
    }

    public static void logAlertTriggered(String alertId, String severity) {
        log.warn("Alert triggered - AlertId: {}, Severity: {}, Timestamp: {}", 
            alertId, severity, DateFormatUtil.now());
    }

    public static void logForwarderStatusChange(String forwarderId, String oldStatus, String newStatus) {
        log.info("Forwarder status changed - ForwarderId: {}, OldStatus: {}, NewStatus: {}, Timestamp: {}", 
            forwarderId, oldStatus, newStatus, DateFormatUtil.now());
    }

    public static void logCacheHit(String cacheKey) {
        log.debug("Cache hit - CacheKey: {}", cacheKey);
    }

    public static void logCacheMiss(String cacheKey) {
        log.debug("Cache miss - CacheKey: {}", cacheKey);
    }

    public static void logDatabaseQuery(String operation, String entity, long executionTimeMs) {
        log.debug("Database query - Operation: {}, Entity: {}, ExecutionTime: {}ms", 
            operation, entity, executionTimeMs);
    }

    public static void logElasticsearchQuery(String index, String query, long executionTimeMs) {
        log.debug("Elasticsearch query - Index: {}, Query: {}, ExecutionTime: {}ms", 
            index, query, executionTimeMs);
    }

    public static void logNotificationSent(String channel, String recipient) {
        log.info("Notification sent - Channel: {}, Recipient: {}, Timestamp: {}", 
            channel, recipient, DateFormatUtil.now());
    }

    public static void logNotificationFailed(String channel, String recipient, String reason) {
        log.warn("Notification failed - Channel: {}, Recipient: {}, Reason: {}, Timestamp: {}", 
            channel, recipient, reason, DateFormatUtil.now());
    }

    public static void logWebSocketConnection(String sessionId, String userId) {
        log.info("WebSocket connected - SessionId: {}, UserId: {}, Timestamp: {}", 
            sessionId, userId, DateFormatUtil.now());
    }

    public static void logWebSocketDisconnection(String sessionId, String userId) {
        log.info("WebSocket disconnected - SessionId: {}, UserId: {}, Timestamp: {}", 
            sessionId, userId, DateFormatUtil.now());
    }

    public static void setMDCValue(String key, String value) {
        MDC_CONTEXT.put(key, value);
    }

    public static String getMDCValue(String key) {
        Object value = MDC_CONTEXT.get(key);
        return value != null ? value.toString() : null;
    }

    public static void clearMDC() {
        MDC_CONTEXT.clear();
    }

    public static void logPerformanceMetric(String operation, long executionTimeMs) {
        if (executionTimeMs > 1000) {
            log.warn("Slow operation detected - Operation: {}, ExecutionTime: {}ms", 
                operation, executionTimeMs);
        } else {
            log.debug("Operation completed - Operation: {}, ExecutionTime: {}ms", 
                operation, executionTimeMs);
        }
    }
}
