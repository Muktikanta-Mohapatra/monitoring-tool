package com.monitoring.logforwarder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Log Forwarder Middleware Application.
 *
 * <p><b>PURPOSE:</b></p>
 * This class bootstraps and launches the Spring Boot application that serves as the central
 * middleware component for log forwarding, event processing, and monitoring operations.
 * It acts as the orchestrator for collecting logs from LogForwarder agents, processing events,
 * storing data in ClickHouse and PostgreSQL, and providing real-time updates via WebSocket.
 *
 * <p><b>SYSTEM ARCHITECTURE OVERVIEW:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                    LOG FORWARDER MIDDLEWARE - COMPLETE ARCHITECTURE             │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  ┌───────────────────────────────────────────────────────────────────────┐      │
 * │  │                     INGESTION LAYER                                   │      │
 * │  │  ┌─────────────────┐         ┌─────────────────┐                      │      │
 * │  │  │ gRPC Server     │         │ HTTP REST API   │                      │      │
 * │  │  │ (port 50051)    │         │ (port 8080)     │                      │      │
 * │  │  │ ForwarderGrpc   │         │ EventController │                      │      │
 * │  │  │ Service         │         │                 │                      │      │
 * │  │  └────────┬────────┘         └────────┬────────┘                      │      │
 * │  │           └──────────────┬────────────┘                               │      │
 * │  │                          ▼                                            │      │
 * │  │                   ┌──────────────┐                                    │      │
 * │  │                   │ EventService │                                    │      │
 * │  │                   └──────┬───────┘                                    │      │
 * │  └──────────────────────────┼────────────────────────────────────────────┘      │
 * │                             ▼                                                   │
 * │  ┌───────────────────────────────────────────────────────────────────────┐      │
 * │  │                     MESSAGING LAYER                                   │      │
 * │  │  ┌─────────────────┐         ┌─────────────────┐                      │      │
 * │  │  │ EventBatch      │         │ EventProducer   │                      │      │
 * │  │  │ Processor       │ ──────▶ │ (Kafka)         │                      │      │
 * │  │  └─────────────────┘         └────────┬────────┘                      │      │
 * │  │                                       │                               │      │
 * │  │                          Kafka Cluster (events, alerts, metrics)      │      │
 * │  │                                       │                               │      │
 * │  │                              ┌────────┴────────┐                      │      │
 * │  │                              │ EventConsumer   │                      │      │
 * │  │                              │ (10 threads)    │                      │      │
 * │  │                              └────────┬────────┘                      │      │
 * │  └──────────────────────────────────────┼────────────────────────────────┘      │
 * │                                         ▼                                       │
 * │  ┌───────────────────────────────────────────────────────────────────────┐      │
 * │  │                     PERSISTENCE LAYER                                 │      │
 * │  │  ┌─────────────────┐         ┌─────────────────┐                      │      │
 * │  │  │ ClickHouse      │         │ PostgreSQL      │                      │      │
 * │  │  │ (Events)        │         │ (Users, Alerts, │                      │      │
 * │  │  │                 │         │  Forwarders)    │                      │      │
 * │  │  └─────────────────┘         └─────────────────┘                      │      │
 * │  └───────────────────────────────────────────────────────────────────────┘      │
 * │                                                                                 │
 * │  ┌───────────────────────────────────────────────────────────────────────┐      │
 * │  │                     REAL-TIME & CACHING LAYER                         │      │
 * │  │  ┌─────────────────┐         ┌─────────────────┐                      │      │
 * │  │  │ WebSocket       │         │ Redis           │                      │      │
 * │  │  │ (Dashboard)     │         │ (Caching)       │                      │      │
 * │  │  └─────────────────┘         └─────────────────┘                      │      │
 * │  └───────────────────────────────────────────────────────────────────────┘      │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>KEY COMPONENTS INITIALIZED:</b></p>
 * <ul>
 *   <li><b>gRPC Server:</b> {@link com.monitoring.logforwarder.grpc.GrpcConfiguration} - Port 50051</li>
 *   <li><b>REST API:</b> Embedded Tomcat - Port 8080</li>
 *   <li><b>Kafka:</b> Producer & Consumer for async event processing</li>
 *   <li><b>ClickHouse:</b> Time-series database for event storage</li>
 *   <li><b>PostgreSQL:</b> Relational database for users, alerts, forwarders</li>
 *   <li><b>Redis:</b> Caching and session management</li>
 *   <li><b>WebSocket:</b> Real-time dashboard updates</li>
 * </ul>
 *
 * <p><b>SPRING BOOT ANNOTATIONS:</b></p>
 * <ul>
 *   <li>{@code @SpringBootApplication} - Enables auto-configuration, component scanning, configuration properties</li>
 *   <li>{@code @EnableCaching} - Activates caching infrastructure (Redis + Caffeine)</li>
 *   <li>{@code @EnableAsync} - Enables async method execution (CompletableFuture support)</li>
 *   <li>{@code @EnableScheduling} - Activates scheduled tasks (batch flush, cleanup, metrics)</li>
 * </ul>
 *
 * <p><b>STARTUP SEQUENCE:</b></p>
 * <ol>
 *   <li>Spring context initialization</li>
 *   <li>Database connection pools created (ClickHouse, PostgreSQL)</li>
 *   <li>Kafka producer/consumer initialized</li>
 *   <li>Redis connection established</li>
 *   <li>gRPC server started (port 50051)</li>
 *   <li>Embedded Tomcat started (port 8080)</li>
 *   <li>Scheduled tasks activated</li>
 *   <li>Application ready to accept connections</li>
 * </ol>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see com.monitoring.logforwarder.grpc.GrpcConfiguration
 * @see com.monitoring.logforwarder.config.KafkaConfig
 * @see com.monitoring.logforwarder.config.ClickHouseDataSourceConfig
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class LogForwarderApplication {

    /**
     * Application entry point that initializes and starts the Spring Boot application context.
     *
     * <p><b>Purpose:</b> Bootstraps the entire application by creating the Spring application context,
     * initializing all beans, establishing database connections, connecting to Kafka and Redis,
     * and starting the embedded web server.</p>
     *
     * <p><b>Technical Details:</b> Uses {@link SpringApplication#run(Class, String...)} to create
     * and refresh the application context. The method blocks until the application is shut down.
     * All configuration is loaded from application.yml and environment-specific profiles.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // Standard launch
     * LogForwarderApplication.main(new String[]{});
     *
     * // With custom arguments
     * LogForwarderApplication.main(new String[]{"--server.port=9090", "--spring.profiles.active=dev"});
     * }</pre>
     *
     * @param args command-line arguments passed to the application; supports Spring Boot properties
     *             (e.g., --server.port=8080, --spring.profiles.active=prod)
     */
    public static void main(String[] args) {
        SpringApplication.run(LogForwarderApplication.class, args);
    }
}
