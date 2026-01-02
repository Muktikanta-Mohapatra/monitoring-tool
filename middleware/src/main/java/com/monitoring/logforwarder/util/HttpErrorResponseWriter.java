package com.monitoring.logforwarder.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitoring.logforwarder.dto.ApiResponseDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.LocalDateTime;

public final class HttpErrorResponseWriter {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private HttpErrorResponseWriter() {
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public static void writeError(HttpServletResponse response, int status, String message, String code) 
            throws IOException {
        ApiResponseDTO body = ApiResponseDTO.builder()
                .success(false)
                .message(message)
                .code(code)
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message, "UNAUTHORIZED");
    }

    public static void writeUnauthorized(HttpServletResponse response, String message, String code) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message, code);
    }

    public static void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, message, "FORBIDDEN");
    }

    public static void writeForbidden(HttpServletResponse response, String message, String code) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, message, code);
    }

    public static void writeBadRequest(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_BAD_REQUEST, message, "BAD_REQUEST");
    }

    public static void writeBadRequest(HttpServletResponse response, String message, String code) throws IOException {
        writeError(response, HttpServletResponse.SC_BAD_REQUEST, message, code);
    }

    public static void writeTooManyRequests(HttpServletResponse response, String message) throws IOException {
        writeError(response, 429, message, "RATE_LIMIT_EXCEEDED");
    }

    public static void writeInternalError(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, "INTERNAL_ERROR");
    }

    public static void writeSimpleError(HttpServletResponse response, int status, String errorMessage) 
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + escapeJson(errorMessage) + "\"}");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
