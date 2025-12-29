package com.monitoring.logforwarder.batch;

import com.monitoring.logforwarder.dto.EventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for bulk indexing events to Elasticsearch.
 *
 * <p><b>Purpose:</b> Manages efficient bulk indexing of log events to Elasticsearch by
 * accumulating events in memory and performing batch writes. This approach significantly
 * improves indexing throughput and reduces I/O overhead compared to individual document writes.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Maintains an in-memory buffer of events pending indexing</li>
 *   <li>Automatically flushes when batch size threshold is reached (default: 500 events)</li>
 *   <li>Time-based flush trigger after 10 seconds of inactivity</li>
 *   <li>Thread-safe operations using synchronized methods</li>
 *   <li>Uses Spring Data Elasticsearch for document operations</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Single event indexing
 * EventDTO event = EventDTO.builder()
 *     .sourceName("web-server-01")
 *     .rawMessage("User login successful")
 *     .severity("INFO")
 *     .build();
 * elasticsearchBulkIndexer.addEventForIndexing(event);
 *
 * // Batch event indexing
 * List<EventDTO> events = Arrays.asList(event1, event2, event3);
 * elasticsearchBulkIndexer.addEventsForIndexing(events);
 *
 * // Manual flush
 * elasticsearchBulkIndexer.flushIndexBatch();
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ElasticsearchOperations
 * @see EventDTO
 */
@Slf4j
@Service
public class ElasticsearchBulkIndexer {

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${app.batch.elasticsearch-batch-size:500}")
    private int bulkBatchSize;

    private final List<EventDTO> indexBatch = new ArrayList<>();
    private long lastIndexTime = System.currentTimeMillis();

    /**
     * Constructs an ElasticsearchBulkIndexer with the required Elasticsearch operations.
     *
     * <p><b>Purpose:</b> Initializes the indexer with Spring Data Elasticsearch operations
     * for performing document persistence.</p>
     *
     * <p><b>Technical Details:</b> Uses constructor injection for the ElasticsearchOperations
     * dependency, which is auto-configured by Spring Boot based on application properties.</p>
     *
     * @param elasticsearchOperations Spring Data Elasticsearch operations for document management
     */
    public ElasticsearchBulkIndexer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * Adds a single event to the indexing batch.
     *
     * <p><b>Purpose:</b> Queues an event for bulk indexing to Elasticsearch. The event is
     * added to the batch buffer and will be indexed when the batch size threshold is reached
     * or when a flush is triggered.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Synchronized method ensuring thread-safe batch modification</li>
     *   <li>Automatically triggers flush if batch size or time threshold is met</li>
     *   <li>Non-blocking for the caller when threshold is not reached</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * EventDTO event = EventDTO.builder()
     *     .timestamp(LocalDateTime.now())
     *     .sourceName("app-server")
     *     .rawMessage("Application started")
     *     .severity("INFO")
     *     .build();
     * elasticsearchBulkIndexer.addEventForIndexing(event);
     * }</pre>
     *
     * @param event the event DTO to be indexed in Elasticsearch
     */
    public synchronized void addEventForIndexing(EventDTO event) {
        indexBatch.add(event);
        
        if (shouldFlushIndexBatch()) {
            flushIndexBatch()
                .exceptionally(ex -> {
                    log.error("Error flushing index batch", ex);
                    return null;
                });
        }
    }

    /**
     * Adds multiple events to the indexing batch.
     *
     * <p><b>Purpose:</b> Queues a collection of events for bulk indexing. Efficiently handles
     * large event batches by automatically splitting them into appropriately-sized chunks
     * when the batch size threshold is exceeded.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Synchronized for thread-safe batch modification</li>
     *   <li>Processes complete batches immediately when threshold is reached</li>
     *   <li>Remaining events are kept in buffer for next flush cycle</li>
     *   <li>More efficient than multiple single-event additions</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<EventDTO> events = new ArrayList<>();
     * for (int i = 0; i < 1000; i++) {
     *     events.add(EventDTO.builder()
     *         .rawMessage("Log message " + i)
     *         .severity("INFO")
     *         .build());
     * }
     * elasticsearchBulkIndexer.addEventsForIndexing(events);
     * }</pre>
     *
     * @param events list of event DTOs to be indexed in Elasticsearch
     */
    public synchronized void addEventsForIndexing(List<EventDTO> events) {
        indexBatch.addAll(events);
        
        while (indexBatch.size() >= bulkBatchSize) {
            List<EventDTO> batch = new ArrayList<>(indexBatch.subList(0, bulkBatchSize));
            indexBatch.subList(0, bulkBatchSize).clear();
            performBulkIndex(batch)
                .exceptionally(ex -> {
                    log.error("Error indexing batch of {} events", batch.size(), ex);
                    return null;
                });
        }
        
        if (shouldFlushIndexBatch()) {
            flushIndexBatch()
                .exceptionally(ex -> {
                    log.error("Error flushing index batch", ex);
                    return null;
                });
        }
    }

    /**
     * Forces immediate indexing of all pending events in the batch buffer.
     *
     * <p><b>Purpose:</b> Immediately processes all events currently in the batch buffer,
     * regardless of batch size. Used for graceful shutdown, time-based flushing, or
     * when immediate indexing is required.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Synchronized to prevent concurrent batch modifications</li>
     *   <li>Clears the batch buffer after processing</li>
     *   <li>Resets the last index time for timeout tracking</li>
     *   <li>No-op if batch is empty</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // Force flush before application shutdown
     * elasticsearchBulkIndexer.flushIndexBatch();
     *
     * // Or in a scheduled task
     * if (elasticsearchBulkIndexer.getCurrentBatchSize() > 0) {
     *     elasticsearchBulkIndexer.flushIndexBatch();
     * }
     * }</pre>
     */
    public synchronized CompletableFuture<Void> flushIndexBatch() {
        if (!indexBatch.isEmpty()) {
            log.debug("Flushing index batch with {} events", indexBatch.size());
            List<EventDTO> batch = new ArrayList<>(indexBatch);
            indexBatch.clear();
            lastIndexTime = System.currentTimeMillis();
            return performBulkIndex(batch);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Performs the actual bulk indexing operation to Elasticsearch.
     *
     * <p><b>Purpose:</b> Executes the Elasticsearch indexing for a batch of events.
     * Each event is individually saved with error handling to ensure partial failures
     * don't prevent other events from being indexed.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Iterates through events and saves each individually</li>
     *   <li>Logs individual failures without stopping batch processing</li>
     *   <li>Records performance metrics (batch size, duration)</li>
     *   <li>Uses ElasticsearchOperations.save() for document persistence</li>
     * </ul>
     *
     * @param batch the list of events to be indexed
     */
    private CompletableFuture<Void> performBulkIndex(List<EventDTO> batch) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Performing bulk index of {} events to Elasticsearch", batch.size());
                long startTime = System.currentTimeMillis();
                
                batch.forEach(event -> {
                    try {
                        elasticsearchOperations.save(event);
                    } catch (Exception e) {
                        log.error("Failed to index event: {}", event.getId(), e);
                    }
                });
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Bulk indexing completed for {} events in {} ms", batch.size(), duration);
            } catch (Exception e) {
                log.error("Failed to perform bulk index", e);
            }
        });
    }

    /**
     * Determines if the batch should be flushed based on size and time thresholds.
     *
     * <p><b>Purpose:</b> Evaluates whether current conditions warrant an immediate flush
     * of the batch buffer, based on batch size reaching threshold or time elapsed since
     * last flush exceeding timeout.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Returns true if batch size &gt;= configured bulkBatchSize</li>
     *   <li>Returns true if time since last index &gt;= 10 seconds</li>
     *   <li>Used internally to trigger automatic flushes</li>
     * </ul>
     *
     * @return {@code true} if batch should be flushed, {@code false} otherwise
     */
    private boolean shouldFlushIndexBatch() {
        return indexBatch.size() >= bulkBatchSize || 
               (System.currentTimeMillis() - lastIndexTime) >= 10000;
    }

    /**
     * Gets the configured bulk batch size threshold.
     *
     * <p><b>Purpose:</b> Returns the maximum number of events that can accumulate
     * before an automatic flush is triggered.</p>
     *
     * @return the configured batch size threshold (default: 500)
     */
    public int getBulkBatchSize() {
        return bulkBatchSize;
    }

    /**
     * Sets the bulk batch size threshold.
     *
     * <p><b>Purpose:</b> Allows runtime configuration of the batch size threshold.
     * Useful for tuning performance based on system load and memory constraints.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // Increase batch size for high-throughput scenarios
     * elasticsearchBulkIndexer.setBulkBatchSize(1000);
     * }</pre>
     *
     * @param size the new batch size threshold
     */
    public void setBulkBatchSize(int size) {
        this.bulkBatchSize = size;
    }

    /**
     * Gets the current number of events pending in the batch buffer.
     *
     * <p><b>Purpose:</b> Returns the count of events currently queued for indexing.
     * Useful for monitoring and deciding when to trigger manual flushes.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * int pending = elasticsearchBulkIndexer.getCurrentBatchSize();
     * if (pending > 100) {
     *     log.info("High number of pending events: {}", pending);
     * }
     * }</pre>
     *
     * @return the number of events currently in the batch buffer
     */
    public int getCurrentBatchSize() {
        return indexBatch.size();
    }
}
