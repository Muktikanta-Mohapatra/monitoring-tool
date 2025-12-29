package com.monitoring.logforwarder.exception;

/**
 * Base exception class for API errors.
 *
 * <p><b>Purpose:</b> Provides a common base for all API exceptions with error code
 * and HTTP status information for consistent error handling.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class ApiException extends RuntimeException {
    private String code;
    private int status;

    public ApiException(String message) {
        super(message);
        this.code = "GENERAL_ERROR";
        this.status = 500;
    }

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
        this.status = 500;
    }

    public ApiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = 500;
    }

    public ApiException(String code, String message, int status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
