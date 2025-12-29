package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for alert rule configuration.
 *
 * <p><b>Purpose:</b> Defines alert rule settings including search criteria,
 * thresholds, time windows, and notification configuration.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDTO {
    private Long id;
    private String name;
    private String description;
    private String severity;
    private Boolean enabled;
    private String searchQuery;
    private Integer thresholdCount;
    private String thresholdOperator;
    private Integer timeWindowMinutes;
    private Map<String, Object> condition;
    private List<Map<String, Object>> actions;
    private String notificationChannel;
    private List<String> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
