package com.monitoring.logforwarder.websocket.validator;

import com.monitoring.logforwarder.websocket.*;
import com.monitoring.logforwarder.websocket.WebSocketMessageTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class WebSocketMessageValidator {

    private static final int MAX_QUERY_LENGTH = 10000;
    private static final int MAX_FIELDS = 100;
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 10000;

    public ValidationResult validate(Object message) {
        if (message == null) {
            return createError(ErrorCode.INVALID_MESSAGE_FORMAT,
                    "Message cannot be null");
        }

        try {
            if (message instanceof GenericMessage) {
                return validateGenericMessage((GenericMessage) message);
            } else if (message instanceof SubscriptionMessage) {
                return validateSubscriptionMessage((SubscriptionMessage) message);
            } else if (message instanceof AlertMessage) {
                return validateAlertMessage((AlertMessage) message);
            } else if (message instanceof MetricsMessage) {
                return validateMetricsMessage((MetricsMessage) message);
            } else if (message instanceof ErrorMessage) {
                return validateErrorMessage((ErrorMessage) message);
            } else if (message instanceof AckMessage) {
                return validateAckMessage((AckMessage) message);
            } else if (message instanceof QueryMessage) {
                return validateQueryMessage((QueryMessage) message);
            } else {
                return createError(ErrorCode.INVALID_MESSAGE_FORMAT,
                        "Unknown message type: " + message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Validation error", e);
            return createError(ErrorCode.INTERNAL_ERROR,
                    "Validation failed: " + e.getMessage());
        }
    }

    private ValidationResult validateGenericMessage(GenericMessage message) {
        List<String> errors = new ArrayList<>();

        if (message.getType() == null || message.getType().trim().isEmpty()) {
            errors.add("Message type is required");
        }

        if (message.getRequestId() == null || message.getRequestId().trim().isEmpty()) {
            errors.add("Request ID is required");
        } else if (message.getRequestId().length() > 255) {
            errors.add("Request ID is too long (max 255 characters)");
        }

        if (message.getVersion() == null || message.getVersion().trim().isEmpty()) {
            errors.add("Version is required");
        } else if (!isValidVersion(message.getVersion())) {
            errors.add("Invalid version format: " + message.getVersion());
        }

        if (message.getTimestamp() == null) {
            errors.add("Timestamp is required");
        }

        return errors.isEmpty() 
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private List<String> validateBaseFields(String type, String version, String requestId, LocalDateTime timestamp) {
        List<String> errors = new ArrayList<>();

        if (type == null || type.trim().isEmpty()) {
            errors.add("Message type is required");
        }

        if (requestId == null || requestId.trim().isEmpty()) {
            errors.add("Request ID is required");
        } else if (requestId.length() > 255) {
            errors.add("Request ID is too long (max 255 characters)");
        }

        if (version == null || version.trim().isEmpty()) {
            errors.add("Version is required");
        } else if (!isValidVersion(version)) {
            errors.add("Invalid version format: " + version);
        }

        if (timestamp == null) {
            errors.add("Timestamp is required");
        }

        return errors;
    }

    private ValidationResult validateSubscriptionMessage(SubscriptionMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getAction() == null || message.getAction().trim().isEmpty()) {
            errors.add("Action is required for subscription message");
        } else if (!isValidSubscriptionAction(message.getAction())) {
            errors.add("Invalid subscription action: " + message.getAction());
        }

        if ("SUBSCRIBE".equals(message.getAction()) || "UPDATE".equals(message.getAction())) {
            if (message.getQuery() == null || message.getQuery().trim().isEmpty()) {
                errors.add("Query is required for subscribe/update action");
            } else if (message.getQuery().length() > MAX_QUERY_LENGTH) {
                errors.add("Query is too long (max " + MAX_QUERY_LENGTH + " characters)");
            }
        }

        if (message.getFields() != null && message.getFields().size() > MAX_FIELDS) {
            errors.add("Too many fields specified (max " + MAX_FIELDS + ")");
        }

        if (message.getBatchSize() != null) {
            if (message.getBatchSize() < MIN_BATCH_SIZE || message.getBatchSize() > MAX_BATCH_SIZE) {
                errors.add("Batch size must be between " + MIN_BATCH_SIZE + " and " + MAX_BATCH_SIZE);
            }
        }

        if (message.getIndexes() != null && message.getIndexes().isEmpty()) {
            errors.add("Indexes list cannot be empty if specified");
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private ValidationResult validateAlertMessage(AlertMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getAlertId() == null || message.getAlertId().trim().isEmpty()) {
            errors.add("Alert ID is required");
        }

        if (message.getAlertName() != null && message.getAlertName().length() > 255) {
            errors.add("Alert name is too long (max 255 characters)");
        }

        if (message.getSeverity() != null && !isValidSeverity(message.getSeverity())) {
            errors.add("Invalid severity level: " + message.getSeverity());
        }

        if (message.getStatus() != null && !isValidAlertStatus(message.getStatus())) {
            errors.add("Invalid alert status: " + message.getStatus());
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private ValidationResult validateMetricsMessage(MetricsMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getMetricType() == null || message.getMetricType().trim().isEmpty()) {
            errors.add("Metric type is required");
        }

        if (message.getValue() != null) {
            if (Double.isNaN(message.getValue()) || Double.isInfinite(message.getValue())) {
                errors.add("Invalid metric value: must be a valid number");
            }
        }

        if (message.getAggregatedMetrics() != null) {
            for (Double value : message.getAggregatedMetrics().values()) {
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    errors.add("Invalid aggregated metric value: must be a valid number");
                    break;
                }
            }
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private ValidationResult validateErrorMessage(ErrorMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getErrorCode() == null || message.getErrorCode().trim().isEmpty()) {
            errors.add("Error code is required");
        }

        if (message.getErrorMessage() == null || message.getErrorMessage().trim().isEmpty()) {
            errors.add("Error message is required");
        } else if (message.getErrorMessage().length() > 1000) {
            errors.add("Error message is too long (max 1000 characters)");
        }

        if (message.getSeverity() != null && !isValidSeverity(message.getSeverity())) {
            errors.add("Invalid severity level: " + message.getSeverity());
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private ValidationResult validateAckMessage(AckMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getOriginalRequestId() == null || message.getOriginalRequestId().trim().isEmpty()) {
            errors.add("Original request ID is required");
        }

        if (message.getStatus() == null || message.getStatus().trim().isEmpty()) {
            errors.add("Status is required");
        } else if (!isValidAckStatus(message.getStatus())) {
            errors.add("Invalid status: " + message.getStatus());
        }

        if (message.getProcessedCount() != null && message.getProcessedCount() < 0) {
            errors.add("Processed count cannot be negative");
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private ValidationResult validateQueryMessage(QueryMessage message) {
        List<String> errors = new ArrayList<>();

        errors.addAll(validateBaseFields(message.getType(), message.getVersion(), message.getRequestId(), message.getTimestamp()));

        if (message.getQueryId() == null || message.getQueryId().trim().isEmpty()) {
            errors.add("Query ID is required");
        }

        if (message.getQueryText() != null && message.getQueryText().length() > MAX_QUERY_LENGTH) {
            errors.add("Query text is too long (max " + MAX_QUERY_LENGTH + " characters)");
        }

        if (message.getQueryType() != null && !isValidQueryType(message.getQueryType())) {
            errors.add("Invalid query type: " + message.getQueryType());
        }

        if (message.getLimit() != null && message.getLimit() <= 0) {
            errors.add("Limit must be greater than 0");
        }

        if (message.getOffset() != null && message.getOffset() < 0) {
            errors.add("Offset cannot be negative");
        }

        if (message.getExecutionTimeMs() != null && message.getExecutionTimeMs() < 0) {
            errors.add("Execution time cannot be negative");
        }

        if (message.getTotalResults() != null && message.getTotalResults() < 0) {
            errors.add("Total results cannot be negative");
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors, message.getRequestId());
    }

    private boolean isValidVersion(String version) {
        return version.matches("^\\d+\\.\\d+(\\.\\d+)?$");
    }

    private boolean isValidSubscriptionAction(String action) {
        return action.matches("^(SUBSCRIBE|UNSUBSCRIBE|UPDATE|LIST)$");
    }

    private boolean isValidSeverity(String severity) {
        return severity.matches("^(CRITICAL|ERROR|WARNING|INFO)$");
    }

    private boolean isValidAlertStatus(String status) {
        return status.matches("^(TRIGGERED|RESOLVED|ACKNOWLEDGED|ESCALATED)$");
    }

    private boolean isValidAckStatus(String status) {
        return status.matches("^(SUCCESS|FAILED|PARTIAL)$");
    }

    private boolean isValidQueryType(String queryType) {
        return queryType.matches("^(SEARCH|AGGREGATE|HISTOGRAM|STATS|FACET)$");
    }

    private ValidationResult createError(String errorCode, String errorMessage) {
        return ValidationResult.invalid(
            List.of(errorMessage),
            null
        );
    }
}
