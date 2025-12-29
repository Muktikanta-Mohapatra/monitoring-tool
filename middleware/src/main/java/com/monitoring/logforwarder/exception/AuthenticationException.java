package com.monitoring.logforwarder.exception;

/**
 * Exception thrown when authentication fails.
 *
 * <p><b>Purpose:</b> Indicates failed authentication attempts such as invalid credentials
 * or expired tokens. Maps to HTTP 401 Unauthorized.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class AuthenticationException extends ApiException {
    
    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message, 401);
    }

    public AuthenticationException(String message, Throwable cause) {
        super("AUTHENTICATION_FAILED", message, 401, cause);
    }
}
