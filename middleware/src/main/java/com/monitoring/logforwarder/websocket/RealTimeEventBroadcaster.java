package com.monitoring.logforwarder.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    private final EventSubscriptionManager subscriptionManager;

    public void broadcastEventUpdate(Object eventData, String eventType) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("event")
                .action(eventType)
                .data(eventData)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/events", message);
        log.debug("Broadcasted event update: {}", eventType);
    }

    public void broadcastToQuery(Object eventData, String query) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("queryResult")
                .action("update")
                .data(eventData)
                .timestamp(LocalDateTime.now())
                .build();

        Set<String> subscribers = subscriptionManager.getSubscribersForEvent(query);
        if (!subscribers.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/query/" + query, message);
            log.debug("Broadcasted to {} subscribers for query: {}", subscribers.size(), query);
        }
    }

    public void broadcastAlertUpdate(Object alertData) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("alert")
                .action("triggered")
                .data(alertData)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/alerts", message);
        log.debug("Broadcasted alert update");
    }

    public void broadcastMetricsUpdate(Object metricsData, String forwarderId) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("metrics")
                .action("update")
                .data(metricsData)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/metrics/" + forwarderId, message);
        log.debug("Broadcasted metrics update for forwarder: {}", forwarderId);
    }

    public void broadcastToUser(String userId, Object data, String messageType) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type(messageType)
                .action("notification")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", message);
        log.debug("Broadcasted notification to user: {}", userId);
    }

    public void broadcastDashboardUpdate(Object dashboardData) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("dashboard")
                .action("update")
                .data(dashboardData)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/dashboard", message);
        log.debug("Broadcasted dashboard update");
    }
}
