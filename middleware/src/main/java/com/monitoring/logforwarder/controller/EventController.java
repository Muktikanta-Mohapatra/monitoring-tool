package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.EventBatchDTO;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.service.EventService;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p><b>PURPOSE:</b></p>
 * This controller serves as the HTTP-based event ingestion endpoint (alternative to gRPC)
 * and provides search/retrieval APIs for the web dashboard. It handles two primary use cases:
 * <ol>
 *   <li><b>Event Ingestion:</b> Receives batched log events from LogForwarder agents via HTTP POST</li>
 *   <li><b>Event Querying:</b> Provides search and retrieval APIs for the web dashboard</li>
 * </ol>
 *
 * <p><b>ARCHITECTURE CONTEXT:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                         HTTP EVENT INGESTION PATH                               │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  LogForwarder (Rust)                                                            │
 * │  ┌─────────────────────────────┐                                                │
 * │  │ outputs/mod.rs:127          │  ←── OutputSender.send_batch_internal()        │
 * │  │ HTTP POST with JSON payload │                                                │
 * │  └─────────────┬───────────────┘                                                │
 * │                │                                                                │
 * │                ▼                                                                │
 * │  ┌─────────────────────────────┐                                                │
 * │  │ EventController (this class)│  ←── POST /api/v1/events/batch                 │
 * │  │ ingestEventBatch():74       │                                                │
 * │  └─────────────┬───────────────┘                                                │
 * │                │                                                                │
 * │                ▼                                                                │
 * │  ┌─────────────────────────────┐                                                │
 * │  │ EventService.saveBatch()    │                                                │
 * │  │ EventService.java:71        │                                                │
 * │  └─────────────┬───────────────┘                                                │
 * │                │                                                                │
 * │                ▼                                                                │
 * │  EventBatchProcessor → Kafka "events" topic → ClickHouse                        │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>API ENDPOINTS:</b></p>
 * <ul>
 *   <li>{@code POST /api/v1/events/batch} - Batch event ingestion (public, uses X-API-KEY)</li>
 *   <li>{@code GET /api/v1/events/search} - Search events with filters</li>
 *   <li>{@code GET /api/v1/events/search/fulltext} - Full-text search across events</li>
 *   <li>{@code GET /api/v1/events/{eventId}} - Retrieve single event by ID</li>
 *   <li>{@code GET /api/v1/events/recent} - Get most recent events</li>
 * </ul>
 *
 * <p><b>AUTHENTICATION:</b></p>
 * <ul>
 *   <li><b>/batch:</b> Authenticated via X-API-KEY header (see {@link com.monitoring.logforwarder.security.ForwarderAuthFilter})</li>
 *   <li><b>Other endpoints:</b> Require JWT Bearer token authentication</li>
 * </ul>
 *
 * <p><b>COMPARISON: HTTP vs gRPC INGESTION:</b></p>
 * <table border="1">
 *   <tr><th>Aspect</th><th>HTTP (this controller)</th><th>gRPC (ForwarderGrpcService)</th></tr>
 *   <tr><td>Protocol</td><td>HTTP/1.1 or HTTP/2</td><td>HTTP/2 (required)</td></tr>
 *   <tr><td>Payload</td><td>JSON</td><td>Protobuf (binary)</td></tr>
 *   <tr><td>Streaming</td><td>Request/Response</td><td>Bidirectional streaming</td></tr>
 *   <tr><td>Efficiency</td><td>Lower (text parsing)</td><td>Higher (binary, streaming)</td></tr>
 *   <tr><td>Compression</td><td>Optional (zstd header)</td><td>Built-in</td></tr>
 * </table>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventService
 * @see com.monitoring.logforwarder.grpc.ForwarderGrpcService
 * @see com.monitoring.logforwarder.security.ForwarderAuthFilter
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Ingests a batch of events from a log forwarder via HTTP POST.
     *
     * <p><b>PURPOSE:</b></p>
     * This is the PRIMARY HTTP endpoint for log event ingestion. LogForwarder agents configured
     * with {@code protocol: "http"} send batched events here. This endpoint is an alternative
     * to the gRPC streaming endpoint ({@link com.monitoring.logforwarder.grpc.ForwarderGrpcService#sendEvents}).
     *
     * <p><b>REQUEST FORMAT:</b></p>
     * <pre>
     * POST /api/v1/events/batch
     * Content-Type: application/json
     * X-API-KEY: &lt;forwarder-api-key&gt;
     * Content-Encoding: zstd (optional, if compressed)
     *
     * {
     *   "forwarderId": "fwd-001",
     *   "apiKey": "xxx",
     *   "events": [
     *     {
     *       "timestamp": "2024-01-01T00:00:00Z",
     *       "rawData": "Log message content",
     *       "sourcetype": "syslog",
     *       "severity": "INFO",
     *       "parsedFields": {"key": "value"},
     *       "enrichedFields": {"geo": "US"}
     *     }
     *   ]
     * }
     * </pre>
     *
     * <p><b>PROCESSING FLOW:</b></p>
     * <ol>
     *   <li>Request authenticated via {@link com.monitoring.logforwarder.security.ForwarderAuthFilter}</li>
     *   <li>Batch assigned a unique UUID (batchId)</li>
     *   <li>{@link EventService#saveBatch} queues events to {@link com.monitoring.logforwarder.batch.EventBatchProcessor}</li>
     *   <li>Events published to Kafka "events" topic asynchronously</li>
     *   <li>HTTP 202 Accepted returned immediately (async processing)</li>
     * </ol>
     *
     * <p><b>CALLED BY:</b></p>
     * <ul>
     *   <li>{@code logforwarder/src/outputs/mod.rs:127} - OutputSender.send_batch_internal()</li>
     *   <li>External log shipping tools configured to use HTTP</li>
     * </ul>
     *
     * @param batchDTO the batch of events to ingest (validated for non-null events)
     * @return HTTP 202 Accepted with batch ID, or error response
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
            return ApiResponseBuilder.accepted(
                "Event batch queued for processing with ID: " + batchId, 
                "BATCH_QUEUED", 
                batchDTO
            );
        }).exceptionally(ex -> {
            log.error("Error ingesting event batch: {}", ex.getMessage(), ex);
            return ApiResponseBuilder.fromException(ex, "BATCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
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
                .thenApply(results -> ApiResponseBuilder.success(
                    "Search completed successfully with " + results.getTotalElements() + " total results",
                    "SEARCH_SUCCESS",
                    results
                ))
                .exceptionally(ex -> {
                    log.error("Error searching events", ex);
                    return ApiResponseBuilder.fromException(ex, "SEARCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (IllegalArgumentException ex) {
            log.error("Invalid search parameters: {}", ex.getMessage());
            return CompletableFuture.completedFuture(
                ApiResponseBuilder.badRequest(
                    "Invalid date format. Use ISO-8601 format with timezone: yyyy-MM-ddTHH:mm:ss.SSSZ",
                    "INVALID_DATE_FORMAT"
                )
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
                ApiResponseBuilder.badRequest("Search query cannot be empty", "EMPTY_QUERY")
            );
        }

        final String queryLower = query.toLowerCase();
        final int searchLimit = limit != null && limit > 0 && limit <= 10000 ? limit : 1000;

        return eventService.getRecentEvents(searchLimit)
            .thenApply(events -> {
                var results = events.stream()
                    .filter(event -> matchesQuery(event, queryLower))
                    .collect(java.util.stream.Collectors.toList());
                
                return ApiResponseBuilder.success(
                    "Full-text search completed with " + results.size() + " matches",
                    "SEARCH_SUCCESS",
                    results
                );
            })
            .exceptionally(ex -> {
                log.error("Error performing full-text search", ex);
                return ApiResponseBuilder.fromException(ex, "SEARCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
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
            .thenApply(ApiResponseBuilder.successMapper("Event retrieved", "GET_EVENT_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving event", ex);
                return ApiResponseBuilder.fromException(ex, "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND);
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
            .thenApply(ApiResponseBuilder.successMapper("Recent events retrieved", "GET_RECENT_SUCCESS"))
            .exceptionally(ex -> {
                log.error("Error retrieving recent events", ex);
                return ApiResponseBuilder.fromException(ex, "GET_RECENT_FAILED", HttpStatus.BAD_REQUEST);
            });
    }
}
