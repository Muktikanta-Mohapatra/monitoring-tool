package com.monitoring.logforwarder.batch;

import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.kafka.EventProducer;
import com.monitoring.logforwarder.service.EventProcessingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Batches incoming events and publishes them to Kafka for durable processing.
 *
 * <p><b>PURPOSE:</b></p>
 * This component sits between the ingestion endpoints (HTTP/gRPC) and Kafka, providing:
 * <ul>
 *   <li><b>Batching:</b> Groups events to reduce Kafka producer overhead</li>
 *   <li><b>Buffering:</b> Queues events when ingestion rate exceeds processing rate</li>
 *   <li><b>Backpressure:</b> Blocks producers if queue is full (prevents OOM)</li>
 *   <li><b>Timed Flushing:</b> Ensures events are published even with low volume</li>
 * </ul>
 *
 * <p><b>ARCHITECTURE POSITION:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                     EventBatchProcessor - KAFKA BRIDGE                          │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  EventService.saveBatch()      EventService.persistEvent()                      │
 * │  (HTTP ingestion)              (gRPC ingestion)                                 │
 * │          │                              │                                       │
 * │          └──────────┬───────────────────┘                                       │
 * │                     ▼                                                           │
 * │          ┌──────────────────────┐                                               │
 * │          │ EventBatchProcessor  │ ←── THIS CLASS                                │
 * │          │ (this class)         │                                               │
 * │          └──────────┬───────────┘                                               │
 * │                     │                                                           │
 * │          ┌──────────▼───────────┐                                               │
 * │          │ LinkedBlockingQueue  │  ←── Thread-safe buffer (capacity: 10000)     │
 * │          │ (eventQueue)         │                                               │
 * │          └──────────┬───────────┘                                               │
 * │                     │                                                           │
 * │       ┌─────────────┴─────────────┐                                             │
 * │       ▼                           ▼                                             │
 * │  [Size >= 100]            [Timeout >= 5000ms]                                   │
 * │       │                           │                                             │
 * │       └───────────┬───────────────┘                                             │
 * │                   ▼                                                             │
 * │          ┌──────────────────────┐                                               │
 * │          │ processBatch()       │                                               │
 * │          │ EventProducer.       │                                               │
 * │          │   publishEvent()     │                                               │
 * │          └──────────┬───────────┘                                               │
 * │                     │                                                           │
 * │                     ▼                                                           │
 * │            Kafka "events" topic                                                 │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>FLUSH TRIGGERS:</b></p>
 * Events are flushed to Kafka when ANY of these conditions is met:
 * <ol>
 *   <li><b>Batch Size:</b> Queue size reaches {@code app.batch.event-batch-size} (default: 100)</li>
 *   <li><b>Timeout:</b> Time since last flush exceeds {@code app.batch.event-batch-timeout-ms} (default: 5000ms)</li>
 *   <li><b>Scheduled:</b> Background scheduler runs every 5 seconds to check and flush</li>
 * </ol>
 *
 * <p><b>CONFIGURATION PROPERTIES:</b></p>
 * <ul>
 *   <li>{@code app.batch.event-batch-size} - Events per batch before flush (default: 100)</li>
 *   <li>{@code app.batch.event-batch-timeout-ms} - Max time before forced flush (default: 5000ms)</li>
 *   <li>{@code app.batch.queue-capacity} - Max events in buffer before blocking (default: 10000)</li>
 * </ul>
 *
 * <p><b>THREAD SAFETY:</b></p>
 * <ul>
 *   <li>Uses {@link LinkedBlockingQueue} for thread-safe event buffering</li>
 *   <li>Multiple threads can call addEvent()/addEvents() concurrently</li>
 *   <li>Flush operations are synchronized via CompletableFuture.runAsync()</li>
 * </ul>
 *
 * <p><b>CALLED BY:</b></p>
 * <ul>
 *   <li>{@link com.monitoring.logforwarder.service.EventService#saveBatch} (line 90)</li>
 *   <li>{@link com.monitoring.logforwarder.service.EventService#persistEvent} (line 234)</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventProducer
 * @see com.monitoring.logforwarder.kafka.EventConsumer
 */
@Slf4j
@Service
public class EventBatchProcessor {

    private final EventProducer eventProducer;
    private final EventProcessingMetrics metrics;
    private final LinkedBlockingQueue<EventDTO> eventQueue;
    
    @Value("${app.batch.event-batch-size:100}")
    private int batchSize;
    
    @Value("${app.batch.event-batch-timeout-ms:5000}")
    private long batchTimeoutMs;
    
    private volatile long lastBatchFlushTime = System.currentTimeMillis();

    @Autowired
    public EventBatchProcessor(EventProducer eventProducer, 
                              EventProcessingMetrics metrics,
                              @Value("${app.batch.queue-capacity:10000}") int queueCapacity) {
        this.eventProducer = eventProducer;
        this.metrics = metrics;
        this.eventQueue = new LinkedBlockingQueue<>(queueCapacity);
        log.info("EventBatchProcessor initialized - batch-size: {}, timeout-ms: {}, queue-capacity: {}", 
            batchSize, batchTimeoutMs, queueCapacity);
    }

    public void addEvent(EventDTO event) {
        if (event == null) {
            log.warn("Null event received, ignoring");
            return;
        }
        
        try {
            if (!eventQueue.offer(event, 5, TimeUnit.SECONDS)) {
                log.error("Failed to queue event within timeout: {}", event.getId());
                metrics.recordEventFailed();
                return;
            }
            
            metrics.recordEventQueued();
            
            if (shouldFlushBatch()) {
                flushBatchAsync();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while adding event to queue: {}", event.getId(), ex);
            metrics.recordEventFailed();
        }
    }

    public void addEvents(List<EventDTO> events) {
        if (events == null || events.isEmpty()) {
            log.debug("Empty event list received");
            return;
        }
        
        log.info("CRITICAL: Received batch of {} events for processing. Queue size before: {}", 
            events.size(), eventQueue.size());
        
        try {
            int queuedCount = 0;
            int failedCount = 0;
            
            for (EventDTO event : events) {
                if (event == null) {
                    log.error("CRITICAL: Null event in batch!");
                    failedCount++;
                    metrics.recordEventFailed();
                    continue;
                }
                
                if (!eventQueue.offer(event, 5, TimeUnit.SECONDS)) {
                    log.error("CRITICAL: Failed to queue event within timeout - QUEUE FULL. Event ID: {}, Queue size: {}/{}", 
                        event.getId(), eventQueue.size(), eventQueue.remainingCapacity());
                    failedCount++;
                    metrics.recordEventFailed();
                } else {
                    metrics.recordEventQueued();
                    queuedCount++;
                    log.debug("Event queued successfully: {}", event.getId());
                }
            }
            
            log.info("CRITICAL: Batch queueing result - Queued: {}/{}, Failed: {}, Queue size after: {}", 
                queuedCount, events.size(), failedCount, eventQueue.size());
            
            if (eventQueue.size() >= batchSize) {
                log.info("CRITICAL: Batch size threshold reached ({}/{}), triggering flush", 
                    eventQueue.size(), batchSize);
                flushBatchAsync();
            }
            
            if (shouldFlushBatch()) {
                log.info("CRITICAL: Batch timeout threshold reached, triggering flush");
                flushBatchAsync();
            }
        } catch (Exception ex) {
            log.error("CRITICAL: Error adding events to batch: {}", ex.getMessage(), ex);
            metrics.recordEventFailed();
        }
    }

    public CompletableFuture<Void> flushBatchAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                List<EventDTO> batch = new ArrayList<>();
                eventQueue.drainTo(batch, batchSize);
                
                if (!batch.isEmpty()) {
                    log.debug("Flushing batch with {} events to Kafka", batch.size());
                    processBatch(batch)
                        .exceptionally(ex -> {
                            log.error("Error processing batch asynchronously: {}", ex.getMessage(), ex);
                            return null;
                        })
                        .join();
                    lastBatchFlushTime = System.currentTimeMillis();
                }
            } catch (Exception ex) {
                log.error("Error flushing batch asynchronously: {}", ex.getMessage(), ex);
            }
        });
    }

    public CompletableFuture<Void> flushBatch() {
        log.debug("Manual flush requested");
        return flushBatchAsync();
    }

    @Scheduled(fixedRateString = "${app.batch.event-batch-timeout-ms:5000}", initialDelay = 5000)
    public void scheduledBatchFlush() {
        if (eventQueue.size() > 0 && shouldFlushBatch()) {
            log.debug("Scheduled flush triggered with {} events in queue", eventQueue.size());
            flushBatchAsync();
        }
    }

    private boolean shouldFlushBatch() {
        long timeSinceLastFlush = System.currentTimeMillis() - lastBatchFlushTime;
        return eventQueue.size() >= batchSize || timeSinceLastFlush >= batchTimeoutMs;
    }

    private CompletableFuture<Void> processBatch(List<EventDTO> batch) {
        log.info("CRITICAL: Processing batch of {} events", batch.size());
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int publishAttempts = 0;
        int publishSuccesses = 0;
        
        for (EventDTO event : batch) {
            try {
                publishAttempts++;
                log.debug("CRITICAL: Publishing event {} to Kafka (attempt {}/{})", 
                    event.getId(), publishAttempts, batch.size());
                
                CompletableFuture<Void> publishFuture = eventProducer.publishEvent(event);
                futures.add(publishFuture);
                metrics.recordEventPublishedToKafka();
                publishSuccesses++;
            } catch (Exception ex) {
                log.error("CRITICAL: Failed to publish event {} to Kafka - Exception: {}", 
                    event.getId(), ex.getMessage(), ex);
                metrics.recordEventFailed();
            }
        }
        
        log.info("CRITICAL: Batch publishing started - Attempts: {}/{}, Futures created: {}", 
            publishAttempts, batch.size(), futures.size());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                long processingTime = System.currentTimeMillis() - startTime;
                log.info("CRITICAL: Batch processing COMPLETED - {} events published to Kafka in {}ms", 
                    batch.size(), processingTime);
            })
            .exceptionally(ex -> {
                log.error("CRITICAL: Error processing batch (at least one future failed): {} - Message: {}", 
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
                metrics.recordEventFailed();
                return null;
            });
    }

    public int getBatchSize() {
        return eventQueue.size();
    }

    public void setBatchSize(int size) {
        this.batchSize = size;
        log.info("Batch size threshold updated to: {}", size);
    }

    public long getBatchTimeoutMs() {
        return batchTimeoutMs;
    }

    public void setBatchTimeoutMs(long timeoutMs) {
        this.batchTimeoutMs = timeoutMs;
        log.info("Batch timeout updated to: {}ms", timeoutMs);
    }

    public int getQueueSize() {
        return eventQueue.size();
    }

    public int getRemainingCapacity() {
        return eventQueue.remainingCapacity();
    }
}
