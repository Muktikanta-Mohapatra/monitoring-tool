package com.monitoring.logforwarder.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    
    private boolean enabled = true;
    private int requestsPerMinute = 1000;
    private int requestsPerHour = 50000;
    private Map<String, EndpointLimit> endpoints = new HashMap<>();

    @Data
    public static class EndpointLimit {
        private int perMinute;
        private int perHour;
    }
}
