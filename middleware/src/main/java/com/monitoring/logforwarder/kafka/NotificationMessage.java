package com.monitoring.logforwarder.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message payload for notification delivery via Kafka.
 *
 * <p><b>Purpose:</b> Represents a notification to be sent through various channels
 * (email, Slack, webhook) with recipient, subject, and body content.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String channel;
    private String recipient;
    private String subject;
    private String body;
    private LocalDateTime timestamp;
}
