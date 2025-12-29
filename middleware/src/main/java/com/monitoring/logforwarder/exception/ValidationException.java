package com.monitoring.logforwarder.exception;

import java.util.Map;
import java.util.HashMap;

/**
 * Exception thrown when request validation fails.
 *
 * <p><b>Purpose:</b> Indicates validation errors in request data with support for
 * field-specific error messages. Maps to HTTP 400 Bad Request.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class ValidationException extends ApiException {
    private Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, 400);
        this.fieldErrors = new HashMap<>();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super("VALIDATION_ERROR", message, 400);
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", message, 400);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public void addFieldError(String field, String error) {
        this.fieldErrors.put(field, error);
    }
}
