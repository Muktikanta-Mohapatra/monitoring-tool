package com.monitoring.logforwarder.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for API rate limiting.
 *
 * <p><b>Purpose:</b> Defines rate limiting thresholds for API endpoints including
 * global limits and per-endpoint overrides to prevent abuse and ensure fair usage.</p>
 *
 * <p><b>Configuration:</b></p>
 * <pre>
 * app:
 *   rate-limit:
 *     enabled: true
 *     requests-per-minute: 1000
 *     requests-per-hour: 50000
 *     endpoints:
 *       /api/v1/events/batch:
 *         per-minute: 100
 *         per-hour: 5000
 * </pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see RateLimitingInterceptor
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    
    private boolean enabled = true;
    private int requestsPerMinute = 1000;
    private int requestsPerHour = 50000;
    private Map<String, EndpointLimit> endpoints = new HashMap<>();
}
