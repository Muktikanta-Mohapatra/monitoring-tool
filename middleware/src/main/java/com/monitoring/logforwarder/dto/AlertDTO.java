package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data Transfer Object for alert information.
 *
 * <p><b>Purpose:</b> Represents an alert instance including its status, severity,
 * trigger details, acknowledgment, and resolution information.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private Long id;
    private Long alertRuleId;
    private String status;
    private String severity;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private String triggerMessage;
    private String resolutionMessage;
    private Map<String, Object> triggerCondition;
    private Integer notificationCount;
    private Boolean notificationDelivered;
    private String notificationChannel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
