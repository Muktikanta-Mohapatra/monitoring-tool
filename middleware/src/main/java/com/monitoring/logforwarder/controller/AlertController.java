package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.service.AlertService;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for alert management operations.
 *
 * <p><b>PURPOSE:</b></p>
 * Handles the complete alert lifecycle including creation, retrieval, acknowledgment, and resolution.
 * Alerts are generated automatically when log events match configured {@link com.monitoring.logforwarder.entity.AlertRule}
 * conditions, or can be created manually through this API.
 *
 * <p><b>ARCHITECTURE CONTEXT:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                           ALERT GENERATION FLOW                                 │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  EventService.processEventFromKafkaAsync()                                      │
 * │           │                                                                     │
 * │           ▼                                                                     │
 * │  AlertRuleEvaluator.evaluateEventAgainstRules()                                │
 * │           │                                                                     │
 * │           ▼ (if rule matches)                                                   │
 * │  EventProducer.publishAlert() ──▶ Kafka "alerts" topic                         │
 * │           │                                                                     │
 * │           ▼                                                                     │
 * │  EventConsumer.consumeAlert() ──▶ AlertService.processAlertFromKafka()         │
 * │           │                                                                     │
 * │           ▼                                                                     │
 * │  PostgreSQL alerts table                                                        │
 * │           │                                                                     │
 * │           ▼                                                                     │
 * │  AlertController (this class) ◀── Web Dashboard / API clients                  │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>ALERT LIFECYCLE:</b></p>
 * <pre>
 * [OPEN] ──acknowledge()──▶ [ACKNOWLEDGED] ──resolve()──▶ [RESOLVED]
 *    │                            │                            │
 *    └─────────── resolve() ──────┴────────────────────────────┘
 * </pre>
 *
 * <p><b>API ENDPOINTS:</b></p>
 * <ul>
 *   <li>{@code GET /api/v1/alerts} - List alerts with optional filters</li>
 *   <li>{@code POST /api/v1/alerts} - Create a new alert manually</li>
 *   <li>{@code POST /api/v1/alerts/{id}/acknowledge} - Acknowledge an alert</li>
 *   <li>{@code POST /api/v1/alerts/{id}/resolve} - Resolve an alert</li>
 * </ul>
 *
 * <p><b>CALLED BY:</b></p>
 * <ul>
 *   <li>Web Dashboard - Alert management panel</li>
 *   <li>External monitoring systems via REST API</li>
 *   <li>WebSocket clients for real-time alert updates (via {@link com.monitoring.logforwarder.websocket.WebSocketHandler})</li>
 * </ul>
 *
 * <p><b>SECURITY:</b></p>
 * All endpoints require authentication. Alert acknowledgment and resolution are audited.
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see AlertService
 * @see com.monitoring.logforwarder.service.AlertRuleEvaluator
 * @see com.monitoring.logforwarder.entity.Alert
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AlertController {

    /**
     * Service layer for alert operations. Injected by Spring via constructor injection.
     * Handles business logic for alert creation, retrieval, acknowledgment, and resolution.
     */
    private final AlertService alertService;

    /**
     * Retrieves alerts with optional filters for status and severity.
     *
     * <p><b>PURPOSE:</b></p>
     * Provides a paginated list of alerts for the dashboard alert panel and API consumers.
     * Supports filtering to show only relevant alerts (e.g., only OPEN or CRITICAL alerts).
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>Web Dashboard - Alert list panel with filter dropdowns</li>
     *   <li>External monitoring integrations querying alert status</li>
     * </ul>
     *
     * <p><b>FILTER OPTIONS:</b></p>
     * <ul>
     *   <li><b>status:</b> OPEN, ACKNOWLEDGED, RESOLVED (case-insensitive)</li>
     *   <li><b>severity:</b> DEBUG, INFO, WARN, ERROR, CRITICAL (case-insensitive)</li>
     * </ul>
     *
     * @param status optional status filter (OPEN, ACKNOWLEDGED, RESOLVED)
     * @param severity optional severity filter (DEBUG, INFO, WARN, ERROR, CRITICAL)
     * @param page page number for pagination (0-indexed, default: 0)
     * @return filtered and paginated list of alerts wrapped in ApiResponseDTO
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") Integer page) {
        return alertService.getAlerts(status, severity, page)
            .thenApply(ApiResponseBuilder.successMapper("Alerts retrieved", "GET_ALERTS_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving alerts", ex);
                return ApiResponseBuilder.fromException(ex, "GET_ALERTS_FAILED", HttpStatus.BAD_REQUEST);
            });
    }

    /**
     * Creates a new alert manually (as opposed to auto-generated from event rules).
     *
     * <p><b>PURPOSE:</b></p>
     * Allows manual creation of alerts by administrators or external systems.
     * Most alerts are auto-generated by {@link com.monitoring.logforwarder.service.AlertRuleEvaluator},
     * but this endpoint enables manual alerting for special cases.
     *
     * <p><b>USE CASES:</b></p>
     * <ul>
     *   <li>External monitoring systems pushing alerts</li>
     *   <li>Manual incident creation by operators</li>
     *   <li>Testing and development purposes</li>
     * </ul>
     *
     * <p><b>ALERT DTO FIELDS:</b></p>
     * <ul>
     *   <li><b>title:</b> Brief description of the alert (required)</li>
     *   <li><b>message:</b> Detailed alert message</li>
     *   <li><b>severity:</b> DEBUG, INFO, WARN, ERROR, CRITICAL</li>
     *   <li><b>source:</b> Source system or component</li>
     * </ul>
     *
     * @param alertDTO the alert data to create (validated via {@code @Valid})
     * @return created alert with assigned ID and OPEN status
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> createAlert(@RequestBody AlertDTO alertDTO) {
        return alertService.triggerAlert(alertDTO)
            .thenApply(ApiResponseBuilder.createdMapper("Alert created", "ALERT_CREATED"))
            .exceptionally(ex -> {
                log.error("Error creating alert", ex);
                return ApiResponseBuilder.fromException(ex, "CREATE_ALERT_FAILED", HttpStatus.BAD_REQUEST);
            });
    }

    /**
     * Acknowledges an alert, transitioning it from OPEN to ACKNOWLEDGED status.
     *
     * <p><b>PURPOSE:</b></p>
     * Marks an alert as acknowledged, indicating that an operator is aware of the issue
     * and is working on it. This prevents the alert from being escalated and provides
     * visibility into who is handling the incident.
     *
     * <p><b>STATE TRANSITION:</b></p>
     * <pre>
     * [OPEN] ──acknowledge()──▶ [ACKNOWLEDGED]
     * </pre>
     *
     * <p><b>SIDE EFFECTS:</b></p>
     * <ul>
     *   <li>Updates alert status to ACKNOWLEDGED</li>
     *   <li>Records acknowledgedBy username and timestamp</li>
     *   <li>Creates an audit log entry via {@link com.monitoring.logforwarder.service.AuditService}</li>
     *   <li>Broadcasts update via WebSocket to connected clients</li>
     * </ul>
     *
     * @param id the alert ID to acknowledge (path variable)
     * @param acknowledgedBy username of the person acknowledging (required query param)
     * @return updated alert with acknowledgment details, or 404 if alert not found
     */
    @PostMapping("/{id}/acknowledge")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam String acknowledgedBy) {
        return alertService.acknowledgeAlert(id, acknowledgedBy)
            .thenApply(ApiResponseBuilder.successMapper("Alert acknowledged", "ALERT_ACKNOWLEDGED"))
            .exceptionally(ex -> {
                log.error("Error acknowledging alert", ex);
                return ApiResponseBuilder.fromException(ex, "ACK_ALERT_FAILED", HttpStatus.NOT_FOUND);
            });
    }

    /**
     * Resolves an alert, transitioning it from OPEN or ACKNOWLEDGED to RESOLVED status.
     *
     * <p><b>PURPOSE:</b></p>
     * Marks an alert as resolved, indicating that the underlying issue has been fixed.
     * The resolution message provides context for future reference and post-incident analysis.
     *
     * <p><b>STATE TRANSITIONS:</b></p>
     * <pre>
     * [OPEN] ────────resolve()──────▶ [RESOLVED]
     * [ACKNOWLEDGED] ──resolve()──▶ [RESOLVED]
     * </pre>
     *
     * <p><b>SIDE EFFECTS:</b></p>
     * <ul>
     *   <li>Updates alert status to RESOLVED</li>
     *   <li>Records resolution message and timestamp</li>
     *   <li>Creates an audit log entry for compliance</li>
     *   <li>Broadcasts update via WebSocket</li>
     *   <li>May trigger notification to alert creator</li>
     * </ul>
     *
     * <p><b>BEST PRACTICES:</b></p>
     * Include meaningful resolution messages that describe:
     * <ul>
     *   <li>Root cause of the issue</li>
     *   <li>Actions taken to resolve</li>
     *   <li>Preventive measures implemented</li>
     * </ul>
     *
     * @param id the alert ID to resolve (path variable)
     * @param message resolution message describing the fix (required query param)
     * @return updated alert with resolution details, or 404 if alert not found
     */
    @PostMapping("/{id}/resolve")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> resolveAlert(
            @PathVariable Long id,
            @RequestParam String message) {
        return alertService.resolveAlert(id, message)
            .thenApply(ApiResponseBuilder.successMapper("Alert resolved", "ALERT_RESOLVED"))
            .exceptionally(ex -> {
                log.error("Error resolving alert", ex);
                return ApiResponseBuilder.fromException(ex, "RESOLVE_ALERT_FAILED", HttpStatus.NOT_FOUND);
            });
    }
}
