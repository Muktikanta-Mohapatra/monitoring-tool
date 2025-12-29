package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserId(Long userId);

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    List<AuditLog> findByUserIdAndActionContaining(Long userId, String action);

    Page<AuditLog> findByUserIdAndActionContaining(Long userId, String action, Pageable pageable);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findByResourceType(String resourceType);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end")
    List<AuditLog> findByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end")
    Page<AuditLog> findByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end")
    List<AuditLog> findUserActivityByDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT a FROM AuditLog a WHERE a.status = 'FAILURE'")
    List<AuditLog> findFailedActions();

    @Query("SELECT a FROM AuditLog a WHERE a.status = 'FAILURE' AND a.createdAt BETWEEN :start AND :end")
    List<AuditLog> findFailedActionsByDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action")
    Long countByAction(@Param("action") String action);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.status = 'FAILURE'")
    Long countFailures();

    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffTime")
    int deleteByCreatedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
