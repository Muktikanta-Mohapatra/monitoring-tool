package com.monitoring.logforwarder.config;

import com.monitoring.logforwarder.security.ForwarderAuthFilter;
import com.monitoring.logforwarder.security.JwtAuthenticationEntryPoint;
import com.monitoring.logforwarder.security.JwtAuthenticationFilter;
import com.monitoring.logforwarder.security.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Configuration class for Spring Security authentication and authorization.
 *
 * <p><b>Purpose:</b> Configures the security infrastructure for the application including
 * JWT-based authentication, role-based authorization, password encoding, and endpoint
 * security rules. Provides stateless security suitable for REST API access.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Stateless session management for REST API compatibility</li>
 *   <li>JWT token-based authentication via custom filter</li>
 *   <li>BCrypt password encoding with strength 10</li>
 *   <li>Method-level security with @PreAuthorize, @Secured, @RolesAllowed</li>
 *   <li>Public endpoints: /api/v1/auth/**, /health, /metrics, /prometheus</li>
 *   <li>Role-based access: ADMIN for user management, ADMIN/OPERATOR for config</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Public authentication endpoint (no token required):
 * POST /api/v1/auth/login
 *
 * // Protected endpoint (requires valid JWT):
 * GET /api/v1/logs/search
 * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 *
 * // Admin-only endpoint:
 * POST /api/v1/users
 * Authorization: Bearer <admin-token>
 *
 * // Using method-level security:
 * &#64;PreAuthorize("hasAuthority('ADMIN')")
 * public void deleteUser(Long userId) { ... }
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @see JwtAuthenticationFilter
 * @see JwtAuthenticationEntryPoint
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        prePostEnabled = true,
        securedEnabled = true,
        jsr250Enabled = true
)
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private ForwarderAuthFilter forwarderAuthFilter;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    /**
     * Creates the password encoder using BCrypt algorithm.
     *
     * <p><b>Purpose:</b> Provides secure password hashing using BCrypt with configured
     * strength (12 rounds) for user password storage and verification.</p>
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH);
    }

    /**
     * Creates the authentication manager for credential validation.
     *
     * <p><b>Purpose:</b> Configures the authentication manager with user details service
     * and password encoder for validating user credentials during login.</p>
     *
     * @param http the HttpSecurity to obtain shared objects
     * @return configured authentication manager
     * @throws Exception if authentication manager creation fails
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }

    /**
     * Configures the security filter chain with authentication and authorization rules.
     *
     * <p><b>Purpose:</b> Defines the complete security configuration including CORS,
     * CSRF, session management, endpoint authorization, and JWT filter integration.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>CSRF disabled for REST API compatibility</li>
     *   <li>Stateless sessions for JWT-based authentication</li>
     *   <li>Public endpoints: auth, health, metrics, event batch ingestion</li>
     *   <li>Role-based access for user and config management</li>
     * </ul>
     *
     * @param http the HttpSecurity to configure
     * @return configured security filter chain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/health", "/metrics", "/prometheus").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/batch").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/logs/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/forwarders/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/alerts/**").authenticated()
                        .requestMatchers("/api/v1/users/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/v1/config/**").hasAnyAuthority("ADMIN", "OPERATOR")
                        .anyRequest().authenticated());

        http.addFilterBefore(forwarderAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, ForwarderAuthFilter.class);

        return http.build();
    }

}
