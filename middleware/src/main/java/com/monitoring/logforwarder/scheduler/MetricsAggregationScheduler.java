package com.monitoring.logforwarder.scheduler;

import com.monitoring.logforwarder.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduled service for metrics aggregation and synchronization.
 *
 * <p><b>Purpose:</b> Executes periodic aggregation of metrics at various intervals
 * (hourly, daily, monthly) and synchronizes with Elasticsearch.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>5-minute interval for recent metrics</li>
 *   <li>Hourly aggregation for trending</li>
 *   <li>Daily aggregation for historical analysis</li>
 *   <li>15-minute Elasticsearch sync</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
public class MetricsAggregationScheduler {

    private final MetricsService metricsService;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MetricsAggregationScheduler(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Scheduled(fixedRateString = "${app.scheduler.metrics-aggregation-interval:300000}", initialDelayString = "${app.scheduler.metrics-aggregation-initial-delay:60000}")
    public void aggregateMetrics() {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting metrics aggregation at {}", LocalDateTime.now().format(formatter));
                metricsService.aggregateMetricsForLastHour();
                log.info("Metrics aggregation completed successfully");
            } catch (Exception e) {
                log.error("Error during metrics aggregation", e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async metrics aggregation", ex);
            return null;
        });
    }

    @Scheduled(fixedRateString = "${app.scheduler.metrics-hourly-aggregation:3600000}", initialDelayString = "${app.scheduler.metrics-hourly-initial-delay:120000}")
    public void aggregateHourlyMetrics() {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting hourly metrics aggregation at {}", LocalDateTime.now().format(formatter));
                metricsService.aggregateMetricsForLastDay();
                log.info("Hourly metrics aggregation completed successfully");
            } catch (Exception e) {
                log.error("Error during hourly metrics aggregation", e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async hourly metrics aggregation", ex);
            return null;
        });
    }

    @Scheduled(fixedRateString = "${app.scheduler.metrics-daily-aggregation:86400000}", initialDelayString = "${app.scheduler.metrics-daily-initial-delay:180000}")
    public void aggregateDailyMetrics() {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting daily metrics aggregation at {}", LocalDateTime.now().format(formatter));
                metricsService.aggregateMetricsForLastMonth();
                log.info("Daily metrics aggregation completed successfully");
            } catch (Exception e) {
                log.error("Error during daily metrics aggregation", e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async daily metrics aggregation", ex);
            return null;
        });
    }

    @Scheduled(cron = "${app.scheduler.metrics-sync-cron:0 */15 * * * ?}")
    public void syncMetricsWithElasticsearch() {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting metrics sync with Elasticsearch at {}", LocalDateTime.now().format(formatter));
                metricsService.syncMetricsToElasticsearch();
                log.info("Metrics sync with Elasticsearch completed successfully");
            } catch (Exception e) {
                log.error("Error during metrics sync with Elasticsearch", e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async metrics sync with Elasticsearch", ex);
            return null;
        });
    }

    @Scheduled(cron = "${app.scheduler.metrics-cache-refresh-cron:0 0 * * * ?}")
    public void refreshMetricsCache() {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting metrics cache refresh at {}", LocalDateTime.now().format(formatter));
                metricsService.refreshMetricsCache();
                log.info("Metrics cache refresh completed successfully");
            } catch (Exception e) {
                log.error("Error during metrics cache refresh", e);
            }
        })
        .exceptionally(ex -> {
            log.error("Error in async metrics cache refresh", ex);
            return null;
        });
    }
}
