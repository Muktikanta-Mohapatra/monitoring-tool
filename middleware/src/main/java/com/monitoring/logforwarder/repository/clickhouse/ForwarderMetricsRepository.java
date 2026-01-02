package com.monitoring.logforwarder.repository.clickhouse;

import com.monitoring.logforwarder.entity.ForwarderMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ForwarderMetricsRepository {

    private final AsyncClickHouseTemplate asyncTemplate;

    private static final RowMapper<ForwarderMetrics> METRICS_ROW_MAPPER = new RowMapper<ForwarderMetrics>() {
        @Override
        public ForwarderMetrics mapRow(ResultSet rs, int rowNum) throws SQLException {
            ForwarderMetrics metrics = new ForwarderMetrics();
            metrics.setId(rs.getLong("id"));
            metrics.setForwarderId(rs.getLong("forwarder_id"));
            metrics.setCollectedAt(rs.getObject("collected_at", LocalDateTime.class));
            metrics.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            metrics.setCpuUsage(rs.getDouble("cpu_usage"));
            metrics.setMemoryUsage(rs.getDouble("memory_usage"));
            metrics.setNetworkInRate(rs.getLong("network_in_rate"));
            metrics.setNetworkOutRate(rs.getLong("network_out_rate"));
            metrics.setDiskUsagePercent(rs.getDouble("disk_usage_percent"));
            return metrics;
        }
    };

    public CompletableFuture<List<ForwarderMetrics>> findByForwarderId(Long forwarderId) {
        String sql = "SELECT * FROM forwarder_metrics WHERE forwarder_id = ?";
        return asyncTemplate.query(sql, new Object[]{forwarderId}, METRICS_ROW_MAPPER);
    }

    public CompletableFuture<List<ForwarderMetrics>> findByForwarderIdAndDateRange(Long forwarderId, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT * FROM forwarder_metrics WHERE forwarder_id = ? AND collected_at BETWEEN ? AND ?";
        return asyncTemplate.query(sql, new Object[]{forwarderId, start, end}, METRICS_ROW_MAPPER);
    }

    public CompletableFuture<Integer> deleteByCreatedAtBefore(LocalDateTime cutoffTime) {
        String sql = "DELETE FROM forwarder_metrics WHERE created_at < ?";
        return asyncTemplate.update(sql, cutoffTime);
    }
}
