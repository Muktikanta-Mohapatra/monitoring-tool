package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.ForwarderDTO;
import com.monitoring.logforwarder.service.ForwarderService;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
public class ForwarderController {

    @Autowired
    private ForwarderService forwarderService;

    /**
     * Retrieves all registered forwarders.
     *
     * @return list of all forwarders with status
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getAllForwarders() {
        return forwarderService.getAllForwarders()
            .thenApply(forwarders -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Forwarders retrieved")
                .code("GET_FORWARDERS_SUCCESS")
                .data(forwarders)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving forwarders", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("GET_FORWARDERS_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
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
            .thenApply(forwarder -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Forwarder retrieved")
                .code("GET_FORWARDER_SUCCESS")
                .data(forwarder)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving forwarder", ex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("FORWARDER_NOT_FOUND")
                        .timestamp(LocalDateTime.now())
                        .build());
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
            .thenApply(forwarder -> ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.builder()
                    .success(true)
                    .message("Forwarder registered")
                    .code("FORWARDER_REGISTERED")
                    .data(forwarder)
                    .timestamp(LocalDateTime.now())
                    .build()))
            .exceptionally(ex -> {
                log.error("Error registering forwarder", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("REGISTRATION_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
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
            .thenApply(v -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Heartbeat received")
                .code("HEARTBEAT_SUCCESS")
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error processing heartbeat", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("HEARTBEAT_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
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
            .thenApply(totalEvents -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Metrics retrieved")
                .code("GET_METRICS_SUCCESS")
                .data(totalEvents)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving metrics", ex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("GET_METRICS_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }
}