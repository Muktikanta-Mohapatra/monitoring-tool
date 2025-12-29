package com.monitoring.logforwarder.exception;

/**
 * Exception thrown when a user lacks permission for an action.
 *
 * <p><b>Purpose:</b> Indicates that an authenticated user does not have sufficient
 * permissions for the requested operation. Maps to HTTP 403 Forbidden.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class UnauthorizedException extends ApiException {
    
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 403);
    }

    public UnauthorizedException(String resource, String action) {
        super("UNAUTHORIZED", 
            String.format("User not authorized to %s %s", action, resource), 
            403);
    }
}
