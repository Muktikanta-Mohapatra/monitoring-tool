package com.monitoring.logforwarder.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p><b>Purpose:</b> Indicates that a requested entity does not exist in the system.
 * Maps to HTTP 404 Not Found.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class ResourceNotFoundException extends ApiException {
    
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
            "RESOURCE_NOT_FOUND",
            String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue),
            404
        );
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, 404);
    }
}
