package com.monitoring.logforwarder.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter for LogForwarder agent API requests.
 *
 * <p><b>Purpose:</b> Implements token bucket rate limiting for the event batch ingestion
 * endpoint to prevent any single forwarder from overwhelming the system.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses Bucket4j token bucket algorithm for accurate rate limiting</li>
 *   <li>Caffeine cache for in-memory bucket storage per API key</li>
 *   <li>Optional Redis integration for distributed rate limiting</li>
 *   <li>Configurable events per minute limit (default: 10000)</li>
 *   <li>Rate limit can be disabled via configuration</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>{@code app.forwarder.rate-limit.events-per-minute} - Max events per minute</li>
 *   <li>{@code app.forwarder.rate-limit.enabled} - Enable/disable rate limiting</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ForwarderAuthFilter
 */
@Slf4j
@Service
public class ForwarderRateLimiter {

    @Autowired(required = false)
    private RedisTemplate<String, Long> redisTemplate;

    @Value("${app.forwarder.rate-limit.events-per-minute:10000}")
    private int eventsPerMinute;

    @Value("${app.forwarder.rate-limit.enabled:true}")
    private boolean rateLimitingEnabled;

    private final LoadingCache<String, Bucket> buckets;

    public ForwarderRateLimiter() {
        this.buckets = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(apiKey -> createNewBucket());
    }

    public boolean isAllowed(String apiKey, int tokenCost) {
        if (!rateLimitingEnabled) {
            return true;
        }

        try {
            if (redisTemplate != null) {
                return checkRedisLimit(apiKey, tokenCost);
            } else {
                return checkLocalLimit(apiKey, tokenCost);
            }
        } catch (Exception e) {
            log.error("Error checking rate limit for API key", e);
            return true;
        }
    }

    public boolean isAllowed(String apiKey) {
        return isAllowed(apiKey, 1);
    }

    private boolean checkRedisLimit(String apiKey, int tokenCost) {
        String redisKey = "rate_limit:forwarder:" + apiKey;
        Long currentCount = redisTemplate.opsForValue().increment(redisKey, tokenCost);

        if (currentCount == tokenCost) {
            redisTemplate.expire(redisKey, 1, TimeUnit.MINUTES);
        }

        if (currentCount <= eventsPerMinute) {
            return true;
        } else {
            log.warn("Rate limit exceeded for API key (Redis): {} tokens used", currentCount);
            return false;
        }
    }

    private boolean checkLocalLimit(String apiKey, int tokenCost) {
        Bucket bucket = buckets.get(apiKey);
        boolean allowed = bucket.tryConsume(tokenCost);
        if (!allowed) {
            log.warn("Rate limit exceeded for API key (Local): {}", apiKey);
        }
        return allowed;
    }

    private Bucket createNewBucket() {
        Bandwidth bandwidth = Bandwidth.classic(eventsPerMinute, 
            Refill.intervally(eventsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder()
            .addLimit(bandwidth)
            .build();
    }

    public long getRemainingTokens(String apiKey) {
        try {
            if (redisTemplate != null) {
                String redisKey = "rate_limit:forwarder:" + apiKey;
                Long used = redisTemplate.opsForValue().get(redisKey);
                if (used == null) {
                    return eventsPerMinute;
                }
                return Math.max(0, eventsPerMinute - used);
            } else {
                Bucket bucket = buckets.get(apiKey);
                return bucket.getAvailableTokens();
            }
        } catch (Exception e) {
            log.error("Error getting remaining tokens", e);
            return eventsPerMinute;
        }
    }

    public void reset(String apiKey) {
        try {
            if (redisTemplate != null) {
                String redisKey = "rate_limit:forwarder:" + apiKey;
                redisTemplate.delete(redisKey);
            } else {
                buckets.invalidate(apiKey);
            }
            log.debug("Rate limit reset for API key: {}", apiKey);
        } catch (Exception e) {
            log.error("Error resetting rate limit", e);
        }
    }
}
