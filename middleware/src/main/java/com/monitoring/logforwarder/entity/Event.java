package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a log event.
 *
 * <p><b>Purpose:</b> Persists log events with raw data, parsed fields, enrichment data,
 * and indexing metadata. Primary storage for ingested log data.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: events</li>
 *   <li>Indexed on timestamp+source, sourcetype, severity, host, batch_id</li>
 *   <li>Supports Elasticsearch dual-write via elasticsearchId</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_timestamp_source", columnList = "timestamp DESC, source_id"),
    @Index(name = "idx_sourcetype_timestamp", columnList = "sourcetype, timestamp DESC"),
    @Index(name = "idx_severity_timestamp", columnList = "severity, timestamp DESC"),
    @Index(name = "idx_host_timestamp", columnList = "host_id, timestamp DESC"),
    @Index(name = "idx_batch_id", columnList = "batch_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    @Column(name = "source_name", length = 256)
    private String sourceName;

    @Column(name = "sourcetype", length = 64)
    private String sourcetype;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "host_id")
    private Integer hostId;

    @Column(name = "index_id")
    private Short indexId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "parsed_fields", columnDefinition = "TEXT")
    private String parsedFields;

    @Column(name = "enriched_fields", columnDefinition = "TEXT")
    private String enrichedFields;

    @Column(name = "indexed_fields", columnDefinition = "TEXT")
    private String indexedFields;

    @Column(name = "parse_duration_us")
    private Long parseDurationUs;

    @Column(name = "detected_format", length = 64)
    private String detectedFormat;

    @Column(name = "elasticsearch_id", length = 100)
    private String elasticsearchId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_indexed")
    private Boolean isIndexed = false;

    @Column(name = "is_enriched")
    private Boolean isEnriched = false;

    @Column(name = "forwarder_id")
    private String forwarderId;

    @Column(name = "_version")
    private Short version = 1;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = generateId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (version == null) {
            version = 1;
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long randomPart = ThreadLocalRandom.current().nextLong(0, 1048576);
        return timestamp | randomPart;
    }
}
