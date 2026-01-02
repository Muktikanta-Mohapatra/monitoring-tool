package com.monitoring.logforwarder.websocket.validator;

import com.monitoring.logforwarder.websocket.ErrorCode;
import com.monitoring.logforwarder.websocket.ErrorMessage;
import com.monitoring.logforwarder.websocket.ErrorSeverity;
import com.monitoring.logforwarder.websocket.WebSocketErrorBuilder;

import java.util.ArrayList;
import java.util.List;

public class QueryValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final String errorCode;

    private QueryValidationResult(boolean valid, List<String> errors, String errorCode) {
        this.valid = valid;
        this.errors = errors;
        this.errorCode = errorCode;
    }

    public static QueryValidationResult valid() {
        return new QueryValidationResult(true, List.of(), null);
    }

    public static QueryValidationResult error(List<String> errors) {
        return new QueryValidationResult(false, new ArrayList<>(errors), ErrorCode.QUERY_INVALID);
    }

    public static QueryValidationResult error(String errorCode, String message) {
        return new QueryValidationResult(false, List.of(message), errorCode);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ErrorMessage toErrorMessage(String requestId) {
        String combinedErrors = String.join("; ", errors);
        String code = errorCode != null ? errorCode : ErrorCode.QUERY_INVALID;
        return WebSocketErrorBuilder.error(code, combinedErrors, ErrorSeverity.ERROR, requestId, "Query validation");
    }
}
