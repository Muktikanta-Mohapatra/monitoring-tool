package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.ForwarderDTO;
import com.monitoring.logforwarder.dto.ForwarderMetricsDTO;
import com.monitoring.logforwarder.entity.Forwarder;
import com.monitoring.logforwarder.entity.ForwarderApiKey;
import com.monitoring.logforwarder.exception.ResourceNotFoundException;
import com.monitoring.logforwarder.kafka.EventProducer;
import com.monitoring.logforwarder.repository.postgresql.ForwarderApiKeyRepository;
import com.monitoring.logforwarder.repository.clickhouse.ForwarderRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import com.monitoring.logforwarder.util.ClientInfoExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for log forwarder management operations.
 *
 * <p><b>Purpose:</b> Handles forwarder registration, status updates, heartbeat
 * processing, and metrics tracking.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Caching for forwarder metrics</li>
 *   <li>Transaction management for data consistency</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Service
public class ForwarderService {

    @Autowired
    private ForwarderRepository forwarderRepository;

    @Autowired
    private ForwarderApiKeyRepository forwarderApiKeyRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventProducer eventProducer;

    public CompletableFuture<List<ForwarderDTO>> getAllForwarders() {
        return forwarderRepository.findActiveForwarders()
            .thenApply(forwarders -> forwarders.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList())
            );
    }

    @Cacheable(value = "forwarderMetrics", key = "#forwarderId")
    public CompletableFuture<ForwarderDTO> getForwarderById(String forwarderId) {
        return forwarderRepository.findByForwarderId(forwarderId)
            .thenApply(forwarderOptional -> {
                if (forwarderOptional == null) {
                    throw new ResourceNotFoundException("Forwarder", "forwarderId", forwarderId);
                }
                return entityToDto(forwarderOptional);
            });
    }

    @CacheEvict(value = "forwarderMetrics", allEntries = true)
    public CompletableFuture<ForwarderDTO> registerForwarder(ForwarderDTO forwarderDTO) {
        return CompletableFuture.supplyAsync(() -> {
            Forwarder forwarder = Forwarder.builder()
                .forwarderId(forwarderDTO.getId())
                .name(forwarderDTO.getName())
                .hostname(forwarderDTO.getHostname())
                .ip(forwarderDTO.getIpAddress())
                .version(forwarderDTO.getVersion())
                .status("ACTIVE")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

            log.info("Registering forwarder: {}", forwarderDTO.getId());
            
            auditService.logAction(
                null,
                forwarderDTO.getId(),
                "FORWARDER_REGISTERED",
                "FORWARDER",
                forwarderDTO.getId(),
                "SUCCESS",
                ClientInfoExtractor.getClientIpFromContext(),
                ClientInfoExtractor.getUserAgentFromContext()
            ).exceptionally(ex -> {
                log.warn("Audit logging failed for forwarder registration: {}", ex.getMessage());
                return null;
            });

            return entityToDto(forwarder);
        });
    }

    @CacheEvict(value = "forwarderMetrics", allEntries = true)
    public CompletableFuture<Void> updateForwarderStatus(String forwarderId, String status) {
        return forwarderRepository.findByForwarderId(forwarderId)
            .thenApplyAsync(forwarderOptional -> {
                if (forwarderOptional == null) {
                    throw new ResourceNotFoundException("Forwarder", "forwarderId", forwarderId);
                }
                
                Forwarder forwarder = forwarderOptional;
                String oldStatus = forwarder.getStatus();
                log.info("Updating forwarder {} status from {} to {}", forwarderId, oldStatus, status);

                auditService.logActionWithChanges(
                    null,
                    forwarderId,
                    "STATUS_UPDATED",
                    "FORWARDER",
                    forwarderId,
                    oldStatus,
                    status,
                    ClientInfoExtractor.getClientIpFromContext(),
                    ClientInfoExtractor.getUserAgentFromContext()
                ).exceptionally(ex -> {
                    log.warn("Audit logging failed for forwarder status update: {}", ex.getMessage());
                    return null;
                });
                return null;
            });
    }

    @CacheEvict(value = "forwarderMetrics", allEntries = true)
    public CompletableFuture<Void> updateForwarderMetrics(String forwarderId, Long eventsProcessedDelta) {
        return forwarderRepository.findByForwarderId(forwarderId)
            .thenApplyAsync(forwarderOptional -> {
                if (forwarderOptional != null) {
                    Forwarder forwarder = forwarderOptional;
                    Long oldValue = forwarder.getEventsProcessed() != null ? forwarder.getEventsProcessed() : 0L;
                    forwarder.setEventsProcessed(oldValue + eventsProcessedDelta);
                    forwarder.setUpdatedAt(LocalDateTime.now());
                    
                    log.debug("Updated metrics for forwarder {}: events processed = {}", 
                        forwarderId, forwarder.getEventsProcessed());

                    ForwarderMetricsDTO metricsDTO = ForwarderMetricsDTO.builder()
                            .forwarderId(forwarderId)
                            .eventsProcessed(forwarder.getEventsProcessed())
                            .cpuUsagePercent(forwarder.getCpuUsagePercent())
                            .queueDepth(forwarder.getQueueDepth())
                            .timestamp(LocalDateTime.now())
                            .build();

                    eventProducer.publishMetrics(metricsDTO);
                }
                return null;
            });
    }

    public CompletableFuture<List<ForwarderDTO>> getActiveForwarders() {
        return forwarderRepository.findActiveForwarders()
            .thenApply(forwarders -> forwarders.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList())
            );
    }

    public CompletableFuture<Long> getTotalEventsProcessed(String forwarderId) {
        return forwarderRepository.findByForwarderId(forwarderId)
            .thenApply(forwarderOptional -> {
                if (forwarderOptional != null) {
                    return forwarderOptional.getEventsProcessed() != null ? forwarderOptional.getEventsProcessed() : 0L;
                }
                return 0L;
            });
    }

    @Transactional(value = "postgresqlTransactionManager")
    public CompletableFuture<Void> updateApiKeyLastUsed(ForwarderApiKey apiKey) {
        return AsyncHelper.executeAsyncFuture(() -> {
            apiKey.setLastUsedAt(LocalDateTime.now());
            forwarderApiKeyRepository.save(apiKey);
            return null;
        });
    }

    private ForwarderDTO entityToDto(Forwarder forwarder) {
        return ForwarderDTO.builder()
            .id(forwarder.getForwarderId())
            .name(forwarder.getName())
            .hostname(forwarder.getHostname())
            .ipAddress(forwarder.getIp())
            .version(forwarder.getVersion())
            .status(forwarder.getStatus())
            .lastHeartbeat(forwarder.getLastHeartbeat())
            .createdAt(forwarder.getCreatedAt())
            .updatedAt(forwarder.getUpdatedAt())
            .totalEventsProcessed(forwarder.getEventsProcessed())
            .cpuUsagePercent(forwarder.getCpuUsagePercent())
            .memoryUsagePercent(forwarder.getMemoryUsagePercent())
            .queueDepth(forwarder.getQueueDepth())
            .enabled(forwarder.getEnabled())
            .build();
    }

}
