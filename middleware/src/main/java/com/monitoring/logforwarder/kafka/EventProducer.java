package com.monitoring.logforwarder.kafka;

import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.dto.ForwarderMetricsDTO;
import com.monitoring.logforwarder.entity.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer service for publishing messages to various topics.
 *
 * <p><b>Purpose:</b> Handles publishing of events, alerts, metrics, audit logs, and
 * notifications to their respective Kafka topics with async confirmation.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Publishes to: events, alerts, metrics, audit-logs, notifications</li>
 *   <li>Uses async send with completion callbacks</li>
 *   <li>Sets message keys for partition routing</li>
 *   <li>Returns CompletableFuture for confirmation waiting</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see KafkaTemplate
 */
@Slf4j
@Service
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<Void> publishEvent(EventDTO event) {
        log.debug("Publishing event to Kafka topic: events, eventId: {}", event.getId());
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        Message<EventDTO> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "events")
                .setHeader(KafkaHeaders.KEY, event.getSourceName())
                .setHeader("eventId", event.getId())
                .setHeader("eventTimestamp", System.currentTimeMillis())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Event published successfully to partition: {} with offset: {} for eventId: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                event.getId());
                        future.complete(null);
                    } else {
                        log.error("Failed to publish event: {}", event.getId(), ex);
                        future.completeExceptionally(ex);
                    }
                });
        
        return future;
    }

    public CompletableFuture<Void> publishAlert(AlertDTO alert) {
        log.debug("Publishing alert to Kafka topic: alerts");
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        Message<AlertDTO> message = MessageBuilder
                .withPayload(alert)
                .setHeader(KafkaHeaders.TOPIC, "alerts")
                .setHeader(KafkaHeaders.KEY, alert.getId() != null ? alert.getId().toString() : "unknown")
                .setHeader("severity", alert.getSeverity())
                .setHeader("alertTimestamp", System.currentTimeMillis())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Alert published successfully to partition: {} with offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        future.complete(null);
                    } else {
                        log.error("Failed to publish alert: {}", alert.getId(), ex);
                        future.completeExceptionally(ex);
                    }
                });
        
        return future;
    }

    public CompletableFuture<Void> publishMetrics(ForwarderMetricsDTO metrics) {
        log.debug("Publishing metrics to Kafka topic: metrics");
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        Message<ForwarderMetricsDTO> message = MessageBuilder
                .withPayload(metrics)
                .setHeader(KafkaHeaders.TOPIC, "metrics")
                .setHeader(KafkaHeaders.KEY, metrics.getForwarderId() != null ? metrics.getForwarderId().toString() : "unknown")
                .setHeader("metricsTimestamp", System.currentTimeMillis())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Metrics published successfully to partition: {} with offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        future.complete(null);
                    } else {
                        log.error("Failed to publish metrics for forwarder: {}", metrics.getForwarderId(), ex);
                        future.completeExceptionally(ex);
                    }
                });
        
        return future;
    }

    public CompletableFuture<Void> publishAuditLog(AuditLog auditLog) {
        log.debug("Publishing audit log to Kafka topic: audit-logs");
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        Message<AuditLog> message = MessageBuilder
                .withPayload(auditLog)
                .setHeader(KafkaHeaders.TOPIC, "audit-logs")
                .setHeader(KafkaHeaders.KEY, auditLog.getId() != null ? auditLog.getId().toString() : "unknown")
                .setHeader("action", auditLog.getAction())
                .setHeader("auditTimestamp", System.currentTimeMillis())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Audit log published successfully to partition: {} with offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        future.complete(null);
                    } else {
                        log.error("Failed to publish audit log: {}", auditLog.getId(), ex);
                        future.completeExceptionally(ex);
                    }
                });
        
        return future;
    }

    public CompletableFuture<Void> publishNotification(String channel, String recipient, String subject, String body) {
        log.debug("Publishing notification to Kafka topic: notifications");
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        NotificationMessage notification = NotificationMessage.builder()
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .timestamp(LocalDateTime.now())
                .build();

        Message<NotificationMessage> message = MessageBuilder
                .withPayload(notification)
                .setHeader(KafkaHeaders.TOPIC, "notifications")
                .setHeader(KafkaHeaders.KEY, channel)
                .setHeader("notificationTimestamp", System.currentTimeMillis())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Notification published successfully to partition: {} with offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        future.complete(null);
                    } else {
                        log.error("Failed to publish notification to {}: {}", channel, recipient, ex);
                        future.completeExceptionally(ex);
                    }
                });
        
        return future;
    }
}
