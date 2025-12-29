package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.EventBatchDTO;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.service.EventService;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for log event management operations.
 *
 * <p><b>Purpose:</b> Handles event ingestion, search, and retrieval operations.
 * Provides endpoints for batch event ingestion from forwarders and event
 * querying for the web dashboard.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/events</li>
 *   <li>Batch endpoint (/batch) is public for forwarder access</li>
 *   <li>Search and retrieval endpoints require authentication</li>
 *   <li>Supports time-range filtering, sourcetype, and severity filters</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Batch ingestion
 * POST /api/v1/events/batch
 * {
 *   "forwarderId": "fwd-001",
 *   "events": [{"rawMessage": "Log entry", "severity": "INFO"}]
 * }
 *
 * // Search events
 * GET /api/v1/events/search?startTime=2024-01-01T00:00:00&severity=ERROR
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@CrossOrigin(origins = "*")
public class EventController {

    @Autowired
    private EventService eventService;

    /**
     * Ingests a batch of events from a log forwarder.
     *
     * <p><b>Purpose:</b> Receives and processes batched log events from forwarders for storage and analysis.</p>
     *
     * @param batchDTO the batch of events to ingest
     * @return processed batch confirmation or error details
     */
    private static volatile int batchSequence = 0;
    
    @PostMapping("/batch")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> ingestEventBatch(@Valid @RequestBody EventBatchDTO batchDTO) {
        String batchId = UUID.randomUUID().toString();
        batchDTO.setBatchId(batchId);
        int sequenceNum = ++batchSequence;
        
        log.debug("Received async batch request: {}", batchId);
        log.warn("CRITICAL: HTTP LAYER REQUEST #{} - Received batch with {} events", 
            sequenceNum, batchDTO.getEvents() != null ? batchDTO.getEvents().size() : 0);
        
        if (batchDTO.getEvents() != null && batchDTO.getEvents().size() > 100) {
            log.warn("CRITICAL: Batch #{} size exceeds 100 events - Batch ID: {}, Size: {}", 
                sequenceNum, batchId, batchDTO.getEvents().size());
        }
        
        return AsyncHelper.executeAsyncFuture(() -> {
            eventService.saveBatch(batchDTO);
            
            ApiResponseDTO response = ApiResponseDTO.builder()
                .success(true)
                .message("Event batch queued for processing with ID: " + batchId)
                .code("BATCH_QUEUED")
                .data(batchDTO)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }).exceptionally(ex -> {
            log.error("Error ingesting event batch: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .code("BATCH_FAILED")
                    .timestamp(LocalDateTime.now())
                    .build());
        });
    }

    /**
     * Searches events with optional filters and pagination.
     *
     * <p><b>Purpose:</b> Provides flexible event search with time range, sourcetype, and severity filtering.</p>
     *
     * @param startTime optional start time filter (ISO-8601 format with timezone, e.g., 2025-12-20T14:07:28.329Z)
     * @param endTime optional end time filter (ISO-8601 format with timezone, e.g., 2025-12-21T14:07:28.329Z)
     * @param sourcetype optional sourcetype filter
     * @param severity optional severity filter (DEBUG, INFO, WARN, ERROR, FATAL)
     * @param page page number (0-indexed, default: 0)
     * @param pageSize results per page (default: 50)
     * @return paginated search results
     */
    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> searchEvents(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sourcetype,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        try {
            LocalDateTime start = startTime != null 
                ? LocalDateTime.ofInstant(Instant.parse(startTime), ZoneId.systemDefault())
                : LocalDateTime.now().minusDays(7);
            LocalDateTime end = endTime != null 
                ? LocalDateTime.ofInstant(Instant.parse(endTime), ZoneId.systemDefault())
                : LocalDateTime.now();
            
            if (page < 0) page = 0;
            if (pageSize <= 0 || pageSize > 1000) pageSize = 50;
            
            return eventService.searchEvents(start, end, sourcetype, severity, page, pageSize)
                .thenApply(results -> ResponseEntity.ok(ApiResponseDTO.builder()
                    .success(true)
                    .message("Search completed successfully with " + results.getTotalElements() + " total results")
                    .code("SEARCH_SUCCESS")
                    .data(results)
                    .timestamp(LocalDateTime.now())
                    .build()))
                .exceptionally(ex -> {
                    log.error("Error searching events", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponseDTO.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .code("SEARCH_FAILED")
                            .timestamp(LocalDateTime.now())
                            .build());
                });
        } catch (IllegalArgumentException ex) {
            log.error("Invalid search parameters: {}", ex.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message("Invalid date format. Use ISO-8601 format with timezone: yyyy-MM-ddTHH:mm:ss.SSSZ")
                        .code("INVALID_DATE_FORMAT")
                        .timestamp(LocalDateTime.now())
                        .build())
            );
        }
    }

    /**
     * Performs a full-text search across recent events.
     *
     * <p><b>Purpose:</b> Enables searching for events using keywords across message, sourcetype, and other fields.</p>
     *
     * @param query search query keyword
     * @param limit maximum number of recent events to search through (default: 1000)
     * @return list of matching events
     */
    @GetMapping("/search/fulltext")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> fullTextSearch(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1000") Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest()
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message("Search query cannot be empty")
                        .code("EMPTY_QUERY")
                        .timestamp(LocalDateTime.now())
                        .build())
            );
        }

        final String queryLower = query.toLowerCase();
        final int searchLimit = limit != null && limit > 0 && limit <= 10000 ? limit : 1000;

        return eventService.getRecentEvents(searchLimit)
            .thenApply(events -> {
                var results = events.stream()
                    .filter(event -> matchesQuery(event, queryLower))
                    .collect(java.util.stream.Collectors.toList());
                
                return ResponseEntity.ok(ApiResponseDTO.builder()
                    .success(true)
                    .message("Full-text search completed with " + results.size() + " matches")
                    .code("SEARCH_SUCCESS")
                    .data(results)
                    .timestamp(LocalDateTime.now())
                    .build());
            })
            .exceptionally(ex -> {
                log.error("Error performing full-text search", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("SEARCH_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    private boolean matchesQuery(EventDTO event, String queryLower) {
        if (event.getRawMessage() != null && event.getRawMessage().toLowerCase().contains(queryLower)) {
            return true;
        }
        if (event.getRawData() != null && event.getRawData().toLowerCase().contains(queryLower)) {
            return true;
        }
        if (event.getSourcetype() != null && event.getSourcetype().toLowerCase().contains(queryLower)) {
            return true;
        }
        if (event.getSeverity() != null && event.getSeverity().toLowerCase().contains(queryLower)) {
            return true;
        }
        if (event.getSourceName() != null && event.getSourceName().toLowerCase().contains(queryLower)) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves a specific event by its ID.
     *
     * <p><b>Purpose:</b> Fetches detailed information for a single event.</p>
     *
     * @param eventId the unique event identifier
     * @return event details or 404 if not found
     */
    @GetMapping("/{eventId}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getEventById(@PathVariable Long eventId) {
        return eventService.getEventById(eventId)
            .thenApply(event -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Event retrieved")
                .code("GET_EVENT_SUCCESS")
                .data(event)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving event", ex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("EVENT_NOT_FOUND")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }

    /**
     * Retrieves the most recent events.
     *
     * <p><b>Purpose:</b> Returns the latest events for real-time dashboard display.</p>
     *
     * @param limit maximum number of events to return (default: 100)
     * @return list of recent events
     */
    @GetMapping("/recent")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getRecentEvents(
            @RequestParam(defaultValue = "100") Integer limit) {
        return eventService.getRecentEvents(limit)
            .thenApply(events -> ResponseEntity.ok(ApiResponseDTO.builder()
                .success(true)
                .message("Recent events retrieved")
                .code("GET_RECENT_SUCCESS")
                .data(events)
                .timestamp(LocalDateTime.now())
                .build()))
            .exceptionally(ex -> {
                log.error("Error retrieving recent events", ex);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDTO.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .code("GET_RECENT_FAILED")
                        .timestamp(LocalDateTime.now())
                        .build());
            });
    }
}
