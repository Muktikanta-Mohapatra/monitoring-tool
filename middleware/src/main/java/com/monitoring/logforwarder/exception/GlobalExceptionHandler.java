package com.monitoring.logforwarder.exception;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API error responses.
 *
 * <p><b>Purpose:</b> Provides centralized exception handling for all controllers,
 * converting exceptions to consistent ApiResponseDTO format with appropriate HTTP status codes.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Handles ResourceNotFoundException (404)</li>
 *   <li>Handles ValidationException (400)</li>
 *   <li>Handles AuthenticationException (401)</li>
 *   <li>Handles UnauthorizedException/AccessDeniedException (403)</li>
 *   <li>Handles generic exceptions (500)</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponseDTO> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiResponseBuilder.notFound(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponseDTO> handleValidationException(ValidationException ex, WebRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        if (!ex.getFieldErrors().isEmpty()) {
            return ApiResponseBuilder.badRequest(ex.getMessage(), ex.getCode(), ex.getFieldErrors());
        }
        return ApiResponseBuilder.badRequest(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponseDTO> handleDataAccessException(DataAccessException ex, WebRequest request) {
        log.error("Data access error: {}", ex.getMessage(), ex);
        return ApiResponseBuilder.internalError(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseDTO> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ApiResponseBuilder.unauthorized(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponseDTO> handleUnauthorizedException(UnauthorizedException ex, WebRequest request) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return ApiResponseBuilder.forbidden(ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDTO> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ApiResponseBuilder.forbidden("Access denied", "ACCESS_DENIED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO> handleValidationErrors(MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Method argument validation error");
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ApiResponseBuilder.badRequest("Validation failed", "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponseDTO> handleApiException(ApiException ex, WebRequest request) {
        log.error("API exception: {}", ex.getMessage(), ex);
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ApiResponseBuilder.error(ex.getMessage(), ex.getCode(), status);
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException ex, WebRequest request) {
        log.debug("Client disconnected during response: {}", ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex, WebRequest request) {
        Throwable rootCause = getRootCause(ex);
        if (rootCause instanceof ClientAbortException) {
            log.debug("Client disconnected during async request: {}", ex.getMessage());
        } else {
            log.debug("Async request not usable: {}", ex.getMessage());
        }
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponseDTO> handleIOException(IOException ex, WebRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (message.contains("connection") || message.contains("abort") || 
            message.contains("broken pipe") || message.contains("stream closed")) {
            log.debug("Connection-related IO exception: {}", ex.getMessage());
            return null;
        }
        log.error("IO exception", ex);
        return ApiResponseBuilder.internalError("Internal server error", "INTERNAL_ERROR");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO> handleGlobalException(Exception ex, WebRequest request) {
        if (isClientDisconnectException(ex)) {
            log.debug("Client disconnect detected in generic handler: {}", ex.getMessage());
            return null;
        }
        log.error("Unexpected exception", ex);
        return ApiResponseBuilder.internalError("Internal server error", "INTERNAL_ERROR");
    }

    private boolean isClientDisconnectException(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ClientAbortException || 
                current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (message.contains("broken pipe") || 
                message.contains("connection reset") ||
                message.contains("connection abort")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
