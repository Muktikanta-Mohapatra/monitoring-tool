package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA repository for Alert entity operations in PostgreSQL.
 *
 * <p><b>Purpose:</b> Provides CRUD and query operations for alerts including
 * filtering by status, severity, rule, and time range.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByStatus(String status);

    Page<Alert> findByStatus(String status, Pageable pageable);

    List<Alert> findBySeverity(String severity);

    Page<Alert> findBySeverity(String severity, Pageable pageable);

    List<Alert> findByStatusAndSeverity(String status, String severity);

    Page<Alert> findByStatusAndSeverity(String status, String severity, Pageable pageable);

    List<Alert> findByRuleId(Long ruleId);

    @Query("SELECT a FROM Alert a WHERE a.status = 'ACTIVE'")
    List<Alert> findActiveAlerts();

    @Query("SELECT a FROM Alert a WHERE a.status = 'RESOLVED'")
    List<Alert> findResolvedAlerts();

    @Query("SELECT a FROM Alert a WHERE a.triggeredAt BETWEEN :start AND :end")
    List<Alert> findAlertsByTimeRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM Alert a WHERE a.severity = :severity AND a.status = 'ACTIVE'")
    List<Alert> findActiveAlertsBySeverity(@Param("severity") String severity);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'ACTIVE'")
    Long countActiveAlerts();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :severity AND a.status = 'ACTIVE'")
    Long countActiveAlertsBySeverity(@Param("severity") String severity);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.triggeredAt BETWEEN :start AND :end")
    Long countAlertsByTimeRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM Alert a WHERE a.acknowledgedAt IS NULL AND a.status = 'ACTIVE'")
    List<Alert> findUnacknowledgedAlerts();

    List<Alert> findByNotificationSentFalse();
}
