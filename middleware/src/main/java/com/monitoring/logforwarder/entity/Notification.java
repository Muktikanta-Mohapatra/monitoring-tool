package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a notification sent for an alert.
 *
 * <p><b>Purpose:</b> Tracks notification delivery for alerts through various
 * channels (email, SMS, webhook), including status and error handling.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: notifications</li>
 *   <li>Indexed on alert_id, status, created_at</li>
 *   <li>Status values: PENDING, SENT, FAILED</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_alert_id", columnList = "alert_id"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_created_at", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private Long id;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "channel", nullable = false, length = 64)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 256)
    private String recipient;

    @Column(name = "subject", length = 512)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "status", length = 32)
    private String status = "PENDING";

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
