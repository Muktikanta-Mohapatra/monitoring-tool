package com.monitoring.logforwarder.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Event listener for WebSocket session lifecycle events.
 *
 * <p><b>Purpose:</b> Handles WebSocket connection and disconnection events
 * to manage subscription cleanup and connection logging.</p>
 *
 * <p><b>Handled Events:</b></p>
 * <ul>
 *   <li>{@link SessionConnectedEvent} - Log new connection</li>
 *   <li>{@link SessionDisconnectEvent} - Cleanup subscriptions on disconnect</li>
 *   <li>{@link SessionSubscribeEvent} - Log subscription</li>
 *   <li>{@link SessionUnsubscribeEvent} - Log unsubscription</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final EventSubscriptionManager subscriptionManager;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.info("WebSocket client connected: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        if (sessionId != null) {
            subscriptionManager.unsubscribeSession(sessionId);
            log.info("WebSocket client disconnected: {}", sessionId);
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        log.info("WebSocket subscription - Session: {}, Destination: {}", sessionId, destination);
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        log.info("WebSocket unsubscription - Session: {}, Destination: {}", sessionId, destination);
    }
}
