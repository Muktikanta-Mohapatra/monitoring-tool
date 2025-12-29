package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a triggered alert.
 *
 * <p><b>Purpose:</b> Persists alert instances including trigger details, status,
 * acknowledgment, and resolution information for monitoring and auditing.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: alerts</li>
 *   <li>Indexed on status, severity, triggered_at, rule_id</li>
 *   <li>Status values: ACTIVE, ACKNOWLEDGED, RESOLVED</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_timestamp", columnList = "triggered_at DESC"),
    @Index(name = "idx_alert_rule_id", columnList = "rule_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    private Long id;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", length = 256)
    private String ruleName;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "query", columnDefinition = "TEXT")
    private String query;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "triggered_by_event_count")
    private Integer triggeredByEventCount;

    @Column(name = "condition", columnDefinition = "TEXT")
    private String condition;

    @Column(name = "actions", columnDefinition = "TEXT")
    private String actions;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "notification_timestamp")
    private LocalDateTime notificationTimestamp;

    @Column(name = "trigger_message", columnDefinition = "TEXT")
    private String triggerMessage;

    @Column(name = "resolution_message", columnDefinition = "TEXT")
    private String resolutionMessage;

    @Column(name = "notification_channel", length = 64)
    private String notificationChannel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = generateId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}
