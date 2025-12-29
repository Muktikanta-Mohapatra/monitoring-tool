package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for standardized API responses.
 *
 * <p><b>Purpose:</b> Provides a consistent response structure for all API endpoints
 * including success status, message, response code, data payload, and errors.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ApiResponseDTO.builder()
 *     .success(true)
 *     .message("Operation completed")
 *     .code("SUCCESS")
 *     .data(resultObject)
 *     .timestamp(LocalDateTime.now())
 *     .build();
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO {
    private boolean success;
    private String message;
    private String code;
    private Object data;
    private LocalDateTime timestamp;
    private List<String> errors;
}
