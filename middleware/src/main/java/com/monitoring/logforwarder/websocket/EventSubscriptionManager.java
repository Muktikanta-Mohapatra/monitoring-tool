package com.monitoring.logforwarder.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for WebSocket event subscriptions and session tracking.
 *
 * <p><b>Purpose:</b> Tracks active WebSocket subscriptions per user/session,
 * manages subscription limits, and provides lookup for targeted message delivery.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Thread-safe subscription storage using ConcurrentHashMap</li>
 *   <li>Maximum 50 subscriptions per user to prevent abuse</li>
 *   <li>Automatic cleanup on session disconnect</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
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
        return SubscriptionLimitCheckResult.allowed(MAX_SUBSCRIPTIONS_PER_USER);
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
}
