package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Data Transfer Object for log event data.
 *
 * <p><b>Purpose:</b> Represents a single log event with raw data, parsed fields,
 * enrichment data, and indexing metadata. Used for event ingestion, storage,
 * and retrieval throughout the system.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private OffsetDateTime timestamp;
    private java.time.Instant timestampInstant;
    private Long timestampNanos;
    private Integer sourceId;
    private String sourceName;
    private String sourcetype;
    private String rawData;
    private String rawMessage;
    private String severity;
    private Integer hostId;
    private Short indexId;
    private Long batchId;
    private Map<String, Object> parsedFields;
    private Map<String, Object> enrichedFields;
    private Map<String, Object> indexedFields;
    private Long parseDurationUs;
    private String detectedFormat;
    private String elasticsearchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isIndexed;
    private Boolean isEnriched;
    private String forwarderId;
    private Short version;
}
