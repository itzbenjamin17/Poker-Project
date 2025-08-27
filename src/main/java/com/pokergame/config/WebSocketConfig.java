package com.pokergame.config;

import com.pokergame.websocket.RoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private RoomWebSocketHandler roomWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        System.out.println("Registering WebSocket handler at /ws/room");
        registry.addHandler(roomWebSocketHandler, "/ws/room")
                .setAllowedOrigins("http://localhost:3000"); // CORS for React
        System.out.println("WebSocket handler registered successfully");
    }
}