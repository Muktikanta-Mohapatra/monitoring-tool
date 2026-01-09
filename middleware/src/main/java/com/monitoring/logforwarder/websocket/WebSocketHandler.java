package com.monitoring.logforwarder.websocket;

import com.monitoring.logforwarder.websocket.validator.QueryValidationResult;
import com.monitoring.logforwarder.websocket.validator.QueryValidator;
import com.monitoring.logforwarder.websocket.validator.ValidationResult;
import com.monitoring.logforwarder.websocket.validator.WebSocketMessageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WebSocket message handler for real-time dashboard communication.
 *
 * <p><b>Purpose:</b> Handles STOMP messages from web clients including subscriptions,
 * queries, and unsubscriptions for real-time log event streaming.</p>
 *
 * <p><b>Message Mappings:</b></p>
 * <ul>
 *   <li>{@code /app/subscribe} - Subscribe to event stream with optional query filter</li>
 *   <li>{@code /app/query} - Execute ad-hoc search query</li>
 *   <li>{@code /app/unsubscribe} - Unsubscribe from event stream</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see EventSubscriptionManager
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketHandler {

    private final EventSubscriptionManager subscriptionManager;

    private final SimpMessagingTemplate messagingTemplate;

    private final WebSocketMessageValidator messageValidator;

    private final QueryValidator queryValidator;

    @MessageMapping("/subscribe")
    public void handleSubscription(@Payload SubscriptionMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);

        ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Subscription message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQuery();
        if (query != null && !query.trim().isEmpty()) {
            List<String> authorizedIndexes = message.getIndexes();
            QueryValidationResult queryValidationResult = queryValidator.validate(query, null, authorizedIndexes);
            if (!queryValidationResult.isValid()) {
                sendErrorResponse(userId, requestId, queryValidationResult.toErrorMessage(requestId));
                log.warn("Query validation failed for user {}: {}", userId, queryValidationResult.getErrors());
                return;
            }
        }

        SubscriptionLimitCheckResult limitResult = subscriptionManager.checkSubscriptionLimit(userId);
        if (!limitResult.isAllowed()) {
            sendErrorResponse(userId, requestId, limitResult.toErrorMessage(requestId));
            log.warn("Subscription limit exceeded for user {}: current={}, max={}", userId, limitResult.getCurrentCount(), limitResult.getMaxCount());
            return;
        }

        subscriptionManager.subscribe(userId, sessionId, query != null ? query : "");

        AckMessage response = AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .processedCount(1L)
                .message("Subscription created successfully")
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/subscriptions", response);
        log.info("User {} subscribed to query with requestId {}", userId, requestId);
    }

    @MessageMapping("/unsubscribe")
    public void handleUnsubscription(@Payload SubscriptionMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);

        ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Unsubscription message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQuery();
        subscriptionManager.unsubscribe(userId, sessionId, query != null ? query : "");

        AckMessage response = AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .processedCount(1L)
                .message("Unsubscription successful")
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/subscriptions", response);
        log.info("User {} unsubscribed with requestId {}", userId, requestId);
    }

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public AckMessage handlePing(@Payload GenericMessage message) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();

        ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            return AckMessage.builder()
                    .type("ACK")
                    .version("1.0")
                    .requestId(generateRequestId())
                    .timestamp(LocalDateTime.now())
                    .originalRequestId(requestId)
                    .status("FAILED")
                    .message("Ping validation failed")
                    .build();
        }

        return AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .message("Pong")
                .build();
    }

    @MessageMapping("/query")
    public void handleQueryRequest(@Payload QueryMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String userId = extractUserId(headerAccessor);

        ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Query message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQueryText();
        if (query != null && !query.trim().isEmpty()) {
            QueryValidationResult queryValidationResult = queryValidator.validate(query, null);
            if (!queryValidationResult.isValid()) {
                ErrorMessage errorMsg = queryValidationResult.toErrorMessage(requestId);
                sendErrorResponse(userId, requestId, errorMsg);
                log.warn("Query validation failed for user {}: {}", userId, queryValidationResult.getErrors());
                return;
            }
        }

        log.info("User {} executing query with requestId {}", userId, requestId);

        AckMessage response = AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .message("Query execution started")
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/queries", response);
    }

    @MessageMapping("/alert-subscribe")
    public void handleAlertSubscription(SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        String requestId = generateRequestId();
        log.info("User {} subscribed to alerts with requestId {}", userId, requestId);

        AckMessage response = AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .message("Alert subscription confirmed")
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/alerts", response);
    }

    @MessageMapping("/metrics-subscribe")
    public void handleMetricsSubscription(@Payload MetricsMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String userId = extractUserId(headerAccessor);

        ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Metrics message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        log.info("User {} subscribed to metrics with requestId {}", userId, requestId);

        AckMessage response = AckMessage.builder()
                .type("ACK")
                .version("1.0")
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .originalRequestId(requestId)
                .status("SUCCESS")
                .message("Metrics subscription confirmed")
                .build();

        messagingTemplate.convertAndSendToUser(userId, "/queue/metrics", response);
    }

    public void onSessionDisconnect(String sessionId) {
        subscriptionManager.unsubscribeSession(sessionId);
        log.info("Session {} disconnected and cleaned up", sessionId);
    }

    private void sendErrorResponse(String userId, String requestId, ErrorMessage errorMessage) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/errors", errorMessage);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Object principal = headerAccessor.getUser();
        if (principal != null) {
            return principal.toString();
        }
        return "anonymous";
    }
}
