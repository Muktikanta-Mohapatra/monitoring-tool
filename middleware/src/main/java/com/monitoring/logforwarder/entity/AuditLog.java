package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing an audit log entry.
 *
 * <p><b>Purpose:</b> Records security-relevant user actions and system events
 * for compliance, security investigation, and operational auditing.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: audit_logs</li>
 *   <li>Indexed on user_id, action, created_at, resource_type</li>
 *   <li>Immutable records (no update, only insert)</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_action", columnList = "action"),
    @Index(name = "idx_timestamp", columnList = "created_at DESC"),
    @Index(name = "idx_resource_type", columnList = "resource_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 256)
    private String resourceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = System.currentTimeMillis() * 1000000L + System.nanoTime() % 1000000L;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}
