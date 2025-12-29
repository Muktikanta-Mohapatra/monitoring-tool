package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for authentication login responses.
 *
 * <p><b>Purpose:</b> Contains JWT tokens and user information returned after
 * successful authentication.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserDTO user;
}
