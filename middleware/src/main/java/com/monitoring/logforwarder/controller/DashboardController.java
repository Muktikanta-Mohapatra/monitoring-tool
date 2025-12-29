package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.service.EventService;
import com.monitoring.logforwarder.service.ForwarderService;
import com.monitoring.logforwarder.service.AlertService;
import com.monitoring.logforwarder.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for dashboard data aggregation.
 *
 * <p><b>Purpose:</b> Provides aggregated data endpoints for the web dashboard,
 * including system summaries, recent logs, and health status.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/dashboard</li>
 *   <li>Aggregates data from multiple services</li>
 *   <li>Optimized for dashboard rendering performance</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventService
 * @see ForwarderService
 * @see AlertService
 * @see MetricsService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private EventService eventService;

    @Autowired
    private ForwarderService forwarderService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private MetricsService metricsService;

    /**
     * Retrieves comprehensive dashboard summary data.
     *
     * <p><b>Purpose:</b> Returns aggregated metrics including event counts, forwarder status,
     * alert counts, and system metrics for the main dashboard view.</p>
     *
     * @return dashboard summary with events, forwarders, alerts, and metrics
     */
    @GetMapping("/summary")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getDashboardSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusDays(1);
        
        return CompletableFuture.allOf(
            forwarderService.getAllForwarders(),
            forwarderService.getActiveForwarders(),
            alertService.getUnacknowledgedAlertCount(),
            metricsService.getSystemMetrics(),
            metricsService.getApplicationMetrics()
        ).thenApply(v -> {
            try {
                Map<String, Object> summary = new HashMap<>();
                summary.put("total_events_last_24h", eventService.countEventsByTimeRange(last24h, now).get());
                summary.put("total_forwarders", forwarderService.getAllForwarders().get().size());
                summary.put("active_forwarders", forwarderService.getActiveForwarders().get().size());
                summary.put("unacknowledged_alerts", alertService.getUnacknowledgedAlertCount().get());
                summary.put("system_metrics", metricsService.getSystemMetrics().get());
                summary.put("app_metrics", metricsService.getApplicationMetrics().get());
                
                return ResponseEntity.ok(ApiResponseDTO.builder()
                    .success(true)
                    .message("Dashboard summary retrieved")
                    .code("DASHBOARD_SUMMARY_SUCCESS")
                    .data(summary)
                    .timestamp(LocalDateTime.now())
                    .build());
            } catch (Exception ex) {
                log.error("Error building dashboard summary", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("DASHBOARD_SUMMARY_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }).exceptionally(ex -> {
            log.error("Error getting dashboard summary", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .code("DASHBOARD_SUMMARY_FAILED")
                    .timestamp(LocalDateTime.now())
                    .build());
        });
    }

    /**
     * Retrieves recent logs for the dashboard log panel.
     *
     * @param limit maximum number of logs to return (default: 100)
     * @return list of recent log events
     */
    @GetMapping("/logs/recent")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getRecentLogs(
            @RequestParam(defaultValue = "100") Integer limit) {
        return eventService.getRecentEvents(limit)
            .thenApply(logs -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Recent logs retrieved")
                .code("GET_RECENT_LOGS_SUCCESS")
                .data(logs)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving recent logs", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("GET_RECENT_LOGS_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    /**
     * Retrieves system health metrics.
     *
     * @return current system health status and metrics
     */
    @GetMapping("/health")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getSystemHealth() {
        return metricsService.getSystemMetrics()
            .thenApply(metrics -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("System health retrieved")
                .code("HEALTH_CHECK_SUCCESS")
                .data(metrics)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving system health", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("HEALTH_CHECK_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }
}
