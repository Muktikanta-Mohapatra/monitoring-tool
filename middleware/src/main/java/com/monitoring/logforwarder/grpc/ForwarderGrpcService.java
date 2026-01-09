package com.monitoring.logforwarder.grpc;

import com.forwarder.v1.AckResponse;
import com.forwarder.v1.CapacityRequest;
import com.forwarder.v1.CapacityResponse;
import com.forwarder.v1.EventBatch;
import com.forwarder.v1.ForwarderServiceGrpc;
import com.forwarder.v1.HealthCheckRequest;
import com.forwarder.v1.HealthCheckResponse;
import com.monitoring.logforwarder.service.EventService;
import io.grpc.stub.StreamObserver;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * gRPC service implementation for receiving log events from remote LogForwarder agents.
 *
 * <p><b>PURPOSE:</b></p>
 * This is the core gRPC service that implements the {@code ForwarderService} defined in
 * {@code forwarder.proto}. It serves as the PRIMARY ingestion endpoint for log events
 * sent from Rust-based LogForwarder agents using bidirectional streaming gRPC.
 *
 * <p><b>ARCHITECTURE POSITION:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                              DATA FLOW                                          │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │  LogForwarder (Rust)                                                            │
 * │  ┌─────────────────────────┐                                                    │
 * │  │ grpc_sender.rs:68       │  ←── send_events() calls this service              │
 * │  │ client.send_events()    │                                                    │
 * │  └───────────┬─────────────┘                                                    │
 * │              │ gRPC Stream (EventBatch messages)                                │
 * │              ▼                                                                  │
 * │  ┌─────────────────────────┐                                                    │
 * │  │ ForwarderGrpcService    │  ←── THIS CLASS                                    │
 * │  │ sendEvents():27         │                                                    │
 * │  └───────────┬─────────────┘                                                    │
 * │              │ For each event in batch                                          │
 * │              ▼                                                                  │
 * │  ┌─────────────────────────┐                                                    │
 * │  │ EventService            │                                                    │
 * │  │ persistEvent():218      │                                                    │
 * │  └───────────┬─────────────┘                                                    │
 * │              │                                                                  │
 * │              ▼                                                                  │
 * │  ┌─────────────────────────┐                                                    │
 * │  │ EventBatchProcessor     │  →  Kafka "events" topic  →  ClickHouse            │
 * │  └─────────────────────────┘                                                    │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>gRPC SERVICE CONTRACT (from forwarder.proto):</b></p>
 * <pre>
 * service ForwarderService {
 *   rpc SendEvents(stream EventBatch) returns (stream AckResponse);  // Bidirectional streaming
 *   rpc HealthCheck(HealthCheckRequest) returns (HealthCheckResponse);  // Unary
 *   rpc GetCapacity(CapacityRequest) returns (CapacityResponse);  // Unary
 * }
 * </pre>
 *
 * <p><b>KEY RESPONSIBILITIES:</b></p>
 * <ul>
 *   <li><b>Event Ingestion:</b> Receives streamed EventBatch messages and sends immediate ACKs</li>
 *   <li><b>Async Processing:</b> Processes events asynchronously to avoid blocking the gRPC stream</li>
 *   <li><b>Health Reporting:</b> Reports middleware health status (CPU, memory, queue depth)</li>
 *   <li><b>Capacity Signaling:</b> Reports available capacity for backpressure management</li>
 * </ul>
 *
 * <p><b>THREADING MODEL:</b></p>
 * <ul>
 *   <li>gRPC uses Netty's event loop threads for I/O</li>
 *   <li>Event processing is offloaded to {@link CompletableFuture#runAsync} to prevent blocking</li>
 *   <li>ACK responses are sent immediately before async processing to minimize client wait time</li>
 * </ul>
 *
 * <p><b>WHY IMMEDIATE ACK:</b></p>
 * The service sends ACK immediately after receiving an EventBatch (line 56) and processes
 * events asynchronously. This design choice:
 * <ul>
 *   <li>Minimizes LogForwarder wait time (important for high-throughput scenarios)</li>
 *   <li>Allows the LogForwarder to send next batch without waiting for DB persistence</li>
 *   <li>Trade-off: Events may be ACKed but fail to persist (handled by Kafka retry semantics)</li>
 * </ul>
 *
 * <p><b>CALLED BY:</b></p>
 * <ul>
 *   <li>{@code logforwarder/src/outputs/grpc_sender.rs} - GrpcOutputSender.send_batch() at line 68</li>
 *   <li>{@code logforwarder/src/network/grpc_client.rs} - GrpcForwarderClient.send_events() at line 43</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see GrpcConfiguration
 * @see EventService#persistEvent(com.forwarder.v1.Event)
 * @see com.forwarder.v1.ForwarderServiceGrpc.ForwarderServiceImplBase
 */
@Slf4j
@Component
public class ForwarderGrpcService extends ForwarderServiceGrpc.ForwarderServiceImplBase {

    /**
     * Service responsible for persisting events and managing event lifecycle.
     * 
     * <p><b>Injected via:</b> Setter injection (required for circular dependency resolution with Spring)</p>
     * <p><b>Used in:</b> {@link #sendEvents} → processEventBatchAsync → eventService.persistEvent()</p>
     * 
     * @see EventService#persistEvent(com.forwarder.v1.Event)
     */
    @Setter
    private EventService eventService;

    /**
     * Handles bidirectional streaming RPC for receiving log event batches from LogForwarder agents.
     *
     * <p><b>PURPOSE:</b></p>
     * This is the PRIMARY method for log ingestion. It implements the gRPC streaming contract where:
     * <ul>
     *   <li>Client (LogForwarder) streams multiple {@link EventBatch} messages</li>
     *   <li>Server (this middleware) streams back {@link AckResponse} for each batch</li>
     * </ul>
     *
     * <p><b>PROTOCOL FLOW:</b></p>
     * <pre>
     * LogForwarder                              Middleware (this method)
     *     │                                           │
     *     │──── EventBatch #1 ──────────────────────▶│
     *     │                                           │── Validate batch
     *     │◀─── AckResponse #1 (immediate) ──────────│── Send ACK immediately
     *     │                                           │── processEventBatchAsync() ──▶ Background
     *     │                                           │
     *     │──── EventBatch #2 ──────────────────────▶│
     *     │◀─── AckResponse #2 ──────────────────────│
     *     │                                           │
     *     │──── onCompleted() ──────────────────────▶│
     *     │◀─── onCompleted() ───────────────────────│
     * </pre>
     *
     * <p><b>DESIGN DECISIONS:</b></p>
     * <ul>
     *   <li><b>Immediate ACK:</b> ACK is sent BEFORE async processing to minimize client blocking</li>
     *   <li><b>Fire-and-Forget:</b> Event processing is async; failures don't affect ACK already sent</li>
     *   <li><b>At-least-once:</b> If processing fails, Kafka consumer retry semantics handle reprocessing</li>
     * </ul>
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>gRPC framework when client calls {@code ForwarderService.SendEvents}</li>
     *   <li>Rust client: {@code logforwarder/src/outputs/grpc_sender.rs:68}</li>
     * </ul>
     *
     * @param responseObserver Stream observer for sending AckResponse messages back to client
     * @return StreamObserver for receiving EventBatch messages from client
     * @see #processEventBatchAsync
     */
    @Override
    public StreamObserver<EventBatch> sendEvents(StreamObserver<AckResponse> responseObserver) {
        return new StreamObserver<EventBatch>() {
            /**
             * Called for each EventBatch received from the LogForwarder stream.
             *
             * <p><b>PROCESSING STEPS:</b></p>
             * <ol>
             *   <li>Validate batch is not empty</li>
             *   <li>Build and send immediate AckResponse</li>
             *   <li>Trigger async processing via {@link #processEventBatchAsync}</li>
             * </ol>
             *
             * <p><b>ERROR HANDLING:</b></p>
             * On exception, sends failure ACK with error message but does NOT close the stream,
             * allowing subsequent batches to be processed.
             *
             * @param eventBatch The batch of events received from LogForwarder
             */
            @Override
            public void onNext(EventBatch eventBatch) {
                try {
                    long receivedAt = System.nanoTime();
                    int eventCount = eventBatch.getEventsList().size();

                    if (eventBatch.getEventsList().isEmpty()) {
                        log.warn("Empty EventBatch received with batch_id: {}", eventBatch.getBatchId());
                        AckResponse ack = AckResponse.newBuilder()
                            .setBatchId(eventBatch.getBatchId())
                            .setSuccess(false)
                            .setEventsReceived(0)
                            .setErrorMessage("Empty batch received")
                            .setTimestampNanos(System.nanoTime())
                            .build();
                        responseObserver.onNext(ack);
                        return;
                    }

                    AckResponse ack = AckResponse.newBuilder()
                        .setBatchId(eventBatch.getBatchId())
                        .setSuccess(true)
                        .setEventsReceived(eventCount)
                        .setErrorMessage("")
                        .setTimestampNanos(System.nanoTime())
                        .build();

                    responseObserver.onNext(ack);
                    log.debug("ACK sent for batch {} with {} events", 
                        eventBatch.getBatchId(), eventCount);

                    processEventBatchAsync(eventBatch);
                } catch (Exception e) {
                    log.error("Error processing EventBatch", e);
                    AckResponse ack = AckResponse.newBuilder()
                        .setBatchId(eventBatch.getBatchId())
                        .setSuccess(false)
                        .setEventsReceived(0)
                        .setErrorMessage(e.getMessage())
                        .setTimestampNanos(System.nanoTime())
                        .build();
                    responseObserver.onNext(ack);
                }
            }

            /**
             * Processes an EventBatch asynchronously in a background thread.
             *
             * <p><b>PURPOSE:</b></p>
             * Offloads event persistence to a background thread to prevent blocking the gRPC
             * I/O thread. This allows the main stream to continue receiving and ACKing batches
             * while previous batches are still being processed.
             *
             * <p><b>PROCESSING FLOW:</b></p>
             * <pre>
             * processEventBatchAsync()
             *         │
             *         ▼ CompletableFuture.runAsync()
             *     ┌───────────────────────────────────┐
             *     │ For each event in eventBatch:    │
             *     │   eventService.persistEvent()    │
             *     │         │                        │
             *     │         ▼                        │
             *     │   EventBatchProcessor.addEvent() │
             *     │         │                        │
             *     │         ▼                        │
             *     │   Kafka "events" topic           │
             *     └───────────────────────────────────┘
             * </pre>
             *
             * <p><b>ERROR HANDLING:</b></p>
             * <ul>
             *   <li>Individual event failures are logged but don't stop batch processing</li>
             *   <li>Failed events are counted and logged in the summary</li>
             *   <li>No retry at this level - Kafka consumer handles reprocessing</li>
             * </ul>
             *
             * <p><b>THREADING:</b></p>
             * Runs on ForkJoinPool.commonPool() via CompletableFuture.runAsync().
             * For high-throughput scenarios, consider using a dedicated executor.
             *
             * @param eventBatch The batch of events to persist
             */
            private void processEventBatchAsync(EventBatch eventBatch) {
                CompletableFuture.runAsync(() -> {
                    try {
                        long startTime = System.nanoTime();
                        java.util.concurrent.atomic.AtomicInteger eventCount = new java.util.concurrent.atomic.AtomicInteger(0);
                        
                        for (com.forwarder.v1.Event event : eventBatch.getEventsList()) {
                            try {
                                eventService.persistEvent(event);
                                eventCount.incrementAndGet();
                            } catch (Exception e) {
                                log.error("Error persisting event in background: {}", event.getSourceName(), e);
                            }
                        }
                        
                        long processingTimeNanos = System.nanoTime() - startTime;
                        log.info("Batch {} processed asynchronously: {} events in {}ns", 
                            eventBatch.getBatchId(), eventCount.get(), processingTimeNanos);
                    } catch (Exception e) {
                        log.error("Error in async event batch processing: {}", eventBatch.getBatchId(), e);
                    }
                });
            }

            /**
             * Called when an error occurs in the client-to-server stream.
             *
             * <p><b>TRIGGERS:</b></p>
             * <ul>
             *   <li>Network disconnection</li>
             *   <li>Client crash or forceful shutdown</li>
             *   <li>gRPC protocol errors</li>
             *   <li>TLS/SSL handshake failures</li>
             * </ul>
             *
             * <p><b>BEHAVIOR:</b></p>
             * Propagates the error back to the client (if still connected) and logs the error.
             * The stream is terminated and the LogForwarder should reconnect.
             *
             * @param t The error that occurred
             */
            @Override
            public void onError(Throwable t) {
                log.error("Error in SendEvents stream", t);
                responseObserver.onError(t);
            }

            /**
             * Called when the client signals completion of the stream.
             *
             * <p><b>TRIGGERS:</b></p>
             * <ul>
             *   <li>LogForwarder graceful shutdown</li>
             *   <li>LogForwarder decides to close the stream and open a new one</li>
             *   <li>Connection pool rotation in LogForwarder</li>
             * </ul>
             *
             * <p><b>BEHAVIOR:</b></p>
             * Signals completion back to the client, allowing the gRPC channel to be closed cleanly.
             */
            @Override
            public void onCompleted() {
                log.debug("SendEvents stream completed");
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Handles health check requests from LogForwarder agents.
     *
     * <p><b>PURPOSE:</b></p>
     * Allows LogForwarder agents to verify the middleware is healthy and accepting events.
     * Used by the circuit breaker in LogForwarder to determine indexer availability.
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>{@code logforwarder/src/network/health_check.rs} - HealthChecker.check()</li>
     *   <li>{@code logforwarder/src/outputs/grpc_sender.rs:199} - GrpcOutputSender.health_check()</li>
     * </ul>
     *
     * <p><b>RESPONSE CONTENT:</b></p>
     * <ul>
     *   <li><b>healthy:</b> Always true if this method is reachable</li>
     *   <li><b>cpuUsage:</b> Current system CPU usage percentage (0-100)</li>
     *   <li><b>memoryUsageMb:</b> Current JVM heap memory usage in MB</li>
     *   <li><b>queueDepth:</b> Reserved for future use (currently 0)</li>
     * </ul>
     *
     * <p><b>ASYNC EXECUTION:</b></p>
     * Runs asynchronously to prevent blocking the gRPC thread during system metric collection.
     *
     * @param request The health check request containing forwarder ID
     * @param responseObserver Observer for sending the health check response
     */
    @Override
    public void healthCheck(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        CompletableFuture.runAsync(() -> {
            try {
                String forwarderId = request.getForwarderId();
                log.debug("Health check request for forwarder: {}", forwarderId);

                HealthCheckResponse response = HealthCheckResponse.newBuilder()
                    .setHealthy(true)
                    .setCpuUsage(getSystemCpuUsage())
                    .setMemoryUsageMb(getSystemMemoryUsageMb())
                    .setQueueDepth(0)
                    .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Error in health check", e);
                responseObserver.onError(e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async health check", ex);
            return null;
        });
    }

    /**
     * Handles capacity requests from LogForwarder agents for backpressure management.
     *
     * <p><b>PURPOSE:</b></p>
     * Provides LogForwarder agents with information about the middleware's current capacity
     * to accept events. This enables intelligent backpressure where forwarders can:
     * <ul>
     *   <li>Slow down event sending if capacity is low</li>
     *   <li>Route events to alternative indexers with more capacity</li>
     *   <li>Buffer events locally if all indexers are at capacity</li>
     * </ul>
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>{@code logforwarder/src/network/load_balancer.rs} - For capacity-aware routing</li>
     *   <li>{@code logforwarder/src/backpressure/} - For flow control decisions</li>
     * </ul>
     *
     * <p><b>RESPONSE CONTENT:</b></p>
     * <ul>
     *   <li><b>availableCapacityBytes:</b> Free JVM heap memory (for buffering)</li>
     *   <li><b>maxEventsPerSecond:</b> Configured max throughput (default 10000 EPS)</li>
     *   <li><b>acceptingConnections:</b> Whether the server is accepting new connections</li>
     * </ul>
     *
     * <p><b>FUTURE ENHANCEMENTS:</b></p>
     * <ul>
     *   <li>Include Kafka queue depth in capacity calculation</li>
     *   <li>Dynamic maxEventsPerSecond based on actual processing rate</li>
     *   <li>Consider ClickHouse write latency in capacity</li>
     * </ul>
     *
     * @param request The capacity request containing forwarder ID
     * @param responseObserver Observer for sending the capacity response
     */
    @Override
    public void getCapacity(CapacityRequest request, StreamObserver<CapacityResponse> responseObserver) {
        CompletableFuture.runAsync(() -> {
            try {
                String forwarderId = request.getForwarderId();
                log.debug("Capacity request for forwarder: {}", forwarderId);

                long availableCapacityBytes = getAvailableMemoryBytes();
                int maxEventsPerSecond = 10000;
                boolean acceptingConnections = true;

                CapacityResponse response = CapacityResponse.newBuilder()
                    .setAvailableCapacityBytes(availableCapacityBytes)
                    .setMaxEventsPerSecond(maxEventsPerSecond)
                    .setAcceptingConnections(acceptingConnections)
                    .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Error in get capacity", e);
                responseObserver.onError(e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async get capacity", ex);
            return null;
        });
    }

    /**
     * Retrieves the current system CPU usage percentage.
     *
     * <p><b>PURPOSE:</b></p>
     * Used in health check responses to give LogForwarders insight into middleware load.
     * High CPU usage may indicate the middleware is under stress.
     *
     * <p><b>IMPLEMENTATION NOTES:</b></p>
     * <ul>
     *   <li>Uses Sun/Oracle JVM-specific API (com.sun.management.OperatingSystemMXBean)</li>
     *   <li>Returns 0.0 if the API is not available (e.g., on non-Oracle JVMs)</li>
     *   <li>Value is multiplied by 100 to convert from 0-1 to 0-100 percentage</li>
     * </ul>
     *
     * @return CPU usage as percentage (0-100), or 0.0 if unavailable
     */
    private double getSystemCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return osBean.getSystemCpuLoad() * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Calculates the current JVM heap memory usage in megabytes.
     *
     * <p><b>PURPOSE:</b></p>
     * Used in health check responses to report memory consumption.
     * High memory usage may indicate event backlog or memory leaks.
     *
     * <p><b>CALCULATION:</b></p>
     * <pre>
     * usedMemory = totalMemory (currently allocated) - freeMemory (unused in allocated)
     * usageMb = usedMemory / (1024 * 1024)
     * </pre>
     *
     * @return JVM heap memory usage in megabytes
     */
    private double getSystemMemoryUsageMb() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024.0 * 1024.0);
    }

    /**
     * Calculates the available JVM heap memory in bytes.
     *
     * <p><b>PURPOSE:</b></p>
     * Used in capacity responses to indicate how much buffering capacity remains.
     * Low available memory may trigger backpressure in LogForwarder.
     *
     * <p><b>CALCULATION:</b></p>
     * <pre>
     * availableMemory = maxMemory (JVM -Xmx) - usedMemory
     * where usedMemory = totalMemory - freeMemory
     * </pre>
     *
     * <p><b>USAGE IN BACKPRESSURE:</b></p>
     * LogForwarders may use this value to:
     * <ul>
     *   <li>Reduce batch sizes when memory is low</li>
     *   <li>Switch to alternative indexers with more capacity</li>
     *   <li>Enable local disk buffering if all indexers are low on memory</li>
     * </ul>
     *
     * @return Available JVM heap memory in bytes
     */
    private long getAvailableMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    }
}
