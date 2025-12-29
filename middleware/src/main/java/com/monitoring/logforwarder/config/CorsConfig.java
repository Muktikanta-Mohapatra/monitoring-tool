package com.monitoring.logforwarder.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for Cross-Origin Resource Sharing (CORS) settings.
 *
 * <p><b>Purpose:</b> Configures CORS policies to enable secure cross-origin requests
 * from frontend applications. This allows the web dashboard and other clients to
 * communicate with the API while maintaining security controls.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Allowed origins configurable via {@code app.cors.allowed-origins} property</li>
 *   <li>Supports GET, POST, PUT, DELETE, PATCH, OPTIONS methods</li>
 *   <li>Allows all headers in requests</li>
 *   <li>Exposes Authorization, Content-Type, and X-Total-Count headers in responses</li>
 *   <li>Credentials (cookies, authorization headers) are allowed</li>
 *   <li>Pre-flight cache duration: 1 hour</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * app:
 *   cors:
 *     allowed-origins: http://localhost:3000,https://dashboard.example.com
 *
 * // Frontend can now make cross-origin requests:
 * fetch('http://api.example.com/api/v1/events', {
 *     method: 'GET',
 *     credentials: 'include',
 *     headers: { 'Authorization': 'Bearer token123' }
 * });
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see CorsConfiguration
 * @see CorsConfigurationSource
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Creates and configures the CORS configuration source.
     *
     * <p><b>Purpose:</b> Defines the CORS policy applied to all API endpoints,
     * specifying which origins, methods, and headers are permitted for cross-origin requests.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Origins are parsed from comma-separated configuration value</li>
     *   <li>Applied to all URL patterns (/**)</li>
     *   <li>Pre-flight response cached for 3600 seconds</li>
     * </ul>
     *
     * @return configured CORS configuration source for the application
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        
        log.info("CORS configured with allowed origins: {}", origins);
        
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
