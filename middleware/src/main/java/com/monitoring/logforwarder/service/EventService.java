package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.batch.EventBatchProcessor;
import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.dto.EventBatchDTO;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.entity.Event;
import com.monitoring.logforwarder.exception.DataAccessException;
import com.monitoring.logforwarder.exception.ValidationException;
import com.monitoring.logforwarder.kafka.EventProducer;
import com.monitoring.logforwarder.repository.clickhouse.ClickHouseEventRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for log event management operations.
 *
 * <p><b>Purpose:</b> Handles event lifecycle including batch ingestion, searching,
 * indexing, and processing from Kafka.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Caching for search results</li>
 *   <li>Transaction management for data consistency</li>
 *   <li>Integration with ForwarderService for metrics</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final ClickHouseEventRepository clickHouseEventRepository;

    @Setter
    private ForwarderService forwarderService;

    @Setter
    private EventBatchProcessor eventBatchProcessor;

    @Setter
    private EventProducer eventProducer;

    @Setter
    private AlertRuleEvaluator alertRuleEvaluator;

    private final EventProcessingMetrics processingMetrics;

    @CacheEvict(value = "searchResults", allEntries = true)
    public EventBatchDTO saveBatch(EventBatchDTO batchDTO) {
        if (batchDTO.getEvents() == null || batchDTO.getEvents().isEmpty()) {
            throw new ValidationException("Event batch cannot be empty");
        }

        log.warn("CRITICAL: saveBatch() called for batch {} with {} events from forwarder {}", 
            batchDTO.getBatchId(), batchDTO.getEvents().size(), batchDTO.getForwarderId());

        try {
            List<EventDTO> events = batchDTO.getEvents();
            
            for (EventDTO event : events) {
                processingMetrics.recordEventIngested();
                if (event.getId() == null || event.getId() == 0) {
                    event.setId(generateEventId());
                    log.debug("CRITICAL: Generated event ID at ingestion: {}", event.getId());
                }
            }

            eventBatchProcessor.addEvents(events);
            
            forwarderService.updateForwarderMetrics(
                batchDTO.getForwarderId(), 
                (long) events.size()
            );

            batchDTO.setBatchSize(events.size());
            log.warn("CRITICAL: saveBatch() SUCCESSFULLY QUEUED {} events for batch {}", 
                events.size(), batchDTO.getBatchId());
            return batchDTO;
        } catch (com.monitoring.logforwarder.exception.ApiException ex) {
            log.error("CRITICAL: saveBatch() FAILED with ApiException for batch {} - {}", 
                batchDTO.getBatchId(), ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("CRITICAL: saveBatch() FAILED for batch {} - {}", 
                batchDTO.getBatchId(), ex.getMessage(), ex);
            throw new DataAccessException("Failed to save event batch: " + ex.getMessage(), ex);
        }
    }

    @Cacheable(value = "searchResults", key = "#page + '-' + #pageSize")
    public CompletableFuture<Page<EventDTO>> searchEvents(LocalDateTime startTime, LocalDateTime endTime, 
                                       String sourcetype, String severity, Integer page, Integer pageSize) {
        return clickHouseEventRepository.findByTimestampBetween(startTime, endTime)
            .thenApply(events -> {
                try {
                    List<Event> filtered = events.stream()
                        .filter(e -> e.getTimestamp() != null && 
                               e.getTimestamp().isAfter(startTime) && 
                               e.getTimestamp().isBefore(endTime) &&
                               (sourcetype == null || e.getSourcetype().equals(sourcetype)) &&
                               (severity == null || e.getSeverity().equals(severity)))
                        .skip((long) page * pageSize)
                        .limit(pageSize)
                        .collect(Collectors.toList());

                    int total = (int) events.stream()
                        .filter(e -> e.getTimestamp() != null && 
                               e.getTimestamp().isAfter(startTime) && 
                               e.getTimestamp().isBefore(endTime) &&
                               (sourcetype == null || e.getSourcetype().equals(sourcetype)) &&
                               (severity == null || e.getSeverity().equals(severity)))
                        .count();

                    Pageable pageable = PageRequest.of(page, pageSize);
                    return new org.springframework.data.domain.PageImpl<>(
                        filtered.stream().map(this::entityToDto).collect(Collectors.toList()),
                        pageable,
                        total
                    );
                } catch (Exception ex) {
                    log.error("Error searching events: {}", ex.getMessage(), ex);
                    throw new DataAccessException("Failed to search events: " + ex.getMessage(), ex);
                }
            });
    }

    public CompletableFuture<EventDTO> getEventById(Long eventId) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Event by ID lookup not supported for ClickHouse repository (read-only)");
            throw new DataAccessException("Event lookup by ID not supported");
        });
    }

    public CompletableFuture<List<EventDTO>> getRecentEvents(Integer limit) {
        return clickHouseEventRepository.findMostRecentEvent()
            .thenApply(event -> {
                if (event != null) {
                    return List.of(entityToDto(event));
                }
                return new java.util.ArrayList<EventDTO>();
            })
            .exceptionally(ex -> {
                log.error("Error retrieving recent events: {}", ex.getMessage(), ex);
                return new java.util.ArrayList<EventDTO>();
            });
    }

    public CompletableFuture<Long> countEventsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return clickHouseEventRepository.countEventsByTimeRange(startTime, endTime)
            .exceptionally(ex -> {
                log.error("Error counting events by time range: {}", ex.getMessage(), ex);
                throw new DataAccessException("Failed to count events: " + ex.getMessage(), ex);
            });
    }

    public CompletableFuture<List<String>> getDistinctSourcetypes() {
        return clickHouseEventRepository.findDistinctSourcetypes()
            .exceptionally(ex -> {
                log.error("Error retrieving distinct sourcetypes: {}", ex.getMessage(), ex);
                throw new DataAccessException("Failed to retrieve sourcetypes: " + ex.getMessage(), ex);
            });
    }

    public CompletableFuture<List<String>> getDistinctSeverities() {
        return clickHouseEventRepository.findDistinctSeverities()
            .exceptionally(ex -> {
                log.error("Error retrieving distinct severities: {}", ex.getMessage(), ex);
                throw new DataAccessException("Failed to retrieve severities: " + ex.getMessage(), ex);
            });
    }

    public CompletableFuture<Void> markAsIndexed(Long eventId) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Mark as indexed not supported for ClickHouse repository (read-only)");
            return null;
        });
    }

    public CompletableFuture<Void> processEventFromKafka(EventDTO eventDTO) {
        log.debug("Processing event from Kafka: {}", eventDTO.getId());
        log.info("Event will be persisted via batch processor: {}", eventDTO.getId());
        processingMetrics.recordEventPersisted();

        return alertRuleEvaluator.evaluateEventAgainstRules(eventDTO)
            .thenAccept(triggeredAlerts -> {
                for (AlertDTO alert : triggeredAlerts) {
                    eventProducer.publishAlert(alert);
                }
            })
            .exceptionally(ex -> {
                log.error("Error evaluating alerts for event: {}", eventDTO.getId(), ex);
                return null;
            });
    }

    public void persistEvent(com.forwarder.v1.Event protoEvent) {
        try {
            log.debug("Persisting proto event from forwarder: {}", protoEvent.getForwarderId());
            
            EventDTO event = EventDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .sourceId(1)
                .sourceName(protoEvent.getSourceName())
                .sourcetype("log")
                .rawData(new String(protoEvent.getRawData().toByteArray()))
                .severity(protoEvent.getSeverity())
                .forwarderId(protoEvent.getForwarderId())
                .isIndexed(false)
                .isEnriched(false)
                .build();

            eventBatchProcessor.addEvent(event);
            log.debug("Proto event queued for publishing: {}", protoEvent.getForwarderId());
        } catch (Exception ex) {
            log.error("Error persisting proto event: {}", ex.getMessage(), ex);
            throw new DataAccessException("Failed to persist event: " + ex.getMessage(), ex);
        }
    }

    public CompletableFuture<Void> persistEventAsync(EventDTO eventDTO) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Persisting event async via batch processor: {}", eventDTO.getId());
                eventBatchProcessor.addEvent(eventDTO);
                log.info("Event queued for batch processing: {}", eventDTO.getId());
                processingMetrics.recordEventPersisted();
            } catch (Exception ex) {
                log.error("Error persisting event: {}", eventDTO.getId(), ex);
                processingMetrics.recordEventFailed();
            }
        });
    }

    public CompletableFuture<Void> processEventFromKafkaAsync(EventDTO eventDTO) {
        processingMetrics.recordEventConsumedFromKafka();
        log.info("CRITICAL: Processing event from Kafka async - ID: {}, Forwarder: {}", 
            eventDTO.getId(), eventDTO.getForwarderId());
        
        if (eventDTO == null) {
            log.error("CRITICAL: Null event received from Kafka, cannot process");
            processingMetrics.recordEventFailed();
            return CompletableFuture.failedFuture(new ValidationException("Event payload cannot be null"));
        }
        
        log.debug("CRITICAL: Building Event entity for ID: {}", eventDTO.getId());
        
        Long eventId = eventDTO.getId();
        if (eventId == null || eventId == 0) {
            eventId = generateEventId();
            log.debug("CRITICAL: Generated new event ID: {}", eventId);
        }
        
        Event event = Event.builder()
            .id(eventId)
            .timestamp(eventDTO.getTimestamp() != null ? LocalDateTime.now() : LocalDateTime.now())
            .sourceId(eventDTO.getSourceId() != null ? eventDTO.getSourceId() : 1)
            .sourceName(eventDTO.getSourceName())
            .sourcetype(eventDTO.getSourcetype())
            .rawMessage(eventDTO.getRawData())
            .severity(eventDTO.getSeverity())
            .forwarderId(eventDTO.getForwarderId())
            .batchId(eventDTO.getBatchId())
            .elasticsearchId(eventDTO.getElasticsearchId())
            .isIndexed(false)
            .isEnriched(false)
            .build();
        
        log.debug("CRITICAL: About to insert into ClickHouse - Event ID: {}", eventDTO.getId());
        
        return clickHouseEventRepository.insertEvent(event)
            .thenCompose(insertResult -> {
                log.info("CRITICAL: Insert returned result: {} for event: {}", insertResult, eventDTO.getId());
                
                if (insertResult == null || insertResult <= 0) {
                    processingMetrics.recordEventFailed();
                    log.error("CRITICAL: Database insert FAILED for event {} - returned: {} rows - EVENT WILL NOT BE PERSISTED", 
                        eventDTO.getId(), insertResult);
                    return CompletableFuture.<Void>failedFuture(
                        new DataAccessException("Failed to insert event into ClickHouse: returned " + insertResult + " rows"));
                }
                
                log.info("CRITICAL: Event SUCCESSFULLY PERSISTED to ClickHouse - ID: {}, Rows affected: {}", 
                    eventDTO.getId(), insertResult);
                processingMetrics.recordEventPersisted();

                return alertRuleEvaluator.evaluateEventAgainstRules(eventDTO)
                    .thenAccept(triggeredAlerts -> {
                        if (triggeredAlerts != null && !triggeredAlerts.isEmpty()) {
                            log.debug("Event triggered {} alert(s): {}", triggeredAlerts.size(), eventDTO.getId());
                            triggeredAlerts.forEach(alert -> {
                                try {
                                    eventProducer.publishAlert(alert);
                                } catch (Exception ex) {
                                    log.error("Failed to publish alert for event: {}", eventDTO.getId(), ex);
                                }
                            });
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Error evaluating alerts for event: {}", eventDTO.getId(), ex);
                        return null;
                    });
            })
            .exceptionally(ex -> {
                processingMetrics.recordEventFailed();
                log.error("CRITICAL: Exception processing event from Kafka: ID: {}, Error: {}", 
                    eventDTO.getId(), ex.getMessage(), ex);
                throw new DataAccessException("Failed to process event: " + ex.getMessage(), ex);
            });
    }

    public CompletableFuture<Void> publishAlertAsync(AlertDTO alertDTO) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Publishing alert async: {}", alertDTO.getId());
                eventProducer.publishAlert(alertDTO);
                log.info("Alert published async: {}", alertDTO.getId());
            } catch (Exception ex) {
                log.error("Error publishing alert async: {}", alertDTO.getId(), ex);
            }
        });
    }

    public CompletableFuture<EventDTO> getEventByIdAsync(Long eventId) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Event by ID lookup not supported for ClickHouse repository (read-only)");
            throw new DataAccessException("Event lookup by ID not supported");
        });
    }

    private EventDTO entityToDto(Event event) {
        return EventDTO.builder()
            .id(event.getId())
            .timestamp(event.getTimestamp() != null ? event.getTimestamp().atOffset(ZoneOffset.UTC) : null)
            .sourceId(event.getSourceId())
            .sourceName(event.getSourceName())
            .sourcetype(event.getSourcetype())
            .rawData(event.getRawData())
            .rawMessage(event.getRawMessage())
            .severity(event.getSeverity())
            .hostId(event.getHostId())
            .batchId(event.getBatchId())
            .elasticsearchId(event.getElasticsearchId())
            .createdAt(event.getCreatedAt())
            .updatedAt(event.getUpdatedAt())
            .isIndexed(event.getIsIndexed())
            .isEnriched(event.getIsEnriched())
            .forwarderId(event.getForwarderId())
            .version(event.getVersion())
            .build();
    }

    private Event dtoToEntity(EventDTO dto) {
        return Event.builder()
            .id(dto.getId())
            .timestamp(dto.getTimestamp() != null ? dto.getTimestamp().toLocalDateTime() : LocalDateTime.now())
            .sourceId(dto.getSourceId() != null ? dto.getSourceId() : 1)
            .sourceName(dto.getSourceName())
            .sourcetype(dto.getSourcetype())
            .rawData(dto.getRawData())
            .rawMessage(dto.getRawMessage())
            .severity(dto.getSeverity())
            .hostId(dto.getHostId())
            .batchId(dto.getBatchId())
            .elasticsearchId(dto.getElasticsearchId())
            .isIndexed(dto.getIsIndexed() != null ? dto.getIsIndexed() : false)
            .isEnriched(dto.getIsEnriched() != null ? dto.getIsEnriched() : false)
            .forwarderId(dto.getForwarderId())
            .version(dto.getVersion() != null ? dto.getVersion() : 1)
            .build();
    }

    private Long generateEventId() {
        long timestamp = System.currentTimeMillis() << 20;
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 1048576);
        long generatedId = timestamp | randomPart;
        log.debug("Generated event ID with timestamp={}, randomPart={}, result={}", timestamp, randomPart, generatedId);
        return generatedId;
    }
}
