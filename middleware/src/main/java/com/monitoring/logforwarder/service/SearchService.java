package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.SearchQueryDTO;
import com.monitoring.logforwarder.dto.SearchResultDTO;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.repository.clickhouse.ClickHouseEventRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchService {

    @Autowired
    private ClickHouseEventRepository clickHouseEventRepository;

    @Cacheable(value = "searchResults", key = "#query.query + '-' + #query.page")
    public CompletableFuture<SearchResultDTO> search(SearchQueryDTO query) {
        long startTime = System.currentTimeMillis();
        
        Pageable pageable = PageRequest.of(
            query.getPage() != null ? query.getPage() : 0,
            query.getPageSize() != null ? query.getPageSize() : 50
        );

        CompletableFuture<Page<EventDTO>> eventsFuture = buildAndExecuteQueryAsync(query, pageable);
        CompletableFuture<Map<String, Object>> aggregationsFuture = buildAggregationsAsync(query);
        CompletableFuture<Map<String, java.util.List<String>>> facetsFuture = buildFacetsAsync();

        return CompletableFuture.allOf(eventsFuture, aggregationsFuture, facetsFuture)
            .thenApply(v -> {
                Page<EventDTO> events = eventsFuture.join();
                Map<String, Object> aggregations = aggregationsFuture.join();
                Map<String, java.util.List<String>> facets = facetsFuture.join();
                long executionTime = System.currentTimeMillis() - startTime;

                return SearchResultDTO.builder()
                    .events(events.getContent())
                    .totalCount(events.getTotalElements())
                    .page(events.getNumber())
                    .pageSize(events.getSize())
                    .totalPages(events.getTotalPages())
                    .executionTimeMs(executionTime)
                    .aggregations(aggregations)
                    .facets(facets)
                    .build();
            });
    }

    private CompletableFuture<Page<EventDTO>> buildAndExecuteQueryAsync(SearchQueryDTO query, Pageable pageable) {
        return clickHouseEventRepository.findByTimestampBetween(query.getStartTime(), query.getEndTime())
            .thenApply(allEvents -> {
                java.util.List<com.monitoring.logforwarder.entity.Event> filtered = allEvents.stream()
                    .filter(e -> e.getTimestamp() != null && 
                           e.getTimestamp().isAfter(query.getStartTime()) && 
                           e.getTimestamp().isBefore(query.getEndTime()))
                    .filter(e -> query.getSourcetype() == null || e.getSourcetype().equals(query.getSourcetype()))
                    .filter(e -> query.getSeverity() == null || e.getSeverity().equals(query.getSeverity()))
                    .skip((long) pageable.getPageNumber() * pageable.getPageSize())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());
                
                int total = (int) allEvents.stream()
                    .filter(e -> e.getTimestamp() != null && 
                           e.getTimestamp().isAfter(query.getStartTime()) && 
                           e.getTimestamp().isBefore(query.getEndTime()))
                    .filter(e -> query.getSourcetype() == null || e.getSourcetype().equals(query.getSourcetype()))
                    .filter(e -> query.getSeverity() == null || e.getSeverity().equals(query.getSeverity()))
                    .count();
                
                return (Page<EventDTO>) new org.springframework.data.domain.PageImpl<>(
                    filtered.stream().map(this::entityToDto).collect(Collectors.toList()),
                    pageable,
                    total
                );
            })
            .exceptionally(ex -> {
                log.error("Error building and executing search query", ex);
                return new org.springframework.data.domain.PageImpl<>(
                    java.util.Collections.emptyList(),
                    pageable,
                    0
                );
            });
    }

    private CompletableFuture<Map<String, Object>> buildAggregationsAsync(SearchQueryDTO query) {
        return clickHouseEventRepository.countEventsByTimeRange(query.getStartTime(), query.getEndTime())
            .thenApply(count -> {
                Map<String, Object> aggregations = new HashMap<>();
                aggregations.put("total_events", count);
                return aggregations;
            })
            .exceptionally(ex -> {
                log.error("Error building aggregations: {}", ex.getMessage());
                Map<String, Object> aggregations = new HashMap<>();
                aggregations.put("total_events", 0L);
                return aggregations;
            });
    }

    private CompletableFuture<Map<String, java.util.List<String>>> buildFacetsAsync() {
        CompletableFuture<java.util.List<String>> sourcetypesFuture = clickHouseEventRepository.findDistinctSourcetypes()
            .exceptionally(ex -> {
                log.error("Error retrieving distinct sourcetypes: {}", ex.getMessage());
                return java.util.Collections.emptyList();
            });
        
        CompletableFuture<java.util.List<String>> severitiesFuture = clickHouseEventRepository.findDistinctSeverities()
            .exceptionally(ex -> {
                log.error("Error retrieving distinct severities: {}", ex.getMessage());
                return java.util.Collections.emptyList();
            });

        return CompletableFuture.allOf(sourcetypesFuture, severitiesFuture)
            .thenApply(v -> {
                Map<String, java.util.List<String>> facets = new HashMap<>();
                facets.put("sourcetype", sourcetypesFuture.join());
                facets.put("severity", severitiesFuture.join());
                return facets;
            });
    }

    private EventDTO entityToDto(com.monitoring.logforwarder.entity.Event event) {
        return EventDTO.builder()
            .id(event.getId())
            .timestamp(event.getTimestamp() != null ? event.getTimestamp().atOffset(ZoneOffset.UTC) : null)
            .sourceId(event.getSourceId())
            .sourceName(event.getSourceName())
            .sourcetype(event.getSourcetype())
            .rawData(event.getRawData())
            .severity(event.getSeverity())
            .createdAt(event.getCreatedAt())
            .build();
    }
}
