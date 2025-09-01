package com.pokergame.config;

import com.pokergame.websocket.RoomWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for poker game real-time communication.
 * Registers WebSocket handlers and configures CORS settings.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private RoomWebSocketHandler roomWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        logger.info("Registering WebSocket handler at /ws/room");
        registry.addHandler(roomWebSocketHandler, "/ws/room")
                .setAllowedOrigins("http://localhost:3000"); // CORS for React
        logger.info("WebSocket handler registered successfully");
    }
}