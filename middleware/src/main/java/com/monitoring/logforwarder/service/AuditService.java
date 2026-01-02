package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.AuditLogDTO;
import com.monitoring.logforwarder.entity.AuditLog;
import com.monitoring.logforwarder.repository.postgresql.AuditLogRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Transactional(value = "postgresqlTransactionManager")
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public CompletableFuture<Void> logAction(Long userId, String username, String action, String resourceType, 
                         String resourceId, String status, String ipAddress, String userAgent) {
        return AsyncHelper.executeAsyncFuture(() -> {
            AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .status(status)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log created for user {} action {}", username, action);
            return null;
        });
    }

    public CompletableFuture<Void> logActionWithChanges(Long userId, String username, String action, String resourceType,
                                    String resourceId, String oldValue, String newValue, 
                                    String ipAddress, String userAgent) {
        return AsyncHelper.executeAsyncFuture(() -> {
            AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .oldValue(oldValue)
                .newValue(newValue)
                .status("SUCCESS")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .build();

            auditLogRepository.save(auditLog);
            return null;
        });
    }

    public CompletableFuture<Page<AuditLogDTO>> getAuditLogs(Long userId, String action, Integer page) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Pageable pageable = PageRequest.of(page != null ? page : 0, 50);
            
            if (userId != null && action != null) {
                return auditLogRepository.findByUserIdAndActionContaining(userId, action, pageable)
                    .map(this::entityToDto);
            } else if (userId != null) {
                return auditLogRepository.findByUserId(userId, pageable)
                    .map(this::entityToDto);
            } else {
                return auditLogRepository.findAll(pageable)
                    .map(this::entityToDto);
            }
        });
    }

    public CompletableFuture<Page<AuditLogDTO>> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Integer page) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Pageable pageable = PageRequest.of(page != null ? page : 0, 50);
            return auditLogRepository.findByDateRange(startDate, endDate, pageable)
                .map(this::entityToDto);
        });
    }

    public CompletableFuture<Void> processAuditLogFromKafka(AuditLog auditLog) {
        return AsyncHelper.executeAsyncFuture(() -> {
            auditLogRepository.save(auditLog);
            log.debug("Processed audit log from Kafka: {}", auditLog.getId());
            return null;
        });
    }

    private AuditLogDTO entityToDto(AuditLog auditLog) {
        return AuditLogDTO.builder()
            .id(auditLog.getId())
            .userId(auditLog.getUserId())
            .username(auditLog.getUsername())
            .action(auditLog.getAction())
            .resourceType(auditLog.getResourceType())
            .resourceId(auditLog.getResourceId())
            .status(auditLog.getStatus())
            .oldValue(auditLog.getOldValue())
            .newValue(auditLog.getNewValue())
            .ipAddress(auditLog.getIpAddress())
            .userAgent(auditLog.getUserAgent())
            .timestamp(auditLog.getCreatedAt())
            .build();
    }
}
