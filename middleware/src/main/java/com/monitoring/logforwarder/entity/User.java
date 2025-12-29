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
 * JPA entity representing a user account.
 *
 * <p><b>Purpose:</b> Persists user credentials, profile, roles, MFA settings,
 * and security status for authentication and authorization.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: users</li>
 *   <li>Indexed on username (unique), email (unique), role</li>
 *   <li>Default role: VIEWER</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_role", columnList = "role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 256)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "role", length = 32, nullable = false)
    private String role;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_password_change")
    private LocalDateTime lastPasswordChange;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "mfa_enabled")
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;

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
        if (role == null) {
            role = "VIEWER";
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}
