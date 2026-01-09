package com.monitoring.logforwarder.kafka;

import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.dto.ForwarderMetricsDTO;
import com.monitoring.logforwarder.entity.AuditLog;
import com.monitoring.logforwarder.service.EventService;
import com.monitoring.logforwarder.service.AlertService;
import com.monitoring.logforwarder.service.MetricsService;
import com.monitoring.logforwarder.service.AuditService;
import com.monitoring.logforwarder.service.NotificationService;
import com.monitoring.logforwarder.service.EventProcessingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer service that processes messages from all Kafka topics and triggers persistence/actions.
 *
 * <p><b>PURPOSE:</b></p>
 * This is the CONSUMPTION side of the Kafka messaging pipeline. It listens to multiple topics
 * and routes messages to appropriate service handlers for persistence and processing.
 * This decouples ingestion (fast, async ACK) from persistence (slower, durable).
 *
 * <p><b>ARCHITECTURE POSITION:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                    EventConsumer - KAFKA MESSAGE ROUTER                         │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  Kafka Topics                      EventConsumer (this class)                   │
 * │                                                                                 │
 * │  ┌──────────────┐                                                               │
 * │  │ "events"     │ ──▶ consumeEvent() ──▶ EventService.processEventFromKafkaAsync│
 * │  │ (10 threads) │                              │                                │
 * │  └──────────────┘                              ▼                                │
 * │                                         ClickHouse INSERT                       │
 * │                                                                                 │
 * │  ┌──────────────┐                                                               │
 * │  │ "alerts"     │ ──▶ consumeAlert() ──▶ AlertService.processAlertFromKafka     │
 * │  │ (5 threads)  │                              │                                │
 * │  └──────────────┘                              ▼                                │
 * │                                         PostgreSQL INSERT + Notify              │
 * │                                                                                 │
 * │  ┌──────────────┐                                                               │
 * │  │ "metrics"    │ ──▶ consumeMetrics() ──▶ MetricsService.processMetrics        │
 * │  │ (5 threads)  │                                                               │
 * │  └──────────────┘                                                               │
 * │                                                                                 │
 * │  ┌──────────────┐                                                               │
 * │  │ "audit-logs" │ ──▶ consumeAuditLog() ──▶ AuditService.processAuditLog        │
 * │  │ (3 threads)  │                                                               │
 * │  └──────────────┘                                                               │
 * │                                                                                 │
 * │  ┌──────────────┐                                                               │
 * │  │"notifications"│ ──▶ consumeNotification() ──▶ NotificationService.send       │
 * │  │ (3 threads)  │                                                               │
 * │  └──────────────┘                                                               │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>ACKNOWLEDGMENT STRATEGY:</b></p>
 * Uses MANUAL acknowledgment for at-least-once delivery guarantees:
 * <ol>
 *   <li>Message received from Kafka (offset NOT committed)</li>
 *   <li>Process message (e.g., insert to ClickHouse)</li>
 *   <li>On SUCCESS: {@code acknowledgment.acknowledge()} commits offset</li>
 *   <li>On FAILURE: Offset NOT committed, message will be redelivered</li>
 * </ol>
 *
 * <p><b>CONCURRENCY:</b></p>
 * <ul>
 *   <li><b>events:</b> 10 concurrent consumers (high throughput)</li>
 *   <li><b>alerts:</b> 5 concurrent consumers</li>
 *   <li><b>metrics:</b> 5 concurrent consumers</li>
 *   <li><b>audit-logs:</b> 3 concurrent consumers</li>
 *   <li><b>notifications:</b> 3 concurrent consumers</li>
 * </ul>
 *
 * <p><b>CONSUMER GROUP:</b></p>
 * All listeners use the same consumer group (configured via {@code spring.kafka.consumer.group-id}),
 * ensuring messages are load-balanced across instances and each message is processed exactly once
 * per consumer group.
 *
 * <p><b>PRODUCED BY:</b></p>
 * Messages in these topics are produced by {@link EventProducer}:
 * <ul>
 *   <li>{@link EventProducer#publishEvent} → "events" topic</li>
 *   <li>{@link EventProducer#publishAlert} → "alerts" topic</li>
 *   <li>{@link EventProducer#publishMetrics} → "metrics" topic</li>
 *   <li>{@link EventProducer#publishAuditLog} → "audit-logs" topic</li>
 *   <li>{@link EventProducer#publishNotification} → "notifications" topic</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventProducer
 * @see EventService#processEventFromKafkaAsync
 */
@Slf4j
@Service
public class EventConsumer {

    private final EventService eventService;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EventProcessingMetrics processingMetrics;

    public EventConsumer(EventService eventService,
                        AlertService alertService,
                        MetricsService metricsService,
                        AuditService auditService,
                        NotificationService notificationService,
                        EventProcessingMetrics processingMetrics) {
        this.eventService = eventService;
        this.alertService = alertService;
        this.metricsService = metricsService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.processingMetrics = processingMetrics;
    }

    @KafkaListener(topics = "events", groupId = "${spring.kafka.consumer.group-id}", concurrency = "10")
    public void consumeEvent(@Payload EventDTO event,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset,
                            Acknowledgment acknowledgment) {
        try {
            log.info("CRITICAL: Consuming event from Kafka - ID: {}, partition: {}, offset: {}", 
                event.getId(), partition, offset);
            
            eventService.processEventFromKafkaAsync(event)
                .thenAccept(v -> {
                    log.info("CRITICAL: Event processed successfully and will be persisted: {}", event.getId());
                    acknowledgment.acknowledge();
                    log.info("CRITICAL: Offset COMMITTED for event {} at partition {} offset {} - Event is SAFE", 
                        event.getId(), partition, offset);
                })
                .exceptionally(ex -> {
                    log.error("CRITICAL: Failed to process event {} from Kafka - Exception: {}", 
                        event.getId(), ex.getMessage(), ex);
                    processingMetrics.recordEventFailed();
                    log.error("CRITICAL: Offset NOT committed for event {} - message will be RETRIED", event.getId());
                    return null;
                })
                .join();
        } catch (Exception e) {
            log.error("CRITICAL: Exception in consumeEvent for event {} - {}", event.getId(), e.getMessage(), e);
            processingMetrics.recordEventFailed();
            log.error("CRITICAL: Offset NOT committed - message will be RETRIED");
        }
    }

    @KafkaListener(topics = "alerts", groupId = "${spring.kafka.consumer.group-id}", concurrency = "5")
    public void consumeAlert(@Payload AlertDTO alert,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset,
                            Acknowledgment acknowledgment) {
        try {
            log.debug("Consuming alert from partition: {} offset: {}", partition, offset);
            alertService.processAlertFromKafka(alert)
                .thenAccept(v -> {
                    log.info("Alert processed successfully: {}", alert.getId());
                    acknowledgment.acknowledge();
                    log.debug("Offset committed for alert: {} at partition: {} offset: {}", 
                        alert.getId(), partition, offset);
                })
                .exceptionally(ex -> {
                    log.error("Failed to process alert: {}", alert.getId(), ex);
                    return null;
                })
                .join();
        } catch (Exception e) {
            log.error("Failed to process alert: {}", alert.getId(), e);
        }
    }

    @KafkaListener(topics = "metrics", groupId = "${spring.kafka.consumer.group-id}", concurrency = "5")
    public void consumeMetrics(@Payload ForwarderMetricsDTO metrics,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment acknowledgment) {
        try {
            log.debug("Consuming metrics from partition: {} offset: {}", partition, offset);
            CompletableFuture.runAsync(() -> metricsService.processMetricsFromKafka(metrics))
                .thenAccept(v -> {
                    log.info("Metrics processed successfully for forwarder: {}", metrics.getForwarderId());
                    acknowledgment.acknowledge();
                    log.debug("Offset committed for metrics: {} at partition: {} offset: {}", 
                        metrics.getForwarderId(), partition, offset);
                })
                .exceptionally(ex -> {
                    log.error("Failed to process metrics for forwarder: {}", metrics.getForwarderId(), ex);
                    return null;
                })
                .join();
        } catch (Exception e) {
            log.error("Failed to process metrics for forwarder: {}", metrics.getForwarderId(), e);
        }
    }

    @KafkaListener(topics = "audit-logs", groupId = "${spring.kafka.consumer.group-id}", concurrency = "3")
    public void consumeAuditLog(@Payload AuditLog auditLog,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset,
                               Acknowledgment acknowledgment) {
        try {
            log.debug("Consuming audit log from partition: {} offset: {}", partition, offset);
            CompletableFuture.runAsync(() -> auditService.processAuditLogFromKafka(auditLog))
                .thenAccept(v -> {
                    log.info("Audit log processed successfully: {}", auditLog.getId());
                    acknowledgment.acknowledge();
                    log.debug("Offset committed for audit log: {} at partition: {} offset: {}", 
                        auditLog.getId(), partition, offset);
                })
                .exceptionally(ex -> {
                    log.error("Failed to process audit log: {}", auditLog.getId(), ex);
                    return null;
                })
                .join();
        } catch (Exception e) {
            log.error("Failed to process audit log: {}", auditLog.getId(), e);
        }
    }

    @KafkaListener(topics = "notifications", groupId = "${spring.kafka.consumer.group-id}", concurrency = "3")
    public void consumeNotification(@Payload NotificationMessage notification,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment acknowledgment) {
        try {
            log.debug("Consuming notification from partition: {} offset: {}", partition, offset);
            CompletableFuture.runAsync(() -> 
                notificationService.processNotificationFromKafka(
                    notification.getChannel(),
                    notification.getRecipient(),
                    notification.getSubject(),
                    notification.getBody()
                )
            )
            .thenAccept(v -> {
                log.info("Notification processed successfully for {}: {}", 
                        notification.getChannel(), notification.getRecipient());
                acknowledgment.acknowledge();
                log.debug("Offset committed for notification: {} at partition: {} offset: {}", 
                    notification.getRecipient(), partition, offset);
            })
            .exceptionally(ex -> {
                log.error("Failed to process notification for {}: {}", 
                        notification.getChannel(), notification.getRecipient(), ex);
                return null;
            })
            .join();
        } catch (Exception e) {
            log.error("Failed to process notification for {}: {}", 
                    notification.getChannel(), notification.getRecipient(), e);
        }
    }
}
