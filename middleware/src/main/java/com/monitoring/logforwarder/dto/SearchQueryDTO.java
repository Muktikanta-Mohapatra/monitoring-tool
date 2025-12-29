package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for event search queries.
 *
 * <p><b>Purpose:</b> Defines search parameters including query string, time range,
 * filters, pagination, and sorting options.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchQueryDTO {
    private String query;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sourcetype;
    private String severity;
    private String host;
    private Integer page;
    private Integer pageSize;
    private String sortBy;
    private String sortOrder;
    private Boolean highlight;
    private Integer limit;
}
