package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;

/**
 * Service for tracking event processing pipeline metrics.
 *
 * <p><b>Purpose:</b> Provides atomic counters for tracking events through the entire
 * processing pipeline from ingestion to persistence, enabling monitoring and debugging.</p>
 *
 * <p><b>Tracked Metrics:</b></p>
 * <ul>
 *   <li><b>eventsIngested:</b> Events received from HTTP/gRPC endpoints</li>
 *   <li><b>eventsQueued:</b> Events added to EventBatchProcessor queue</li>
 *   <li><b>eventsPublishedToKafka:</b> Events successfully published to Kafka</li>
 *   <li><b>eventsConsumedFromKafka:</b> Events consumed from Kafka</li>
 *   <li><b>eventsPersisted:</b> Events successfully written to ClickHouse</li>
 *   <li><b>eventsDuplicate:</b> Duplicate events detected and skipped</li>
 *   <li><b>eventsFailed:</b> Events that failed processing</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
@Getter
public class EventProcessingMetrics {

    private final AtomicLong eventsIngested = new AtomicLong(0);
    private final AtomicLong eventsQueued = new AtomicLong(0);
    private final AtomicLong eventsPublishedToKafka = new AtomicLong(0);
    private final AtomicLong eventsConsumedFromKafka = new AtomicLong(0);
    private final AtomicLong eventsPersisted = new AtomicLong(0);
    private final AtomicLong eventsDuplicate = new AtomicLong(0);
    private final AtomicLong eventsFailed = new AtomicLong(0);
    
    private final AtomicReference<Long> lastResetTime = new AtomicReference<>(System.currentTimeMillis());

    public CompletableFuture<Void> recordEventIngested() {
        return AsyncHelper.executeAsyncFuture(() -> {
            long count = eventsIngested.incrementAndGet();
            if (count % 1000 == 0) {
                log.info("Metrics: {} events ingested total", count);
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordEventQueued() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsQueued.incrementAndGet();
            return null;
        });
    }

    public CompletableFuture<Void> recordEventPublishedToKafka() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsPublishedToKafka.incrementAndGet();
            return null;
        });
    }

    public CompletableFuture<Void> recordEventConsumedFromKafka() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsConsumedFromKafka.incrementAndGet();
            return null;
        });
    }

    public CompletableFuture<Void> recordEventPersisted() {
        return AsyncHelper.executeAsyncFuture(() -> {
            long count = eventsPersisted.incrementAndGet();
            if (count % 1000 == 0) {
                log.info("Metrics: {} events persisted total", count);
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordDuplicateEvent() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsDuplicate.incrementAndGet();
            return null;
        });
    }

    public CompletableFuture<Void> recordEventFailed() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsFailed.incrementAndGet();
            return null;
        });
    }

    public CompletableFuture<MetricsSnapshot> getSnapshot() {
        return AsyncHelper.executeAsyncFuture(() ->
            MetricsSnapshot.builder()
                .eventsIngested(eventsIngested.get())
                .eventsQueued(eventsQueued.get())
                .eventsPublishedToKafka(eventsPublishedToKafka.get())
                .eventsConsumedFromKafka(eventsConsumedFromKafka.get())
                .eventsPersisted(eventsPersisted.get())
                .eventsDuplicate(eventsDuplicate.get())
                .eventsFailed(eventsFailed.get())
                .ingestedVsPersisteddiff(eventsIngested.get() - eventsPersisted.get())
                .publishedVsConsumedDiff(eventsPublishedToKafka.get() - eventsConsumedFromKafka.get())
                .build()
        );
    }

    public CompletableFuture<Void> resetMetrics() {
        return AsyncHelper.executeAsyncFuture(() -> {
            eventsIngested.set(0);
            eventsQueued.set(0);
            eventsPublishedToKafka.set(0);
            eventsConsumedFromKafka.set(0);
            eventsPersisted.set(0);
            eventsDuplicate.set(0);
            eventsFailed.set(0);
            lastResetTime.set(System.currentTimeMillis());
            log.info("Event processing metrics reset");
            return null;
        });
    }

    public CompletableFuture<Void> logMetrics() {
        return AsyncHelper.executeAsyncFuture(() -> {
            try {
                MetricsSnapshot snapshot = getSnapshot().get();
                log.warn("=== EVENT PROCESSING METRICS ===");
                log.warn("Events Ingested: {}", snapshot.getEventsIngested());
                log.warn("Events Queued: {}", snapshot.getEventsQueued());
                log.warn("Events Published to Kafka: {}", snapshot.getEventsPublishedToKafka());
                log.warn("Events Consumed from Kafka: {}", snapshot.getEventsConsumedFromKafka());
                log.warn("Events Persisted to DB: {}", snapshot.getEventsPersisted());
                log.warn("Duplicate Events: {}", snapshot.getEventsDuplicate());
                log.warn("Failed Events: {}", snapshot.getEventsFailed());
                log.warn("MISMATCH - Ingested vs Persisted: {} (should be <= 0)", snapshot.getIngestedVsPersisteddiff());
                log.warn("MISMATCH - Published vs Consumed: {} (should be 0)", snapshot.getPublishedVsConsumedDiff());
                log.warn("================================");
            } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
                log.error("Failed to get metrics snapshot: {}", ex.getMessage());
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        });
    }

    @Getter
    @lombok.Builder
    public static class MetricsSnapshot {
        private final long eventsIngested;
        private final long eventsQueued;
        private final long eventsPublishedToKafka;
        private final long eventsConsumedFromKafka;
        private final long eventsPersisted;
        private final long eventsDuplicate;
        private final long eventsFailed;
        private final long ingestedVsPersisteddiff;
        private final long publishedVsConsumedDiff;

        public boolean isConsistent() {
            return ingestedVsPersisteddiff <= 0 && publishedVsConsumedDiff == 0;
        }
    }
}
