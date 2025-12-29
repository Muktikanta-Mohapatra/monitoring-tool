package com.monitoring.logforwarder.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduler service responsible for periodically flushing accumulated event batches.
 *
 * <p><b>Purpose:</b> Ensures that events collected in memory buffers are periodically persisted
 * to their respective destinations (Kafka for event processing and Elasticsearch for indexing),
 * even when batch size thresholds are not reached. This prevents data loss and ensures timely
 * event delivery in low-traffic scenarios.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses Spring's {@code @Scheduled} annotation for time-based task execution</li>
 *   <li>Flushes event batches to Kafka at configurable intervals (default: 10 seconds)</li>
 *   <li>Flushes Elasticsearch index batches at configurable intervals (default: 15 seconds)</li>
 *   <li>Thread-safe operations coordinated with batch processor synchronization</li>
 *   <li>Graceful error handling to prevent scheduler interruption on failures</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * app:
 *   scheduler:
 *     batch-flush-interval: 10000    # 10 seconds for event batches
 *     index-batch-flush-interval: 15000  # 15 seconds for ES batches
 *
 * // The scheduler automatically runs based on configured intervals
 * // No manual invocation required - Spring handles scheduling
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventBatchProcessor
 * @see ElasticsearchBulkIndexer
 */
@Slf4j
@Service
public class BatchFlushScheduler {

    private final EventBatchProcessor eventBatchProcessor;
    private final ElasticsearchBulkIndexer elasticsearchBulkIndexer;

    /**
     * Constructs a BatchFlushScheduler with required dependencies.
     *
     * <p><b>Purpose:</b> Initializes the scheduler with references to batch processors
     * that manage event and Elasticsearch indexing operations.</p>
     *
     * <p><b>Technical Details:</b> Uses constructor injection for dependency management,
     * ensuring immutability and facilitating unit testing.</p>
     *
     * @param eventBatchProcessor the processor handling event batch operations for Kafka
     * @param elasticsearchBulkIndexer the indexer handling bulk operations for Elasticsearch
     */
    public BatchFlushScheduler(EventBatchProcessor eventBatchProcessor,
                              ElasticsearchBulkIndexer elasticsearchBulkIndexer) {
        this.eventBatchProcessor = eventBatchProcessor;
        this.elasticsearchBulkIndexer = elasticsearchBulkIndexer;
    }

    /**
     * Periodically flushes accumulated event batches to Kafka.
     *
     * <p><b>Purpose:</b> Ensures events waiting in the batch buffer are sent to Kafka
     * at regular intervals, preventing data staleness and ensuring timely processing
     * even during low-traffic periods.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Executes at fixed rate defined by {@code app.scheduler.batch-flush-interval} (default: 10s)</li>
     *   <li>Only flushes if batch contains pending events (batchSize &gt; 0)</li>
     *   <li>Catches and logs exceptions to prevent scheduler thread termination</li>
     *   <li>Thread-safe through synchronized batch processor methods</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // This method is automatically invoked by Spring Scheduler
     * // Manual invocation for testing:
     * batchFlushScheduler.flushEventBatches();
     * }</pre>
     */
    @Scheduled(fixedRateString = "${app.scheduler.batch-flush-interval:10000}")
    public void flushEventBatches() {
        try {
            int batchSize = eventBatchProcessor.getBatchSize();
            if (batchSize > 0) {
                log.debug("Flushing event batch with {} events", batchSize);
                eventBatchProcessor.flushBatch()
                    .exceptionally(ex -> {
                        log.error("Error during scheduled event batch flush", ex);
                        return null;
                    })
                    .thenRun(() -> log.info("Event batch flushed successfully"));
            }
        } catch (Exception e) {
            log.error("Error during event batch flush scheduling", e);
        }
    }

    /**
     * Periodically flushes accumulated events to Elasticsearch for indexing.
     *
     * <p><b>Purpose:</b> Ensures events pending in the Elasticsearch index buffer are bulk-indexed
     * at regular intervals, optimizing search availability while maintaining efficient batch operations.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Executes at fixed rate defined by {@code app.scheduler.index-batch-flush-interval} (default: 15s)</li>
     *   <li>Only flushes if index batch contains pending events</li>
     *   <li>Uses Elasticsearch bulk API for efficient indexing</li>
     *   <li>Catches and logs exceptions to ensure scheduler continuity</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // Automatically invoked by Spring Scheduler
     * // For testing purposes:
     * batchFlushScheduler.flushIndexBatches();
     * }</pre>
     */
    @Scheduled(fixedRateString = "${app.scheduler.index-batch-flush-interval:15000}")
    public void flushIndexBatches() {
        try {
            int batchSize = elasticsearchBulkIndexer.getCurrentBatchSize();
            if (batchSize > 0) {
                log.debug("Flushing index batch with {} events", batchSize);
                elasticsearchBulkIndexer.flushIndexBatch()
                    .exceptionally(ex -> {
                        log.error("Error during scheduled index batch flush", ex);
                        return null;
                    })
                    .thenRun(() -> log.info("Index batch flushed successfully"));
            }
        } catch (Exception e) {
            log.error("Error during index batch flush scheduling", e);
        }
    }
}
