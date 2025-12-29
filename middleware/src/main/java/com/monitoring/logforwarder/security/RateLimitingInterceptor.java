package com.monitoring.logforwarder.security;

import com.monitoring.logforwarder.config.properties.RateLimitProperties;
import com.monitoring.logforwarder.util.ClientInfoExtractor;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final RateLimitProperties rateLimitProperties;

    public RateLimitingInterceptor(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        String clientId = ClientInfoExtractor.getClientIp(request);
        String endpoint = getEndpoint(request);
        
        Bucket bucket = resolveBucket(clientId, endpoint);
        
        if (bucket.tryConsume(1)) {
            response.addHeader("X-Rate-Limit-Limit", String.valueOf(getLimit(endpoint)));
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            return true;
        } else {
            log.warn("Rate limit exceeded for client: {} on endpoint: {}", clientId, endpoint);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            return false;
        }
    }

    private Bucket resolveBucket(String clientId, String endpoint) {
        String key = clientId + ":" + endpoint;
        return bucketCache.computeIfAbsent(key, k -> createBucketForEndpoint(endpoint));
    }

    private Bucket createBucketForEndpoint(String endpoint) {
        RateLimitProperties.EndpointLimit limits = rateLimitProperties.getEndpoints().get(endpoint);
        int perMinute = limits != null ? limits.getPerMinute() : rateLimitProperties.getRequestsPerMinute();
        int perHour = limits != null ? limits.getPerHour() : rateLimitProperties.getRequestsPerHour();
        
        Bandwidth minuteLimit = Bandwidth.classic(perMinute, 
                Refill.intervally(perMinute, Duration.ofMinutes(1)));
        Bandwidth hourLimit = Bandwidth.classic(perHour, 
                Refill.intervally(perHour, Duration.ofHours(1)));
        
        return Bucket.builder()
                .addLimit(minuteLimit)
                .addLimit(hourLimit)
                .build();
    }

    private String getEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return "/" + parts[1] + "/" + parts[2] + "/" + parts[3];
        }
        return path;
    }

    private int getLimit(String endpoint) {
        RateLimitProperties.EndpointLimit limits = rateLimitProperties.getEndpoints().get(endpoint);
        if (limits != null) {
            return limits.getPerHour();
        }
        return rateLimitProperties.getRequestsPerHour();
    }
}
