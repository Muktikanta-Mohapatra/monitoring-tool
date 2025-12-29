package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for search query results.
 *
 * <p><b>Purpose:</b> Contains search results including matching events,
 * pagination information, execution metrics, and aggregations.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<EventDTO> events;
    private Long totalCount;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
    private Long executionTimeMs;
    private Map<String, Object> aggregations;
    private Map<String, List<String>> facets;
}
