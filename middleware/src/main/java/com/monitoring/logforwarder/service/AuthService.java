package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.LoginRequestDTO;
import com.monitoring.logforwarder.dto.LoginResponseDTO;
import com.monitoring.logforwarder.entity.User;
import com.monitoring.logforwarder.exception.AuthenticationException;
import com.monitoring.logforwarder.exception.UnauthorizedException;
import com.monitoring.logforwarder.repository.postgresql.UserRepository;
import com.monitoring.logforwarder.security.JwtTokenProvider;
import com.monitoring.logforwarder.security.SecurityConstants;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Service for authentication operations.
 *
 * <p><b>Purpose:</b> Handles user authentication including login, token refresh,
 * logout, and account lockout management.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>JWT token generation and validation</li>
 *   <li>Failed login attempt tracking</li>
 *   <li>Account lockout after max attempts</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
@Transactional(value = "postgresqlTransactionManager")
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public CompletableFuture<LoginResponseDTO> authenticate(LoginRequestDTO loginRequest) {
        return AsyncHelper.executeAsyncFuture(() -> {
            try {
                Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                    )
                );

                User user = userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new AuthenticationException("User not found"));

                if (user.getIsLocked()) {
                    throw new UnauthorizedException("User account is locked");
                }

                if (!user.getIsActive()) {
                    throw new UnauthorizedException("User account is inactive");
                }

                user.setFailedLoginAttempts(0);
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);

                String accessToken = tokenProvider.generateToken(authentication);
                String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

                com.monitoring.logforwarder.dto.UserDTO userDTO = new com.monitoring.logforwarder.dto.UserDTO();
                userDTO.setUsername(user.getUsername());
                userDTO.setEmail(user.getEmail());
                userDTO.setRole(user.getRole());

                return LoginResponseDTO.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(SecurityConstants.JWT_EXPIRATION_MS / 1000)
                    .user(userDTO)
                    .build();

            } catch (Exception ex) {
                User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);
                if (user != null) {
                    int attempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
                    user.setFailedLoginAttempts(attempts + 1);
                    
                    if (user.getFailedLoginAttempts() >= SecurityConstants.MAX_LOGIN_ATTEMPTS) {
                        user.setIsLocked(true);
                        user.setLastLogin(LocalDateTime.now());
                    }
                    userRepository.save(user);
                }
                throw new AuthenticationException("Invalid username or password");
            }
        });
    }

    public CompletableFuture<LoginResponseDTO> refreshToken(String refreshToken) {
        return AsyncHelper.executeAsyncFuture(() -> {
            if (!tokenProvider.validateToken(refreshToken)) {
                throw new AuthenticationException("Invalid or expired refresh token");
            }

            String username = tokenProvider.getUsernameFromToken(refreshToken);
            String newAccessToken = tokenProvider.generateTokenFromUsername(username);
            String newRefreshToken = tokenProvider.generateRefreshToken(username);

            return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(SecurityConstants.JWT_EXPIRATION_MS / 1000)
                .build();
        });
    }

    public CompletableFuture<Void> logout(Long userId) {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.info("User {} logged out", userId);
            return null;
        });
    }

    public CompletableFuture<Boolean> validateToken(String token) {
        return AsyncHelper.executeAsyncFuture(() -> tokenProvider.validateToken(token));
    }
}
