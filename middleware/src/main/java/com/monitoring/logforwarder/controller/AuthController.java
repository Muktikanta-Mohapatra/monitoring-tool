package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.LoginRequestDTO;
import com.monitoring.logforwarder.security.JwtTokenProvider;
import com.monitoring.logforwarder.service.AuthService;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for authentication and authorization operations.
 *
 * <p><b>Purpose:</b> Handles user authentication including login, token refresh,
 * logout, and current user retrieval. Provides JWT-based authentication for
 * secure API access.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/auth</li>
 *   <li>Public endpoints (no authentication required)</li>
 *   <li>Returns JWT tokens for authenticated sessions</li>
 *   <li>Supports token refresh for extended sessions</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Login request
 * POST /api/v1/auth/login
 * Content-Type: application/json
 * {
 *   "username": "admin",
 *   "password": "password123"
 * }
 *
 * // Response
 * {
 *   "success": true,
 *   "data": {
 *     "accessToken": "eyJhbGciOiJIUzI1NiIs...",
 *     "tokenType": "Bearer",
 *     "expiresIn": 3600
 *   }
 * }
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see AuthService
 * @see JwtTokenProvider
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final JwtTokenProvider tokenProvider;

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * <p><b>Purpose:</b> Validates user credentials and issues access tokens for API authentication.</p>
     *
     * @param loginRequest the login credentials containing username and password
     * @return JWT tokens on success, error response on authentication failure
     */
    @PostMapping("/login")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return authService.authenticate(loginRequest)
            .thenApply(ApiResponseBuilder.successMapper("Login successful", "LOGIN_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Authentication failed", ex);
                return ApiResponseBuilder.fromException(ex, "LOGIN_FAILED", HttpStatus.UNAUTHORIZED);
            });
    }

    /**
     * Refreshes an expired or expiring JWT token.
     *
     * <p><b>Purpose:</b> Issues new tokens using a valid refresh token, extending user sessions.</p>
     *
     * @param token the Authorization header containing the Bearer token
     * @return new JWT tokens on success, unauthorized response on invalid token
     */
    @PostMapping("/refresh")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> refreshToken(@RequestHeader("Authorization") String token) {
        try {
            String refreshToken = token.replace("Bearer ", "");
            return authService.refreshToken(refreshToken)
                .thenApply(ApiResponseBuilder.successMapper("Token refreshed", "TOKEN_REFRESH_SUCCESS"))
                .exceptionally(ex -> {
                    log.error("Token refresh failed", ex);
                    return ApiResponseBuilder.fromException(ex, "TOKEN_REFRESH_FAILED", HttpStatus.UNAUTHORIZED);
                });
        } catch (Exception ex) {
            log.error("Token refresh error", ex);
            return CompletableFuture.completedFuture(
                ApiResponseBuilder.fromException(ex, "TOKEN_REFRESH_FAILED", HttpStatus.UNAUTHORIZED)
            );
        }
    }

    /**
     * Logs out the current user and invalidates the session.
     *
     * <p><b>Purpose:</b> Clears the security context and invalidates any active tokens.</p>
     *
     * @return success response confirming logout
     */
    @PostMapping("/logout")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> logout() {
        return authService.logout(null)
            .thenApply(v -> {
                SecurityContextHolder.clearContext();
                return ApiResponseBuilder.success("Logged out successfully", "LOGOUT_SUCCESS");
            })
            .exceptionally(ex -> {
                log.error("Logout failed", ex);
                return ApiResponseBuilder.fromException(ex, "LOGOUT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
            });
    }

    /**
     * Retrieves the current authenticated user's information.
     *
     * <p><b>Purpose:</b> Returns the username of the currently authenticated user from the security context.</p>
     *
     * @return current user information
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDTO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ApiResponseBuilder.unauthorized("Not authenticated", "NOT_AUTHENTICATED");
        }
        return ApiResponseBuilder.success("Current user retrieved", "GET_USER_SUCCESS", authentication.getName());
    }
}
