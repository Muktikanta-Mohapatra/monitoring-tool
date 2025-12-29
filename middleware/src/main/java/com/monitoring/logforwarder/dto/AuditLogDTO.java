package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data Transfer Object for audit log entries.
 *
 * <p><b>Purpose:</b> Records security-relevant actions including user operations,
 * resource changes, and access attempts for compliance and security auditing.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String status;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
    private Map<String, Object> additionalData;
}
