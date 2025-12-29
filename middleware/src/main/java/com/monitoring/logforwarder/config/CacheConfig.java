package com.monitoring.logforwarder.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for multi-tier caching infrastructure.
 *
 * <p><b>Purpose:</b> Configures and provides multiple cache managers for the application,
 * implementing a multi-tier caching strategy with both local (Caffeine) and distributed
 * (Redis) caching. This optimizes performance by reducing database and Elasticsearch queries
 * while maintaining data consistency across application instances.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Primary cache manager uses Redis for distributed caching across instances</li>
 *   <li>Local Caffeine cache for high-speed, in-memory caching of frequently accessed data</li>
 *   <li>Multiple specialized cache configurations with different TTL values</li>
 *   <li>Cache names: searchResults (30min), metrics (5min), forwarders (15min), alertRules (1hr), users (1hr)</li>
 *   <li>Null values are disabled to prevent cache pollution</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Using caching in a service method
 * &#64;Cacheable(value = "searchResults", key = "#query.hashCode()")
 * public SearchResultDTO search(SearchQueryDTO query) {
 *     // Expensive search operation
 *     return elasticsearchClient.search(query);
 * }
 *
 * // Evicting cache entries
 * &#64;CacheEvict(value = "forwarders", allEntries = true)
 * public void updateForwarder(ForwarderDTO forwarder) {
 *     // Update operation
 * }
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see CacheManager
 * @see RedisCacheManager
 * @see CaffeineCacheManager
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates the primary Redis-based cache manager for distributed caching.
     *
     * <p><b>Purpose:</b> Provides the main cache manager used throughout the application
     * for distributed caching. Multiple cache configurations are defined with varying TTL
     * values optimized for different data types.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Default TTL: 1 hour</li>
     *   <li>searchResults cache: 30 minutes TTL</li>
     *   <li>metrics cache: 5 minutes TTL</li>
     *   <li>forwarders cache: 15 minutes TTL</li>
     *   <li>alertRules and users caches: 1 hour TTL</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory for establishing Redis connections
     * @return configured Redis cache manager as the primary cache manager
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis Cache Manager");
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();

        RedisCacheConfiguration searchResultsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        RedisCacheConfiguration metricsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        RedisCacheConfiguration forewarderConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("searchResults", searchResultsConfig)
                .withCacheConfiguration("metrics", metricsConfig)
                .withCacheConfiguration("forwarders", forewarderConfig)
                .withCacheConfiguration("alertRules", defaultConfig)
                .withCacheConfiguration("users", defaultConfig)
                .build();
    }

    /**
     * Creates a local in-memory Caffeine cache manager for high-speed caching.
     *
     * <p><b>Purpose:</b> Provides a local cache manager using Caffeine for scenarios
     * requiring ultra-low latency access. Ideal for frequently accessed, rarely changing data
     * that doesn't need distributed consistency.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Maximum 10,000 entries</li>
     *   <li>10-minute expiration after write</li>
     *   <li>Statistics recording enabled for monitoring</li>
     *   <li>Caches: queryCache, aggregationCache, userCache, configCache</li>
     * </ul>
     *
     * @return configured Caffeine cache manager for local caching
     */
    @Bean(name = "localCacheManager")
    public CacheManager localCacheManager() {
        log.info("Configuring Local Caffeine Cache Manager");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "queryCache",
                "aggregationCache",
                "userCache",
                "configCache"
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .expireAfterWrite(10, TimeUnit.MINUTES));

        return cacheManager;
    }

    /**
     * Creates a distributed cache manager for multi-instance deployment scenarios.
     *
     * <p><b>Purpose:</b> Provides a cache manager specifically designed for data that must
     * be shared and consistent across multiple application instances, such as distributed
     * locks and session data.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Default TTL: 30 minutes</li>
     *   <li>distributedLocks cache: 5 minutes TTL</li>
     *   <li>sessionCache: 24 hours TTL</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory
     * @return configured Redis cache manager for distributed caching
     */
    @Bean(name = "distributedCacheManager")
    public CacheManager distributedCacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Distributed Cache Manager for multi-instance setup");
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("distributedLocks", 
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("sessionCache",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(24)))
                .build();
    }

    /**
     * Creates a short-lived cache manager for temporary or volatile data.
     *
     * <p><b>Purpose:</b> Provides a cache manager with very short TTL for data that changes
     * frequently or is only valid for brief periods, such as rate limiting counters or
     * temporary computation results.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>TTL: 2 minutes</li>
     *   <li>Suitable for real-time metrics and transient data</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory
     * @return configured Redis cache manager with short TTL
     */
    @Bean(name = "shortLivedCache")
    public CacheManager shortLivedCache(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Short-lived Cache Manager for temporary data");
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(2))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Creates a long-lived cache manager for reference and static data.
     *
     * <p><b>Purpose:</b> Provides a cache manager with extended TTL for data that changes
     * infrequently, such as reference data, configuration settings, or static lookup tables.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>TTL: 6 hours</li>
     *   <li>Suitable for static configuration and reference data</li>
     *   <li>Reduces database load for rarely-changing data</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory
     * @return configured Redis cache manager with extended TTL
     */
    @Bean(name = "longLivedCache")
    public CacheManager longLivedCache(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Long-lived Cache Manager for reference data");
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(6))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
