package com.queueless.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket configuration using STOMP over SockJS.
 * Clients subscribe to /topic/queue/{shopId} for live queue updates.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.allowed-origins}")
    private String allowedOrigins;

    /**
     * Configures the in-memory message broker.
     * /topic — broadcast (shop queue updates, TV display)
     * /queue — point-to-point (individual token notifications)
     * /app  — client-to-server destination prefix
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Registers the STOMP WebSocket endpoint with SockJS fallback.
     * Mobile apps connect directly via WebSocket; web uses SockJS.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS()
                .setHeartbeatTime(25000);

        // Native WebSocket endpoint for React Native (no SockJS)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns(origins);
    }
}
