package com.monitoring.logforwarder.websocket;

import com.monitoring.logforwarder.websocket.validator.QueryValidator;
import com.monitoring.logforwarder.websocket.validator.WebSocketMessageValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
public class WebSocketHandler {

    @Autowired
    private EventSubscriptionManager subscriptionManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private WebSocketMessageValidator messageValidator;

    @Autowired
    private QueryValidator queryValidator;

    @MessageMapping("/subscribe")
    public void handleSubscription(@Payload WebSocketMessageTypes.SubscriptionMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);

        WebSocketMessageValidator.ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            WebSocketMessageTypes.ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Subscription message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQuery();
        if (query != null && !query.trim().isEmpty()) {
            List<String> authorizedIndexes = message.getIndexes();
            QueryValidator.QueryValidationResult queryValidationResult = queryValidator.validate(query, null, authorizedIndexes);
            if (!queryValidationResult.isValid()) {
                sendErrorResponse(userId, requestId, queryValidationResult.toErrorMessage(requestId));
                log.warn("Query validation failed for user {}: {}", userId, queryValidationResult.getErrors());
                return;
            }
        }

        EventSubscriptionManager.SubscriptionLimitCheckResult limitResult = subscriptionManager.checkSubscriptionLimit(userId);
        if (!limitResult.isAllowed()) {
            sendErrorResponse(userId, requestId, limitResult.toErrorMessage(requestId));
            log.warn("Subscription limit exceeded for user {}: current={}, max={}", userId, limitResult.getCurrentCount(), limitResult.getMaxCount());
            return;
        }

        subscriptionManager.subscribe(userId, sessionId, query != null ? query : "");

        WebSocketMessageTypes.AckMessage response = WebSocketMessageTypes.AckMessage.builder()
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
    public void handleUnsubscription(@Payload WebSocketMessageTypes.SubscriptionMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String sessionId = headerAccessor.getSessionId();
        String userId = extractUserId(headerAccessor);

        WebSocketMessageValidator.ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            WebSocketMessageTypes.ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Unsubscription message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQuery();
        subscriptionManager.unsubscribe(userId, sessionId, query != null ? query : "");

        WebSocketMessageTypes.AckMessage response = WebSocketMessageTypes.AckMessage.builder()
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
    public WebSocketMessageTypes.AckMessage handlePing(@Payload WebSocketMessageTypes.GenericMessage message) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();

        WebSocketMessageValidator.ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            return WebSocketMessageTypes.AckMessage.builder()
                    .type("ACK")
                    .version("1.0")
                    .requestId(generateRequestId())
                    .timestamp(LocalDateTime.now())
                    .originalRequestId(requestId)
                    .status("FAILED")
                    .message("Ping validation failed")
                    .build();
        }

        return WebSocketMessageTypes.AckMessage.builder()
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
    public void handleQueryRequest(@Payload WebSocketMessageTypes.QueryMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String userId = extractUserId(headerAccessor);

        WebSocketMessageValidator.ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            WebSocketMessageTypes.ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Query message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        String query = message.getQueryText();
        if (query != null && !query.trim().isEmpty()) {
            QueryValidator.QueryValidationResult queryValidationResult = queryValidator.validate(query, null);
            if (!queryValidationResult.isValid()) {
                WebSocketMessageTypes.ErrorMessage errorMsg = queryValidationResult.toErrorMessage(requestId);
                sendErrorResponse(userId, requestId, errorMsg);
                log.warn("Query validation failed for user {}: {}", userId, queryValidationResult.getErrors());
                return;
            }
        }

        log.info("User {} executing query with requestId {}", userId, requestId);

        WebSocketMessageTypes.AckMessage response = WebSocketMessageTypes.AckMessage.builder()
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

        WebSocketMessageTypes.AckMessage response = WebSocketMessageTypes.AckMessage.builder()
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
    public void handleMetricsSubscription(@Payload WebSocketMessageTypes.MetricsMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String requestId = message.getRequestId() != null ? message.getRequestId() : generateRequestId();
        String userId = extractUserId(headerAccessor);

        WebSocketMessageValidator.ValidationResult validationResult = messageValidator.validate(message);
        if (!validationResult.isValid()) {
            WebSocketMessageTypes.ErrorMessage errorMsg = validationResult.toErrorMessage();
            errorMsg.setRequestId(requestId);
            errorMsg.setOriginalRequestId(requestId);
            sendErrorResponse(userId, requestId, errorMsg);
            log.warn("Metrics message validation failed for user {}: {}", userId, validationResult.getErrors());
            return;
        }

        log.info("User {} subscribed to metrics with requestId {}", userId, requestId);

        WebSocketMessageTypes.AckMessage response = WebSocketMessageTypes.AckMessage.builder()
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

    private void sendErrorResponse(String userId, String requestId, WebSocketMessageTypes.ErrorMessage errorMessage) {
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
