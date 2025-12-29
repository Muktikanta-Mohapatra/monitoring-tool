package com.monitoring.logforwarder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for Redis connectivity and template setup.
 *
 * <p><b>Purpose:</b> Configures Redis connection factory and template for distributed
 * caching, session management, and real-time data operations. Provides the foundation
 * for high-performance, in-memory data storage across application instances.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses Lettuce as the Redis client for non-blocking I/O</li>
 *   <li>Configurable host, port, and connection pool settings</li>
 *   <li>String serializer for keys, JSON serializer for values</li>
 *   <li>Supports hash operations with consistent serialization</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * spring:
 *   redis:
 *     host: localhost
 *     port: 6379
 *     jedis:
 *       pool:
 *         max-active: 20
 *         max-idle: 10
 *
 * // Using RedisTemplate:
 * &#64;Autowired
 * private RedisTemplate<String, Object> redisTemplate;
 *
 * redisTemplate.opsForValue().set("key", eventDTO);
 * EventDTO cached = (EventDTO) redisTemplate.opsForValue().get("key");
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see RedisTemplate
 * @see LettuceConnectionFactory
 */
@Configuration
@EnableRedisRepositories(basePackages = "com.monitoring.logforwarder.repository.redis")
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.jedis.pool.max-active:20}")
    private int maxActive;

    @Value("${spring.redis.jedis.pool.max-idle:10}")
    private int maxIdle;

    /**
     * Creates the Redis connection factory using Lettuce client.
     *
     * <p><b>Purpose:</b> Provides the connection factory for establishing connections
     * to Redis server, using Lettuce for non-blocking, reactive I/O operations.</p>
     *
     * @return configured Lettuce connection factory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    /**
     * Creates the RedisTemplate for Redis operations.
     *
     * <p><b>Purpose:</b> Provides a configured template for Redis data operations with
     * appropriate serializers for keys (String) and values (JSON).</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>String serializer for keys and hash keys</li>
     *   <li>JSON serializer for values and hash values</li>
     *   <li>Supports complex object serialization</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory
     * @return configured Redis template for String keys and Object values
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
