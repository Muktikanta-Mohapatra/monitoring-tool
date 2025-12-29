package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for user account information.
 *
 * <p><b>Purpose:</b> Represents a user account including profile information,
 * role, MFA status, and security-related fields like lockout status.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Boolean active;
    private Boolean mfaEnabled;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer failedLoginAttempts;
    private Boolean lockedOut;
    private LocalDateTime lockoutUntil;
}
