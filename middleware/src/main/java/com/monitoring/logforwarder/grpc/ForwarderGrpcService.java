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

@Slf4j
@Component
public class ForwarderGrpcService extends ForwarderServiceGrpc.ForwarderServiceImplBase {

    @Setter
    private EventService eventService;

    @Override
    public StreamObserver<EventBatch> sendEvents(StreamObserver<AckResponse> responseObserver) {
        return new StreamObserver<EventBatch>() {
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

            @Override
            public void onError(Throwable t) {
                log.error("Error in SendEvents stream", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.debug("SendEvents stream completed");
                responseObserver.onCompleted();
            }
        };
    }

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

    private double getSystemCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return osBean.getSystemCpuLoad() * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getSystemMemoryUsageMb() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024.0 * 1024.0);
    }

    private long getAvailableMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    }
}
