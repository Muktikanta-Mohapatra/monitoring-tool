package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class NotificationService {

    public CompletableFuture<Void> sendNotification(String channel, Long alertId) {
        return AsyncHelper.executeAsyncFuture(() -> {
            final String finalChannel = (channel == null || channel.isEmpty()) ? "email" : channel;
            
            log.info("Sending {} notification for alert {}", finalChannel, alertId);
            
            switch (finalChannel.toLowerCase()) {
                case "email":
                    sendEmailNotification(alertId);
                    break;
                case "slack":
                    sendSlackNotification(alertId);
                    break;
                case "webhook":
                    sendWebhookNotification(alertId);
                    break;
                default:
                    log.warn("Unknown notification channel: {}", finalChannel);
            }
            return null;
        });
    }

    private void sendEmailNotification(Long alertId) {
        log.debug("Sending email notification for alert {}", alertId);
    }

    private void sendSlackNotification(Long alertId) {
        log.debug("Sending Slack notification for alert {}", alertId);
    }

    private void sendWebhookNotification(Long alertId) {
        log.debug("Sending webhook notification for alert {}", alertId);
    }

    public CompletableFuture<Void> handleNotificationFailure(Long alertId, String channel, String reason) {
        return AsyncHelper.executeAsyncFuture(() -> {
            log.error("Failed to send {} notification for alert {}: {}", channel, alertId, reason);
            return null;
        });
    }

    public CompletableFuture<Void> processNotificationFromKafka(String channel, String recipient, String subject, String body) {
        return AsyncHelper.executeAsyncFuture(() -> {
            try {
                log.debug("Processing notification from Kafka: channel={}, recipient={}", channel, recipient);

                if (channel == null || channel.isEmpty() || recipient == null || recipient.isEmpty()) {
                    log.warn("Received notification with missing channel or recipient");
                    return null;
                }

                switch (channel.toLowerCase()) {
                    case "email":
                        dispatchEmailNotification(recipient, subject, body);
                        break;
                    case "slack":
                        dispatchSlackNotification(recipient, subject, body);
                        break;
                    case "webhook":
                        dispatchWebhookNotification(recipient, subject, body);
                        break;
                    default:
                        log.warn("Unknown notification channel: {}", channel);
                }
            } catch (Exception e) {
                log.error("Error processing notification from Kafka: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    private void dispatchEmailNotification(String recipient, String subject, String body) {
        try {
            log.info("Dispatching email notification to {} with subject: {}", recipient, subject);
        } catch (Exception e) {
            log.error("Failed to dispatch email notification to {}: {}", recipient, e.getMessage());
        }
    }

    private void dispatchSlackNotification(String recipient, String subject, String body) {
        try {
            log.info("Dispatching Slack notification to channel/user: {} with message: {}", recipient, subject);
        } catch (Exception e) {
            log.error("Failed to dispatch Slack notification to {}: {}", recipient, e.getMessage());
        }
    }

    private void dispatchWebhookNotification(String recipient, String subject, String body) {
        try {
            log.info("Dispatching webhook notification to {} with payload", recipient);
        } catch (Exception e) {
            log.error("Failed to dispatch webhook notification to {}: {}", recipient, e.getMessage());
        }
    }
}
