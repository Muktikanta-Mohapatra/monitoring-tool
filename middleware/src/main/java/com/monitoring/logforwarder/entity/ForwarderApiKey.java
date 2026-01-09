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
 * JPA entity representing a forwarder API key.
 *
 * <p><b>Purpose:</b> Stores API keys for LogForwarder authentication with
 * BCrypt-hashed keys, status tracking, and expiration management.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: forwarder_api_keys</li>
 *   <li>Unique index on api_key_hash</li>
 *   <li>Status values: ACTIVE, REVOKED, EXPIRED</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "forwarder_api_keys", indexes = {
    @Index(name = "idx_forwarder_id", columnList = "forwarder_id"),
    @Index(name = "idx_api_key_hash", columnList = "api_key_hash", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForwarderApiKey {

    @Id
    private Long id;

    @Column(name = "forwarder_id", nullable = false, length = 100)
    private String forwarderId;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 256)
    private String apiKeyHash;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApiKeyStatus status;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

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
            status = ApiKeyStatus.ACTIVE;
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}
