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
 * JPA entity representing an alert rule configuration.
 *
 * <p><b>Purpose:</b> Defines rules for triggering alerts based on conditions,
 * including notification settings and severity levels for proactive monitoring.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: alert_rules</li>
 *   <li>Indexed on enabled, created_by</li>
 *   <li>Condition stored as JSON for flexible rule definitions</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "alert_rules", indexes = {
    @Index(name = "idx_alert_rule_enabled", columnList = "enabled"),
    @Index(name = "idx_alert_rule_created_by", columnList = "created_by")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "condition_type", nullable = false, length = 64)
    private String conditionType;

    @Column(name = "condition_json", nullable = false, columnDefinition = "TEXT")
    private String conditionJson;

    @Column(name = "severity", length = 32)
    private String severity = "MEDIUM";

    @Column(name = "notifications_json", columnDefinition = "TEXT")
    private String notificationsJson;

    @Column(name = "notification_channels", columnDefinition = "TEXT")
    private String notificationChannels;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = generateId();
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}
