package com.monitoring.logforwarder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Log Forwarder Middleware Application.
 *
 * <p><b>Purpose:</b> This class bootstraps and launches the Spring Boot application that serves as
 * the central middleware component for log forwarding, event processing, and monitoring operations.
 * It acts as the orchestrator for collecting logs from various forwarders, processing events,
 * storing data in Elasticsearch and MySQL, and providing real-time updates via WebSocket.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>{@code @SpringBootApplication} - Enables auto-configuration, component scanning, and configuration properties</li>
 *   <li>{@code @EnableCaching} - Activates Spring's caching infrastructure with Redis and Caffeine cache managers</li>
 *   <li>{@code @EnableAsync} - Enables asynchronous method execution for non-blocking operations</li>
 *   <li>{@code @EnableScheduling} - Activates scheduled task execution for batch processing, cleanup, and metrics aggregation</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Running the application from command line:
 * java -jar logforwarder.jar --spring.profiles.active=prod
 *
 * // Or programmatically:
 * SpringApplication app = new SpringApplication(LogForwarderApplication.class);
 * app.setAdditionalProfiles("dev");
 * app.run(args);
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
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
