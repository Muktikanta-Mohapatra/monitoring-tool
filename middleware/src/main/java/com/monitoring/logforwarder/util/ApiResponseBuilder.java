package com.monitoring.logforwarder.util;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public final class ApiResponseBuilder {

    private ApiResponseBuilder() {
    }

    public static ResponseEntity<ApiResponseDTO> success(String message, String code) {
        return ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message(message)
                .code(code)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public static ResponseEntity<ApiResponseDTO> success(String message, String code, Object data) {
        return ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message(message)
                .code(code)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public static ResponseEntity<ApiResponseDTO> created(String message, String code, Object data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.builder()
                        .success(true)
                        .message(message)
                        .code(code)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    public static ResponseEntity<ApiResponseDTO> accepted(String message, String code, Object data) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponseDTO.builder()
                        .success(true)
                        .message(message)
                        .code(code)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    public static ResponseEntity<ApiResponseDTO> error(String message, String code, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(message)
                        .code(code)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    public static ResponseEntity<ApiResponseDTO> error(String message, String code, HttpStatus status, Object data) {
        return ResponseEntity.status(status)
                .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(message)
                        .code(code)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    public static ResponseEntity<ApiResponseDTO> error(String message, String code, HttpStatus status, List<String> errors) {
        return ResponseEntity.status(status)
                .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(message)
                        .code(code)
                        .errors(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    public static ResponseEntity<ApiResponseDTO> badRequest(String message, String code) {
        return error(message, code, HttpStatus.BAD_REQUEST);
    }

    public static ResponseEntity<ApiResponseDTO> badRequest(String message, String code, Object data) {
        return error(message, code, HttpStatus.BAD_REQUEST, data);
    }

    public static ResponseEntity<ApiResponseDTO> notFound(String message, String code) {
        return error(message, code, HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<ApiResponseDTO> unauthorized(String message, String code) {
        return error(message, code, HttpStatus.UNAUTHORIZED);
    }

    public static ResponseEntity<ApiResponseDTO> forbidden(String message, String code) {
        return error(message, code, HttpStatus.FORBIDDEN);
    }

    public static ResponseEntity<ApiResponseDTO> internalError(String message, String code) {
        return error(message, code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static ResponseEntity<ApiResponseDTO> fromException(Throwable ex, String code, HttpStatus status) {
        String message = extractMessage(ex);
        return error(message, code, status);
    }

    public static <T> Function<T, ResponseEntity<ApiResponseDTO>> successMapper(String message, String code) {
        return data -> success(message, code, data);
    }

    public static <T> Function<T, ResponseEntity<ApiResponseDTO>> successMapper(String message, String code, 
                                                                                   Function<T, String> messageFormatter) {
        return data -> success(messageFormatter.apply(data), code, data);
    }

    public static <T> Function<T, ResponseEntity<ApiResponseDTO>> createdMapper(String message, String code) {
        return data -> created(message, code, data);
    }

    public static Function<Throwable, ResponseEntity<ApiResponseDTO>> errorHandler(String code, HttpStatus status) {
        return ex -> fromException(ex, code, status);
    }

    public static Function<Throwable, ResponseEntity<ApiResponseDTO>> badRequestHandler(String code) {
        return errorHandler(code, HttpStatus.BAD_REQUEST);
    }

    public static Function<Throwable, ResponseEntity<ApiResponseDTO>> notFoundHandler(String code) {
        return errorHandler(code, HttpStatus.NOT_FOUND);
    }

    public static Function<Throwable, ResponseEntity<ApiResponseDTO>> internalErrorHandler(String code) {
        return errorHandler(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static Function<Throwable, ResponseEntity<ApiResponseDTO>> unauthorizedHandler(String code) {
        return errorHandler(code, HttpStatus.UNAUTHORIZED);
    }

    public static ApiResponseDTO buildSuccess(String message, String code, Object data) {
        return ApiResponseDTO.builder()
                .success(true)
                .message(message)
                .code(code)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ApiResponseDTO buildError(String message, String code) {
        return ApiResponseDTO.builder()
                .success(false)
                .message(message)
                .code(code)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ApiResponseDTO buildError(String message, String code, List<String> errors) {
        return ApiResponseDTO.builder()
                .success(false)
                .message(message)
                .code(code)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String extractMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return ex.getMessage() != null ? ex.getMessage() : "Unknown error";
    }
}
