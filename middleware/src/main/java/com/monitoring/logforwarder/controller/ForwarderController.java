package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.ForwarderDTO;
import com.monitoring.logforwarder.service.ForwarderService;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for log forwarder management.
 *
 * <p><b>Purpose:</b> Manages log forwarder registration, status updates,
 * heartbeat monitoring, and metrics retrieval.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/forwarders</li>
 *   <li>Supports forwarder registration and discovery</li>
 *   <li>Heartbeat endpoint for health monitoring</li>
 *   <li>Per-forwarder metrics retrieval</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ForwarderService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/forwarders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ForwarderController {

    private final ForwarderService forwarderService;

    /**
     * Retrieves all registered forwarders.
     *
     * @return list of all forwarders with status
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getAllForwarders() {
        return forwarderService.getAllForwarders()
            .thenApply(ApiResponseBuilder.successMapper("Forwarders retrieved", "GET_FORWARDERS_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving forwarders", ex);
                return ApiResponseBuilder.fromException(ex, "GET_FORWARDERS_FAILED", HttpStatus.BAD_REQUEST);
            });
    }

    /**
     * Retrieves a specific forwarder by ID.
     *
     * @param id the forwarder identifier
     * @return forwarder details or 404 if not found
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getForwarderById(@PathVariable String id) {
        return forwarderService.getForwarderById(id)
            .thenApply(ApiResponseBuilder.successMapper("Forwarder retrieved", "GET_FORWARDER_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving forwarder", ex);
                return ApiResponseBuilder.fromException(ex, "FORWARDER_NOT_FOUND", HttpStatus.NOT_FOUND);
            });
    }

    /**
     * Registers a new log forwarder.
     *
     * @param forwarderDTO the forwarder registration data
     * @return registered forwarder with assigned ID
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('FORWARDER')")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> registerForwarder(@RequestBody ForwarderDTO forwarderDTO) {
        return forwarderService.registerForwarder(forwarderDTO)
            .thenApply(ApiResponseBuilder.createdMapper("Forwarder registered", "FORWARDER_REGISTERED"))
            .exceptionally(ex -> {
                log.error("Error registering forwarder", ex);
                return ApiResponseBuilder.fromException(ex, "REGISTRATION_FAILED", HttpStatus.BAD_REQUEST);
            });
    }

    /**
     * Receives a heartbeat from a forwarder to indicate it's active.
     * Requires X-API-Key header for authentication.
     *
     * @param id the forwarder identifier
     * @return success confirmation
     */
    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("hasAnyAuthority('FORWARDER')")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> heartbeat(@PathVariable String id) {
        return forwarderService.updateForwarderStatus(id, "ACTIVE")
            .thenApply(v -> ApiResponseBuilder.success("Heartbeat received", "HEARTBEAT_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error processing heartbeat", ex);
                return ApiResponseBuilder.fromException(ex, "HEARTBEAT_FAILED", HttpStatus.BAD_REQUEST);
            });
    }

    /**
     * Retrieves metrics for a specific forwarder.
     *
     * @param id the forwarder identifier
     * @return forwarder metrics (total events processed)
     */
    @GetMapping("/{id}/metrics")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getMetrics(@PathVariable String id) {
        return forwarderService.getTotalEventsProcessed(id)
            .thenApply(ApiResponseBuilder.successMapper("Metrics retrieved", "GET_METRICS_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving metrics", ex);
                return ApiResponseBuilder.fromException(ex, "GET_METRICS_FAILED", HttpStatus.NOT_FOUND);
            });
    }
}
