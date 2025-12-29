package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.ForwarderMetricsDTO;
import com.monitoring.logforwarder.repository.clickhouse.ClickHouseEventRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class MetricsService {

    @Autowired
    private ClickHouseEventRepository eventRepository;

    private final AtomicLong lastEventCount = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastCheckTime = new AtomicReference<>(LocalDateTime.now());

    public CompletableFuture<Map<String, Object>> getSystemMetrics() {
        return AsyncHelper.executeAsyncFuture(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            double cpuLoad = 0;
            var osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                cpuLoad = sunOsBean.getProcessCpuLoad() * 100;
            }

            metrics.put("cpuUsage", cpuLoad);
            metrics.put("memoryUsage", (usedMemory * 100.0) / maxMemory);
            metrics.put("diskUsage", 0.0);
            metrics.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
            
            return metrics;
        });
    }

    public CompletableFuture<Map<String, Object>> getApplicationMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);
        
        return eventRepository.countEventsByTimeRange(oneMinuteAgo, now)
            .thenApply(currentCount -> {
                Map<String, Object> metrics = new HashMap<>();
                
                double eventsPerSecond = currentCount / 60.0;
                
                metrics.put("eventsPerSecond", Math.round(eventsPerSecond * 100.0) / 100.0);
                metrics.put("averageLatency", 0.0);
                metrics.put("errorRate", 0.0);
                metrics.put("activeConnections", Thread.activeCount());
                
                lastEventCount.set(currentCount);
                lastCheckTime.set(now);
                
                return metrics;
            })
            .exceptionally(ex -> {
                log.error("Error calculating application metrics", ex);
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("eventsPerSecond", 0.0);
                metrics.put("averageLatency", 0.0);
                metrics.put("errorRate", 0.0);
                metrics.put("activeConnections", Thread.activeCount());
                return metrics;
            });
    }

    public CompletableFuture<Void> aggregateMetricsForLastHour() {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.debug("Aggregating metrics for last hour");
            return null;
        });
    }

    public CompletableFuture<Void> aggregateMetricsForLastDay() {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.debug("Aggregating metrics for last day");
            return null;
        });
    }

    public CompletableFuture<Void> aggregateMetricsForLastMonth() {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.debug("Aggregating metrics for last month");
            return null;
        });
    }

    public CompletableFuture<Void> syncMetricsToElasticsearch() {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.debug("Syncing metrics to Elasticsearch");
            return null;
        });
    }

    public CompletableFuture<Void> refreshMetricsCache() {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.debug("Refreshing metrics cache");
            return null;
        });
    }

    public CompletableFuture<Void> processMetricsFromKafka(ForwarderMetricsDTO metrics) {
        return AsyncHelper.executeAsyncFuture(() -> {
            try {
                log.debug("Processing metrics from Kafka for forwarder: {}", metrics.getForwarderId());

                if (metrics == null || metrics.getForwarderId() == null) {
                    log.warn("Received null or invalid metrics");
                    return null;
                }

                log.info("Forwarder {} - Events Processed: {}, CPU: {}%, Memory: {} bytes, Queue Depth: {}",
                        metrics.getForwarderId(),
                        metrics.getEventsProcessed(),
                        metrics.getCpuUsagePercent(),
                        metrics.getMemoryUsageBytes(),
                        metrics.getQueueDepth());

            } catch (Exception e) {
                log.error("Error processing metrics from Kafka: {}", e.getMessage(), e);
            }
            return null;
        });
    }
}
