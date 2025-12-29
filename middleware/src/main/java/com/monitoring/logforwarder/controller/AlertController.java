package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for alert management operations.
 *
 * <p><b>Purpose:</b> Handles alert lifecycle including creation, retrieval,
 * acknowledgment, and resolution. Enables monitoring and response to
 * critical system events.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/alerts</li>
 *   <li>Supports filtering by status (OPEN, ACKNOWLEDGED, RESOLVED) and severity</li>
 *   <li>Alert acknowledgment and resolution workflows</li>
 *   <li>Requires authentication for all operations</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Get open alerts
 * GET /api/v1/alerts?status=OPEN&severity=CRITICAL
 *
 * // Acknowledge an alert
 * POST /api/v1/alerts/123/acknowledge?acknowledgedBy=admin
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see AlertService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    @Autowired
    private AlertService alertService;

    /**
     * Retrieves alerts with optional filters.
     *
     * @param status optional status filter (OPEN, ACKNOWLEDGED, RESOLVED)
     * @param severity optional severity filter
     * @param page page number for pagination (default: 0)
     * @return filtered list of alerts
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") Integer page) {
        return alertService.getAlerts(status, severity, page)
            .thenApply(alerts -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Alerts retrieved")
                .code("GET_ALERTS_SUCCESS")
                .data(alerts)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving alerts", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("GET_ALERTS_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    /**
     * Creates a new alert.
     *
     * @param alertDTO the alert data to create
     * @return created alert with assigned ID
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> createAlert(@RequestBody AlertDTO alertDTO) {
        return alertService.triggerAlert(alertDTO)
            .thenApply(alert -> ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.builder()
                    .success(true)
                    .message("Alert created")
                    .code("ALERT_CREATED")
                    .data(alert)
                    .timestamp(LocalDateTime.now())
                    .build()))
            .exceptionally(ex -> {
                log.error("Error creating alert", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("CREATE_ALERT_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    /**
     * Acknowledges an alert.
     *
     * @param id the alert ID to acknowledge
     * @param acknowledgedBy username of the person acknowledging
     * @return updated alert with acknowledgment details
     */
    @PostMapping("/{id}/acknowledge")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam String acknowledgedBy) {
        return alertService.acknowledgeAlert(id, acknowledgedBy)
            .thenApply(alert -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Alert acknowledged")
                .code("ALERT_ACKNOWLEDGED")
                .data(alert)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error acknowledging alert", ex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("ACK_ALERT_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    /**
     * Resolves an alert with a resolution message.
     *
     * @param id the alert ID to resolve
     * @param message resolution message describing the fix
     * @return updated alert with resolution details
     */
    @PostMapping("/{id}/resolve")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> resolveAlert(
            @PathVariable Long id,
            @RequestParam String message) {
        return alertService.resolveAlert(id, message)
            .thenApply(alert -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Alert resolved")
                .code("ALERT_RESOLVED")
                .data(alert)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error resolving alert", ex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("RESOLVE_ALERT_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }
}
