package com.monitoring.logforwarder.exception;

/**
 * Exception thrown when database or data access operations fail.
 *
 * <p><b>Purpose:</b> Indicates failures in data access layer operations including
 * query execution, connection issues, and data consistency problems. Maps to HTTP 500 Internal Server Error.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class DataAccessException extends ApiException {

    public DataAccessException(String message) {
        super("DATA_ACCESS_ERROR", message, 500);
    }

    public DataAccessException(String message, Throwable cause) {
        super("DATA_ACCESS_ERROR", message, 500);
        initCause(cause);
    }

    public DataAccessException(String code, String message, int status) {
        super(code, message, status);
    }

    public DataAccessException(String code, String message, int status, Throwable cause) {
        super(code, message, status);
        initCause(cause);
    }
}
