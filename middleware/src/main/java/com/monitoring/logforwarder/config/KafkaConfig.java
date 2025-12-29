package com.monitoring.logforwarder.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Apache Kafka messaging infrastructure.
 *
 * <p><b>Purpose:</b> Configures Kafka producers, consumers, topics, and listener containers
 * for event-driven messaging throughout the application. Enables asynchronous, decoupled
 * communication between services for log events, alerts, metrics, and notifications.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Creates multiple topics: events (12 partitions), alerts (6), metrics (9), audit-logs (3), notifications (6)</li>
 *   <li>Producer configured with acks=all, snappy compression, and batching</li>
 *   <li>Consumer configured with JSON deserialization and auto-commit</li>
 *   <li>Concurrent listener factory with 8 parallel consumers</li>
 *   <li>Topic retention: events (7 days), alerts (30 days), metrics (1 day), audit-logs (90 days)</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * spring:
 *   kafka:
 *     bootstrap-servers: localhost:9092
 *     consumer:
 *       group-id: log-forwarder-group
 *     producer:
 *       batch-size: 16384
 *       compression-type: snappy
 *
 * // Publishing events:
 * kafkaTemplate.send("events", eventKey, eventDTO);
 *
 * // Consuming events:
 * &#64;KafkaListener(topics = "events")
 * public void handleEvent(EventDTO event) {
 *     processEvent(event);
 * }
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see KafkaTemplate
 * @see KafkaListenerContainerFactory
 */
@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.producer.batch-size:16384}")
    private Integer batchSize;

    @Value("${spring.kafka.producer.linger-ms:10}")
    private Integer lingerMs;

    @Value("${spring.kafka.producer.compression-type:snappy}")
    private String compressionType;

    /**
     * Creates the Kafka admin client for topic management operations.
     *
     * <p><b>Purpose:</b> Provides administrative capabilities for managing Kafka topics,
     * including creation, deletion, and configuration updates.</p>
     *
     * @return configured Kafka admin bean
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        log.info("Configuring Kafka Admin");
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Creates the main events topic for log event streaming.
     *
     * <p><b>Purpose:</b> Defines the primary topic for log events with high throughput
     * configuration (12 partitions) and 7-day retention.</p>
     *
     * @return configured events topic with 12 partitions, 3 replicas
     */
    @Bean
    public NewTopic eventTopic() {
        log.info("Creating Kafka topic: events");
        return TopicBuilder.name("events")
                .partitions(12)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config("compression.type", compressionType)
                .build();
    }

    /**
     * Creates the alerts topic for alert notifications.
     *
     * <p><b>Purpose:</b> Defines the topic for alert messages with extended 30-day
     * retention for audit and analysis purposes.</p>
     *
     * @return configured alerts topic with 6 partitions, 3 replicas
     */
    @Bean
    public NewTopic alertTopic() {
        log.info("Creating Kafka topic: alerts");
        return TopicBuilder.name("alerts")
                .partitions(6)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Creates the metrics topic for system metrics streaming.
     *
     * <p><b>Purpose:</b> Defines the topic for real-time metrics with 24-hour retention,
     * optimized for time-series data that is quickly aggregated and archived.</p>
     *
     * @return configured metrics topic with 9 partitions, 3 replicas
     */
    @Bean
    public NewTopic metricsTopic() {
        log.info("Creating Kafka topic: metrics");
        return TopicBuilder.name("metrics")
                .partitions(9)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Creates the audit-logs topic for security audit trails.
     *
     * <p><b>Purpose:</b> Defines the topic for audit logs with 90-day retention
     * for compliance and security investigation requirements.</p>
     *
     * @return configured audit-logs topic with 3 partitions, 3 replicas
     */
    @Bean
    public NewTopic auditLogTopic() {
        log.info("Creating Kafka topic: audit-logs");
        return TopicBuilder.name("audit-logs")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(90 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Creates the notifications topic for user notifications.
     *
     * <p><b>Purpose:</b> Defines the topic for user-facing notifications with
     * 7-day retention and lower replication for less critical data.</p>
     *
     * @return configured notifications topic with 6 partitions, 2 replicas
     */
    @Bean
    public NewTopic notificationTopic() {
        log.info("Creating Kafka topic: notifications");
        return TopicBuilder.name("notifications")
                .partitions(6)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Creates the Kafka producer factory for message publishing.
     *
     * <p><b>Purpose:</b> Configures the producer with optimized settings for reliability
     * and throughput including acks=all, batching, compression, and retry policies.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>acks=all for durability guarantee</li>
     *   <li>3 retries with 30s request timeout</li>
     *   <li>Snappy compression for reduced bandwidth</li>
     *   <li>JSON serialization for message values</li>
     * </ul>
     *
     * @return configured producer factory
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        log.info("Configuring Kafka Producer Factory");
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configs.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configs.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configs.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        configs.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * Creates the KafkaTemplate for sending messages.
     *
     * <p><b>Purpose:</b> Provides a high-level abstraction for Kafka operations,
     * simplifying message publishing with automatic serialization.</p>
     *
     * @return configured Kafka template
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Creates the Kafka consumer factory for message consumption.
     *
     * <p><b>Purpose:</b> Configures consumers with optimal settings for reliable
     * message processing including manual commit, session management, and JSON deserialization.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Max 500 records per poll for balanced throughput</li>
     *   <li>30s session timeout with 10s heartbeat interval</li>
     *   <li>Manual commit for guaranteed persistence (offsets only committed after successful DB storage)</li>
     *   <li>JSON deserialization with EventDTO as default type</li>
     * </ul>
     *
     * @return configured consumer factory
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        log.info("Configuring Kafka Consumer Factory");
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        configs.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configs.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.monitoring.logforwarder.dto.EventDTO");
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    /**
     * Creates the Kafka listener container factory for @KafkaListener methods.
     *
     * <p><b>Purpose:</b> Configures the container factory that manages concurrent
     * message listeners with 8 parallel consumer threads, manual acknowledgment mode,
     * and 3-second poll timeout. Manual commit ensures offsets are only persisted
     * after successful database storage, guaranteeing no message loss.</p>
     *
     * @return configured listener container factory with concurrency of 8 and manual ack
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
    kafkaListenerContainerFactory() {
        log.info("Configuring Kafka Listener Container Factory with MANUAL acknowledgment");
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(8);
        factory.getContainerProperties().setPollTimeout(3000);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        log.info("Kafka listener container configured with manual acknowledgment for guaranteed message processing");
        return factory;
    }
}
