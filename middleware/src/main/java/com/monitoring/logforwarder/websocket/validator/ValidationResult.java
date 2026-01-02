package com.monitoring.logforwarder.websocket.validator;

import com.monitoring.logforwarder.websocket.ErrorMessage;
import com.monitoring.logforwarder.websocket.WebSocketErrorBuilder;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final String requestId;

    private ValidationResult(boolean valid, List<String> errors, String requestId) {
        this.valid = valid;
        this.errors = errors;
        this.requestId = requestId;
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of(), null);
    }

    public static ValidationResult invalid(List<String> errors, String requestId) {
        return new ValidationResult(false, new ArrayList<>(errors), requestId);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public String getRequestId() {
        return requestId;
    }

    public ErrorMessage toErrorMessage() {
        String combinedErrors = String.join("; ", errors);
        return WebSocketErrorBuilder.validationError(combinedErrors, requestId);
    }
}
