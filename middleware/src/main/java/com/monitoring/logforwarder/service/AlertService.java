package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.entity.Alert;
import com.monitoring.logforwarder.exception.ResourceNotFoundException;
import com.monitoring.logforwarder.repository.postgresql.AlertRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for alert management operations.
 *
 * <p><b>Purpose:</b> Handles alert lifecycle including triggering, acknowledgment,
 * resolution, and notification delivery.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
@Transactional(value = "postgresqlTransactionManager")
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private NotificationService notificationService;

    public CompletableFuture<AlertDTO> triggerAlert(AlertDTO alertDTO) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Alert alert = Alert.builder()
                .ruleId(alertDTO.getAlertRuleId())
                .status("TRIGGERED")
                .severity(alertDTO.getSeverity())
                .triggeredAt(LocalDateTime.now())
                .triggerMessage(alertDTO.getTriggerMessage())
                .notificationChannel(alertDTO.getNotificationChannel())
                .createdAt(LocalDateTime.now())
                .build();

            Alert saved = alertRepository.save(alert);
            
            notificationService.sendNotification(alertDTO.getNotificationChannel(), saved.getId())
                .exceptionally(ex -> {
                    log.warn("Notification sending failed for alert {}: {}", saved.getId(), ex.getMessage());
                    return null;
                });
            
            return entityToDto(saved);
        });
    }

    public CompletableFuture<AlertDTO> acknowledgeAlert(Long alertId, String acknowledgedBy) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", alertId));
            
            alert.setStatus("ACKNOWLEDGED");
            alert.setAcknowledgedAt(LocalDateTime.now());
            alert.setAcknowledgedBy(acknowledgedBy);
            alert.setUpdatedAt(LocalDateTime.now());
            
            Alert updated = alertRepository.save(alert);
            return entityToDto(updated);
        });
    }

    public CompletableFuture<AlertDTO> resolveAlert(Long alertId, String resolutionMessage) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", alertId));
            
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(LocalDateTime.now());
            alert.setResolutionMessage(resolutionMessage);
            alert.setUpdatedAt(LocalDateTime.now());
            
            Alert updated = alertRepository.save(alert);
            return entityToDto(updated);
        });
    }

    @Transactional(value = "postgresqlTransactionManager", readOnly = true)
    public CompletableFuture<Page<AlertDTO>> getAlerts(String status, String severity, Integer page) {
        return AsyncHelper.executeAsyncFuture(() -> {
            Pageable pageable = PageRequest.of(page != null ? page : 0, 50);
            Page<Alert> alerts;
            if (status != null && severity != null) {
                alerts = alertRepository.findByStatusAndSeverity(status, severity, pageable);
            } else if (status != null) {
                alerts = alertRepository.findByStatus(status, pageable);
            } else if (severity != null) {
                alerts = alertRepository.findBySeverity(severity, pageable);
            } else {
                alerts = alertRepository.findAll(pageable);
            }
            return alerts.map(this::entityToDto);
        });
    }

    @Transactional(value = "postgresqlTransactionManager", readOnly = true)
    public CompletableFuture<Long> getUnacknowledgedAlertCount() {
        return AsyncHelper.executeAsyncFuture(() -> alertRepository.countActiveAlerts());
    }

    public CompletableFuture<Void> processAlertFromKafka(AlertDTO alertDTO) {
        return triggerAlert(alertDTO).thenApply(__ -> null);
    }

    private AlertDTO entityToDto(Alert alert) {
        return AlertDTO.builder()
            .id(alert.getId())
            .alertRuleId(alert.getRuleId())
            .status(alert.getStatus())
            .severity(alert.getSeverity())
            .triggeredAt(alert.getTriggeredAt())
            .resolvedAt(alert.getResolvedAt())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .triggerMessage(alert.getTriggerMessage())
            .resolutionMessage(alert.getResolutionMessage())
            .notificationChannel(alert.getNotificationChannel())
            .createdAt(alert.getCreatedAt())
            .updatedAt(alert.getUpdatedAt())
            .build();
    }
}
