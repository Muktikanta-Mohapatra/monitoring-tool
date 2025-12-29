package com.monitoring.logforwarder.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.logforwarder.dto.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Authentication entry point for unauthorized requests.
 *
 * <p><b>Purpose:</b> Handles authentication failures by returning a standardized
 * JSON error response with HTTP 401 Unauthorized status.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest httpServletRequest,
                         HttpServletResponse httpServletResponse,
                         AuthenticationException ex) throws IOException, ServletException {
        log.error("Responding with unauthorized error. Message: {}", ex.getMessage());

        httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final ApiResponseDTO body = ApiResponseDTO.builder()
            .success(false)
            .message("Unauthorized: " + ex.getMessage())
            .code("UNAUTHORIZED")
            .timestamp(LocalDateTime.now())
            .build();

        objectMapper.writeValue(httpServletResponse.getOutputStream(), body);
    }
}
