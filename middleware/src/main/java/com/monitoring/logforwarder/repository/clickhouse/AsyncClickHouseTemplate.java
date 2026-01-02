package com.monitoring.logforwarder.repository.clickhouse;

import com.monitoring.logforwarder.util.VirtualThreadExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncClickHouseTemplate {

    private final JdbcTemplate jdbcTemplate;

    private final ExecutorService virtualThreadExecutor = VirtualThreadExecutor.getExecutor();

    public <T> CompletableFuture<List<T>> query(String sql, RowMapper<T> rowMapper) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse query: {}", sql);
                    return jdbcTemplate.query(sql, rowMapper);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse query: {}", sql, e);
                    throw new RuntimeException("ClickHouse query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public <T> CompletableFuture<List<T>> query(String sql, Object[] args, RowMapper<T> rowMapper) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse query with args: {}", sql);
                    return jdbcTemplate.query(sql, args, rowMapper);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse query: {} with args: {}", sql, args, e);
                    throw new RuntimeException("ClickHouse query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public <T> CompletableFuture<T> queryForObject(String sql, RowMapper<T> rowMapper) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse single object query: {}", sql);
                    List<T> results = jdbcTemplate.query(sql, rowMapper);
                    return results.isEmpty() ? null : results.get(0);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse query: {}", sql, e);
                    throw new RuntimeException("ClickHouse query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public <T> CompletableFuture<T> queryForObject(String sql, Object[] args, RowMapper<T> rowMapper) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse single object query with args: {}", sql);
                    List<T> results = jdbcTemplate.query(sql, args, rowMapper);
                    return results.isEmpty() ? null : results.get(0);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse query: {} with args: {}", sql, args, e);
                    throw new RuntimeException("ClickHouse query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public CompletableFuture<Integer> update(String sql) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse update: {}", sql);
                    return jdbcTemplate.update(sql);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse update: {}", sql, e);
                    throw new RuntimeException("ClickHouse update failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public CompletableFuture<Integer> update(String sql, Object... args) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse update with args: {}", sql);
                    return jdbcTemplate.update(sql, args);
                } catch (Exception e) {
                    log.error("Error executing ClickHouse update: {} with args: {}", sql, args, e);
                    throw new RuntimeException("ClickHouse update failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public CompletableFuture<Long> queryForLong(String sql) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse count query: {}", sql);
                    Long result = jdbcTemplate.queryForObject(sql, Long.class);
                    return result != null ? result : 0L;
                } catch (Exception e) {
                    log.error("Error executing ClickHouse count query: {}", sql, e);
                    throw new RuntimeException("ClickHouse count query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }

    public CompletableFuture<Long> queryForLong(String sql, Object... args) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    log.debug("Executing async ClickHouse count query with args: {}", sql);
                    Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
                    return result != null ? result : 0L;
                } catch (Exception e) {
                    log.error("Error executing ClickHouse count query: {} with args: {}", sql, args, e);
                    throw new RuntimeException("ClickHouse count query failed: " + sql, e);
                }
            },
            virtualThreadExecutor
        );
    }
}
