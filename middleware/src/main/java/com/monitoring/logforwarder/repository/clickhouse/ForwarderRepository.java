package com.monitoring.logforwarder.repository.clickhouse;

import com.monitoring.logforwarder.entity.Forwarder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Repository
public class ForwarderRepository {

    @Autowired
    private AsyncClickHouseTemplate asyncTemplate;

    private static final RowMapper<Forwarder> FORWARDER_ROW_MAPPER = new RowMapper<Forwarder>() {
        @Override
        public Forwarder mapRow(ResultSet rs, int rowNum) throws SQLException {
            Forwarder forwarder = new Forwarder();
            forwarder.setId(rs.getLong("id"));
            forwarder.setForwarderId(rs.getString("forwarder_id"));
            forwarder.setApiKey(rs.getString("api_key"));
            forwarder.setStatus(rs.getString("status"));
            forwarder.setLastHeartbeat(rs.getObject("last_heartbeat", LocalDateTime.class));
            forwarder.setEventsProcessed(rs.getLong("events_processed"));
            forwarder.setEventsDropped(rs.getLong("events_dropped"));
            forwarder.setCpuUsagePercent(rs.getDouble("cpu_usage_percent"));
            forwarder.setMemoryUsagePercent(rs.getDouble("memory_usage_percent"));
            forwarder.setHealthStatus(rs.getString("health_status"));
            return forwarder;
        }
    };

    public CompletableFuture<Forwarder> findByForwarderId(String forwarderId) {
        String sql = "SELECT * FROM forwarders WHERE forwarder_id = ? LIMIT 1";
        return asyncTemplate.queryForObject(sql, new Object[]{forwarderId}, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<Forwarder> findByApiKey(String apiKey) {
        String sql = "SELECT * FROM forwarders WHERE api_key = ? LIMIT 1";
        return asyncTemplate.queryForObject(sql, new Object[]{apiKey}, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<List<Forwarder>> findByStatus(String status) {
        String sql = "SELECT * FROM forwarders WHERE status = ?";
        return asyncTemplate.query(sql, new Object[]{status}, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<List<Forwarder>> findStaleForwarders(LocalDateTime cutoffTime) {
        String sql = "SELECT * FROM forwarders WHERE last_heartbeat < ?";
        return asyncTemplate.query(sql, new Object[]{cutoffTime}, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<Long> countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM forwarders WHERE status = ?";
        return asyncTemplate.queryForLong(sql, status);
    }

    public CompletableFuture<List<Forwarder>> findActiveForwarders() {
        String sql = "SELECT * FROM forwarders WHERE status IN ('ACTIVE', 'WARNING')";
        return asyncTemplate.query(sql, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<List<Forwarder>> findOfflineForwarders() {
        String sql = "SELECT * FROM forwarders WHERE status = 'offline'";
        return asyncTemplate.query(sql, FORWARDER_ROW_MAPPER);
    }

    public CompletableFuture<Long> getTotalEventsProcessed() {
        String sql = "SELECT SUM(events_processed) FROM forwarders";
        return asyncTemplate.queryForLong(sql);
    }

    public CompletableFuture<Long> getTotalEventsDropped() {
        String sql = "SELECT SUM(events_dropped) FROM forwarders";
        return asyncTemplate.queryForLong(sql);
    }

    public CompletableFuture<Double> getAverageCpuUsage() {
        String sql = "SELECT AVG(cpu_usage_percent) FROM forwarders WHERE status = 'online'";
        return asyncTemplate.queryForLong(sql)
            .thenApply(value -> value != null ? value.doubleValue() : 0.0)
            .exceptionally(ex -> {
                log.error("Error getting average CPU usage", ex);
                return 0.0;
            });
    }

    public CompletableFuture<Double> getAverageMemoryUsage() {
        String sql = "SELECT AVG(memory_usage_percent) FROM forwarders WHERE status = 'online'";
        return asyncTemplate.queryForLong(sql)
            .thenApply(value -> value != null ? value.doubleValue() : 0.0)
            .exceptionally(ex -> {
                log.error("Error getting average memory usage", ex);
                return 0.0;
            });
    }

    public CompletableFuture<List<Forwarder>> findByHealthStatusNotLike(String status) {
        String sql = "SELECT * FROM forwarders WHERE health_status != ?";
        return asyncTemplate.query(sql, new Object[]{status}, FORWARDER_ROW_MAPPER);
    }
}
