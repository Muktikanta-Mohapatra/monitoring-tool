package com.monitoring.logforwarder.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Configuration class for setting up the gRPC server that receives log events from remote LogForwarder agents.
 *
 * <p><b>PURPOSE:</b></p>
 * This class is the entry point for all gRPC-based communication between the Rust-based LogForwarder agents
 * and this Java middleware. It creates and configures a Netty-based gRPC server that listens for incoming
 * bidirectional streaming connections from log forwarders.
 *
 * <p><b>ARCHITECTURE CONTEXT:</b></p>
 * <pre>
 * LogForwarder (Rust)                         Middleware (Java)
 * ┌─────────────────┐                        ┌─────────────────────────────────┐
 * │ GrpcOutputSender│ ──── gRPC/HTTP2 ────▶ │ GrpcConfiguration (this class) │
 * │ grpc_sender.rs  │                        │         ↓                       │
 * └─────────────────┘                        │ ForwarderGrpcService            │
 *                                            │         ↓                       │
 *                                            │ EventService → Kafka → ClickHouse│
 *                                            └─────────────────────────────────┘
 * </pre>
 *
 * <p><b>WHEN THIS CLASS IS LOADED:</b></p>
 * <ul>
 *   <li>Spring Boot automatically loads this class during application startup due to {@code @Configuration}</li>
 *   <li>The {@link #grpcServer()} bean is created and the gRPC server starts listening immediately</li>
 *   <li>Server port is configured via {@code grpc.server.port} property (typically 50051)</li>
 * </ul>
 *
 * <p><b>PROTOCOL DEFINITION:</b></p>
 * The gRPC service contract is defined in {@code src/main/proto/forwarder.proto} which defines:
 * <ul>
 *   <li>{@code SendEvents(stream EventBatch) returns (stream AckResponse)} - Main event ingestion</li>
 *   <li>{@code HealthCheck(HealthCheckRequest) returns (HealthCheckResponse)} - Health monitoring</li>
 *   <li>{@code GetCapacity(CapacityRequest) returns (CapacityResponse)} - Backpressure signaling</li>
 * </ul>
 *
 * <p><b>RELATED COMPONENTS:</b></p>
 * <ul>
 *   <li>{@link ForwarderGrpcService} - The actual service implementation that handles incoming events</li>
 *   <li>{@code logforwarder/src/outputs/grpc_sender.rs} - Rust client that connects to this server</li>
 *   <li>{@code logforwarder/src/network/connection_pool.rs} - Manages gRPC channel connections</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ForwarderGrpcService
 * @see io.grpc.Server
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcConfiguration {

    /**
     * The port number on which the gRPC server listens for incoming connections.
     * 
     * <p><b>Configuration:</b> Set via {@code grpc.server.port} in application.yml/properties</p>
     * <p><b>Default:</b> Typically 50051 (standard gRPC port)</p>
     * <p><b>Used by:</b> LogForwarder agents connect to this port using the indexer configuration</p>
     */
    @Value("${grpc.server.port}")
    private int grpcPort;

    /**
     * The gRPC service implementation that handles all incoming ForwarderService RPC calls.
     * Injected by Spring's constructor injection via {@code @RequiredArgsConstructor}.
     */
    private final ForwarderGrpcService forwarderGrpcService;

    /**
     * Creates and starts the gRPC server as a Spring-managed bean.
     *
     * <p><b>PURPOSE:</b></p>
     * This method initializes a Netty-based gRPC server that:
     * <ul>
     *   <li>Binds to the configured port ({@code grpc.server.port})</li>
     *   <li>Registers the {@link ForwarderGrpcService} to handle incoming RPCs</li>
     *   <li>Starts accepting connections immediately upon bean creation</li>
     *   <li>Registers a JVM shutdown hook for graceful termination</li>
     * </ul>
     *
     * <p><b>LIFECYCLE:</b></p>
     * <pre>
     * Application Start → @Bean method called → server.start() → Accepting connections
     *                                                                    ↓
     * Application Stop  ← Shutdown hook      ← JVM shutdown    ← SIGTERM/SIGINT
     *                          ↓
     *                   server.shutdown() → await 5s → server.shutdownNow() if needed
     * </pre>
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>Spring IoC container during application context initialization</li>
     *   <li>Only called once per application lifecycle</li>
     * </ul>
     *
     * <p><b>CONNECTION HANDLING:</b></p>
     * Once started, the server handles connections from LogForwarder agents:
     * <ol>
     *   <li>LogForwarder's {@code GrpcOutputSender} opens a gRPC channel</li>
     *   <li>Calls {@code SendEvents} RPC with streaming EventBatch messages</li>
     *   <li>This server processes each batch and returns AckResponse</li>
     * </ol>
     *
     * @return The started gRPC {@link Server} instance
     * @throws IOException if the server fails to bind to the specified port (port in use, permission denied, etc.)
     */
    @Bean
    public Server grpcServer() throws IOException {
        log.info("Starting gRPC server on port: {}", grpcPort);

        Server server = NettyServerBuilder.forPort(grpcPort)
            .addService(forwarderGrpcService)
            .build();

        server.start();
        log.info("gRPC server started successfully on port {}", grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server");
            try {
                server.shutdown();
                if (!server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Error shutting down gRPC server", e);
                server.shutdownNow();
            }
        }));

        return server;
    }
}
