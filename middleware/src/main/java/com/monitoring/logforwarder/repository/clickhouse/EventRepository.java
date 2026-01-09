package com.monitoring.logforwarder.repository.clickhouse;

import com.monitoring.logforwarder.entity.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for event CRUD operations in ClickHouse.
 *
 * <p><b>Purpose:</b> Provides async query and persistence operations for log events
 * stored in ClickHouse time-series database.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EventRepository {

    private final AsyncClickHouseTemplate asyncTemplate;

    private static final RowMapper<Event> EVENT_ROW_MAPPER = new RowMapper<Event>() {
        @Override
        public Event mapRow(ResultSet rs, int rowNum) throws SQLException {
            Event event = new Event();
            event.setId(rs.getLong("id"));
            event.setTimestamp(rs.getObject("timestamp", LocalDateTime.class));
            event.setSourceId(rs.getInt("source_id"));
            event.setSourcetype(rs.getString("sourcetype"));
            event.setSeverity(rs.getString("severity"));
            event.setForwarderId(rs.getString("forwarder_id"));
            event.setBatchId(rs.getLong("batch_id"));
            event.setElasticsearchId(rs.getString("elasticsearch_id"));
            event.setIsIndexed(rs.getBoolean("is_indexed"));
            event.setRawMessage(rs.getString("raw_message"));
            return event;
        }
    };

    public CompletableFuture<List<Event>> findByTimestampBetween(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT * FROM events WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC";
        return asyncTemplate.query(sql, new Object[]{start, end}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findBySourceId(Integer sourceId) {
        String sql = "SELECT * FROM events WHERE source_id = ?";
        return asyncTemplate.query(sql, new Object[]{sourceId}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findBySourcetype(String sourcetype) {
        String sql = "SELECT * FROM events WHERE sourcetype = ?";
        return asyncTemplate.query(sql, new Object[]{sourcetype}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findBySeverity(String severity) {
        String sql = "SELECT * FROM events WHERE severity = ?";
        return asyncTemplate.query(sql, new Object[]{severity}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findByForwarderId(String forwarderId) {
        String sql = "SELECT * FROM events WHERE forwarder_id = ?";
        return asyncTemplate.query(sql, new Object[]{forwarderId}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findEventsBySeverityAndTimeRange(LocalDateTime start, LocalDateTime end, String severity) {
        String sql = "SELECT * FROM events WHERE timestamp BETWEEN ? AND ? AND severity = ?";
        return asyncTemplate.query(sql, new Object[]{start, end, severity}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findEventsBySourceAndTimeRange(LocalDateTime start, LocalDateTime end, Integer sourceId) {
        String sql = "SELECT * FROM events WHERE timestamp BETWEEN ? AND ? AND source_id = ?";
        return asyncTemplate.query(sql, new Object[]{start, end, sourceId}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findEventsByMultipleCriteria(LocalDateTime start, LocalDateTime end, String sourcetype, String severity) {
        String sql = "SELECT * FROM events WHERE timestamp BETWEEN ? AND ? AND sourcetype = ? AND severity = ?";
        return asyncTemplate.query(sql, new Object[]{start, end, sourcetype, severity}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<Long> countEventsByTimeRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM events WHERE timestamp BETWEEN ? AND ?";
        return asyncTemplate.queryForLong(sql, start, end);
    }

    public CompletableFuture<Long> countEventsBySeverityAndTimeRange(String severity, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM events WHERE severity = ? AND timestamp BETWEEN ? AND ?";
        return asyncTemplate.queryForLong(sql, severity, start, end);
    }

    public CompletableFuture<List<Event>> findByBatchId(Long batchId) {
        String sql = "SELECT * FROM events WHERE batch_id = ?";
        return asyncTemplate.query(sql, new Object[]{batchId}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<Event> findByElasticsearchId(String elasticsearchId) {
        String sql = "SELECT * FROM events WHERE elasticsearch_id = ? LIMIT 1";
        return asyncTemplate.queryForObject(sql, new Object[]{elasticsearchId}, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<Event>> findByIsIndexedFalse() {
        String sql = "SELECT * FROM events WHERE is_indexed = false";
        return asyncTemplate.query(sql, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<List<String>> findDistinctSourcetypes() {
        String sql = "SELECT DISTINCT sourcetype FROM events";
        return asyncTemplate.query(sql, (rs, rowNum) -> rs.getString("sourcetype"));
    }

    public CompletableFuture<List<String>> findDistinctSeverities() {
        String sql = "SELECT DISTINCT severity FROM events";
        return asyncTemplate.query(sql, (rs, rowNum) -> rs.getString("severity"));
    }

    public CompletableFuture<Integer> deleteByTimestampBefore(LocalDateTime timestamp) {
        String sql = "DELETE FROM events WHERE timestamp < ?";
        return asyncTemplate.update(sql, timestamp);
    }

    public CompletableFuture<Integer> deleteFailedEventsByTimestampBefore(LocalDateTime timestamp) {
        String sql = "DELETE FROM events WHERE is_indexed = false AND timestamp < ?";
        return asyncTemplate.update(sql, timestamp);
    }

    public CompletableFuture<Event> findMostRecentEvent() {
        String sql = "SELECT * FROM events WHERE timestamp = (SELECT MAX(timestamp) FROM events) LIMIT 1";
        return asyncTemplate.queryForObject(sql, EVENT_ROW_MAPPER);
    }

    public CompletableFuture<Integer> insertEvent(Event event) {
        if (event == null) {
            log.error("Cannot insert null event to ClickHouse");
            return CompletableFuture.failedFuture(new IllegalArgumentException("Event cannot be null"));
        }
        
        String sql = "INSERT INTO events (timestamp, source_id, source_name, sourcetype, raw_message, severity, forwarder_id, batch_id, elasticsearch_id, is_indexed, is_enriched) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        log.debug("Inserting event to ClickHouse: id={}, timestamp={}, forwarderId={}, batch_id={}", 
            event.getId(), event.getTimestamp(), event.getForwarderId(), event.getBatchId());
        
        return asyncTemplate.update(sql,
            event.getTimestamp(),
            event.getSourceId(),
            event.getSourceName(),
            event.getSourcetype(),
            event.getRawMessage(),
            event.getSeverity(),
            event.getForwarderId(),
            event.getBatchId(),
            event.getElasticsearchId(),
            event.getIsIndexed(),
            event.getIsEnriched()
        )
        .thenApply(result -> {
            if (result != null && result > 0) {
                log.debug("Event inserted successfully: id={}, rows_affected={}", event.getId(), result);
            } else {
                log.error("Event insert returned no rows: id={}, result={}", event.getId(), result);
            }
            return result;
        })
        .exceptionally(ex -> {
            log.error("Failed to insert event to ClickHouse: id={}, error={}", event.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Insert failed for event: " + event.getId(), ex);
        });
    }

    public CompletableFuture<Integer> insertEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            log.debug("Empty event list received for batch insert");
            return CompletableFuture.completedFuture(0);
        }

        log.info("Starting batch insert of {} events to ClickHouse", events.size());
        
        return CompletableFuture.supplyAsync(() -> {
            int totalInserted = 0;
            int failedCount = 0;
            
            for (Event event : events) {
                try {
                    Integer result = insertEvent(event).get();
                    if (result != null && result > 0) {
                        totalInserted += result;
                    } else {
                        failedCount++;
                        log.warn("Event insert failed (0 rows): id={}, forwarderId={}", 
                            event.getId(), event.getForwarderId());
                    }
                } catch (Exception ex) {
                    failedCount++;
                    log.error("Exception during event insert: id={}, error={}", 
                        event.getId(), ex.getMessage());
                }
            }
            
            log.info("Batch insert completed: total_inserted={}, failed={}, from_batch={}", 
                totalInserted, failedCount, events.size());
            
            if (failedCount > 0) {
                log.warn("CRITICAL: {} events failed to insert in batch operation", failedCount);
            }
            
            return totalInserted;
        });
    }
}
