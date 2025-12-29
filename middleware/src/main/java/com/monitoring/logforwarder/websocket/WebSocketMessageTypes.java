package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class WebSocketMessageTypes {

    public static final String VERSION = "1.0";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenericMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
        private String subscriptionId;
        private String action;
        private String query;
        private List<String> fields;
        private Integer batchSize;
        @JsonProperty("indexes")
        private List<String> indexes;
        private Map<String, Object> filters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
        private String alertId;
        private String alertName;
        private String severity;
        private String status;
        private LocalDateTime triggeredAt;
        private String message;
        private Long eventCount;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
        private String metricType;
        private LocalDateTime collectedAt;
        private Double value;
        private Map<String, Object> tags;
        private Map<String, Double> aggregatedMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorMessage {
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AckMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
        private String originalRequestId;
        private String status;
        private Long processedCount;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryMessage {
        private String type;
        private String version;
        private String requestId;
        private LocalDateTime timestamp;
        private String queryId;
        private String queryText;
        private String queryType;
        private Integer limit;
        private Integer offset;
        private Long executionTimeMs;
        private Long totalResults;
        private List<Map<String, Object>> results;
    }

    public static class ErrorCode {
        public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
        public static final String QUERY_INVALID = "QUERY_INVALID";
        public static final String SUBSCRIPTION_LIMIT_EXCEEDED = "SUBSCRIPTION_LIMIT_EXCEEDED";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        public static final String SUBSCRIPTION_NOT_FOUND = "SUBSCRIPTION_NOT_FOUND";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String TIMEOUT = "TIMEOUT";
        public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
        public static final String SQL_INJECTION_DETECTED = "SQL_INJECTION_DETECTED";
        public static final String INVALID_MESSAGE_FORMAT = "INVALID_MESSAGE_FORMAT";
    }

    public static class ErrorSeverity {
        public static final String CRITICAL = "CRITICAL";
        public static final String ERROR = "ERROR";
        public static final String WARNING = "WARNING";
        public static final String INFO = "INFO";
    }
}
