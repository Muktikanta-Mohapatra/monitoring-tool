package com.monitoring.logforwarder.websocket;

import com.monitoring.logforwarder.websocket.WebSocketMessageTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EventSubscriptionManager {

    private static final int MAX_SUBSCRIPTIONS_PER_USER = 50;

    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> userSubscriptionCount = new ConcurrentHashMap<>();

    public void subscribe(String userId, String sessionId, String query) {
        String subscriptionKey = userId + ":" + query;
        subscriptions.computeIfAbsent(subscriptionKey, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        userSessions.put(sessionId, userId);
        
        userSubscriptionCount.compute(userId, (k, v) -> v == null ? 1 : v + 1);
        log.info("User {} subscribed to: {}", userId, query);
    }

    public void unsubscribe(String userId, String sessionId, String query) {
        String subscriptionKey = userId + ":" + query;
        Set<String> sessions = subscriptions.get(subscriptionKey);
        if (sessions != null) {
            boolean wasRemoved = sessions.remove(sessionId);
            if (wasRemoved) {
                userSubscriptionCount.compute(userId, (k, v) -> v != null && v > 0 ? v - 1 : 0);
            }
            if (sessions.isEmpty()) {
                subscriptions.remove(subscriptionKey);
            }
        }
        log.info("User {} unsubscribed from: {}", userId, query);
    }

    public void unsubscribeSession(String sessionId) {
        String userId = userSessions.remove(sessionId);
        if (userId != null) {
            int[] removedCount = {0};
            for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
                if (entry.getKey().startsWith(userId + ":")) {
                    boolean wasRemoved = entry.getValue().remove(sessionId);
                    if (wasRemoved) {
                        removedCount[0]++;
                    }
                    if (entry.getValue().isEmpty()) {
                        subscriptions.remove(entry.getKey());
                    }
                }
            }
            if (removedCount[0] > 0) {
                userSubscriptionCount.compute(userId, (k, v) -> v != null && v >= removedCount[0] ? v - removedCount[0] : 0);
            }
            log.info("Session {} cleaned up for user {} (removed {} subscriptions)", sessionId, userId, removedCount[0]);
        }
    }

    public SubscriptionLimitCheckResult checkSubscriptionLimit(String userId) {
        int currentCount = userSubscriptionCount.getOrDefault(userId, 0);
        if (currentCount >= MAX_SUBSCRIPTIONS_PER_USER) {
            return SubscriptionLimitCheckResult.exceeded(currentCount, MAX_SUBSCRIPTIONS_PER_USER);
        }
        return SubscriptionLimitCheckResult.allowed();
    }

    public int getSubscriptionCountForUser(String userId) {
        return userSubscriptionCount.getOrDefault(userId, 0);
    }

    public Set<String> getSubscribersForEvent(String query) {
        Set<String> subscribers = new HashSet<>();
        subscriptions.forEach((key, sessions) -> {
            if (key.endsWith(":" + query)) {
                subscribers.addAll(sessions);
            }
        });
        return subscribers;
    }

    public Set<String> getSubscribersForUser(String userId) {
        Set<String> subscribers = new HashSet<>();
        subscriptions.forEach((key, sessions) -> {
            if (key.startsWith(userId + ":")) {
                subscribers.addAll(sessions);
            }
        });
        return subscribers;
    }

    public int getActiveSubscriptions() {
        return userSessions.size();
    }

    public Map<String, Set<String>> getAllSubscriptions() {
        return new HashMap<>(subscriptions);
    }

    public static class SubscriptionLimitCheckResult {
        private final boolean allowed;
        private final int currentCount;
        private final int maxCount;

        private SubscriptionLimitCheckResult(boolean allowed, int currentCount, int maxCount) {
            this.allowed = allowed;
            this.currentCount = currentCount;
            this.maxCount = maxCount;
        }

        public static SubscriptionLimitCheckResult allowed() {
            return new SubscriptionLimitCheckResult(true, 0, MAX_SUBSCRIPTIONS_PER_USER);
        }

        public static SubscriptionLimitCheckResult exceeded(int currentCount, int maxCount) {
            return new SubscriptionLimitCheckResult(false, currentCount, maxCount);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        public int getMaxCount() {
            return maxCount;
        }

        public WebSocketMessageTypes.ErrorMessage toErrorMessage(String requestId) {
            return WebSocketMessageTypes.ErrorMessage.builder()
                    .type("ERROR")
                    .version("1.0")
                    .requestId(requestId)
                    .timestamp(LocalDateTime.now())
                    .errorCode(WebSocketMessageTypes.ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED)
                    .errorMessage("Subscription limit exceeded: maximum " + maxCount + " active subscriptions allowed, currently at " + currentCount)
                    .severity(WebSocketMessageTypes.ErrorSeverity.WARNING)
                    .details(Map.of(
                        "currentCount", currentCount,
                        "maxCount", maxCount,
                        "limitExceededBy", currentCount - maxCount + 1
                    ))
                    .build();
        }
    }
}
