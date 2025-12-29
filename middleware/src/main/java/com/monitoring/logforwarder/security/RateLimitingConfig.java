package com.monitoring.logforwarder.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for rate limiting interceptor registration.
 *
 * <p><b>Purpose:</b> Registers the rate limiting interceptor for API endpoints
 * with exclusions for authentication and health check endpoints.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Configuration
public class RateLimitingConfig implements WebMvcConfigurer {

    private final RateLimitingInterceptor rateLimitingInterceptor;

    public RateLimitingConfig(RateLimitingInterceptor rateLimitingInterceptor) {
        this.rateLimitingInterceptor = rateLimitingInterceptor;
        log.info("RateLimitingConfig initialized");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("Registering rate limiting interceptors");
        
        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/actuator/**",
                        "/api/v1/health"
                );
    }
}
