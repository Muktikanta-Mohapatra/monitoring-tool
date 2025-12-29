package com.monitoring.logforwarder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration class for WebSocket messaging with STOMP protocol support.
 *
 * <p><b>Purpose:</b> Configures WebSocket endpoints and message broker for real-time,
 * bidirectional communication between the server and web clients. Enables live updates
 * for log events, alerts, metrics, and system notifications.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>STOMP (Simple Text Oriented Messaging Protocol) over WebSocket</li>
 *   <li>Simple in-memory message broker for /topic and /queue destinations</li>
 *   <li>Application destination prefix: /app for client-to-server messages</li>
 *   <li>User destination prefix: /user for user-specific messages</li>
 *   <li>SockJS fallback for browsers without WebSocket support</li>
 *   <li>Configurable allowed origins for CORS security</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * app:
 *   websocket:
 *     allowed-origins: http://localhost:3000,https://dashboard.example.com
 *
 * // JavaScript client connection:
 * const socket = new SockJS('/ws');
 * const stompClient = Stomp.over(socket);
 * stompClient.connect({}, () => {
 *     stompClient.subscribe('/topic/events', (message) => {
 *         console.log('Received:', JSON.parse(message.body));
 *     });
 * });
 *
 * // Server-side broadcasting:
 * &#64;Autowired
 * private SimpMessagingTemplate messagingTemplate;
 * messagingTemplate.convertAndSend("/topic/events", eventDTO);
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see WebSocketMessageBrokerConfigurer
 * @see StompEndpointRegistry
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * Configures the message broker for WebSocket communication.
     *
     * <p><b>Purpose:</b> Sets up the simple in-memory message broker with destination
     * prefixes for topic subscriptions, application messages, and user-specific messages.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>/topic, /queue - broker destinations for subscriptions</li>
     *   <li>/app - prefix for messages from client to server</li>
     *   <li>/user - prefix for user-specific destinations</li>
     * </ul>
     *
     * @param config the message broker registry to configure
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Registers STOMP endpoints for WebSocket connections.
     *
     * <p><b>Purpose:</b> Configures the WebSocket endpoint at /ws with CORS support
     * and SockJS fallback for browsers without native WebSocket support.</p>
     *
     * @param registry the STOMP endpoint registry to configure
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] originsArray = allowedOrigins.split(",");
        for (int i = 0; i < originsArray.length; i++) {
            originsArray[i] = originsArray[i].trim();
        }
        registry.addEndpoint("/ws")
                .setAllowedOrigins(originsArray)
                .withSockJS();
    }
}
