package com.pokergame.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pokergame.dto.WebSocketMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // Track which users are connected to which rooms
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    // Track which session belongs to which room and player
    private final Map<WebSocketSession, String> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionToPlayer = new ConcurrentHashMap<>();

    public RoomWebSocketHandler() {
        System.out.println("RoomWebSocketHandler created and initialized");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        System.out.println("WebSocket connection established: " + session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            System.out.println("Received WebSocket message: " + payload);

            // Parse the incoming message
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
            };
            Map<String, Object> messageMap = objectMapper.readValue(payload, typeRef);
            String type = (String) messageMap.get("type");
            String roomId = (String) messageMap.get("roomId");
            String playerName = (String) messageMap.get("playerName");

            switch (type) {
                case "JOIN_ROOM":
                    handleJoinRoom(session, roomId, playerName);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom(session, roomId, playerName);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error handling WebSocket message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        System.out.println("WebSocket connection closed: " + session.getId());

        // Clean up session mappings
        String roomId = sessionToRoom.remove(session);
        String playerName = sessionToPlayer.remove(session);

        if (roomId != null) {
            // Remove session from room
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }

            System.out.println("Player " + playerName + " disconnected from room " + roomId);
        }
    }

    private void handleJoinRoom(WebSocketSession session, String roomId, String playerName) {
        System.out.println("Player " + playerName + " joining room " + roomId + " via WebSocket");

        // Add session to room tracking
        roomSessions.computeIfAbsent(roomId, k -> new HashSet<>()).add(session);
        sessionToRoom.put(session, roomId);
        sessionToPlayer.put(session, playerName);

        // Send confirmation to the joining player
        sendToSession(session, new WebSocketMessage("JOINED_ROOM", roomId,
                Map.of("message", "Successfully connected to room", "playerName", playerName)));
    }

    private void handleLeaveRoom(WebSocketSession session, String roomId, String playerName) {
        System.out.println("Player " + playerName + " leaving room " + roomId + " via WebSocket");

        // Remove session from room tracking
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }

        sessionToRoom.remove(session);
        sessionToPlayer.remove(session);
    }

    public void broadcastToRoom(String roomId, WebSocketMessage message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            System.out.println("No WebSocket sessions found for room: " + roomId);
            return;
        }

        System.out.println("Broadcasting to room " + roomId + ": " + message.type());

        // Create a copy to avoid concurrent modification
        Set<WebSocketSession> sessionsCopy = new HashSet<>(sessions);

        for (WebSocketSession session : sessionsCopy) {
            sendToSession(session, message);
        }
    }

    private void sendToSession(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } else {
                System.out.println("Attempted to send to closed session: " + session.getId());
                // Clean up closed session
                cleanupClosedSession(session);
            }
        } catch (Exception e) {
            System.err.println("Error sending WebSocket message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupClosedSession(WebSocketSession session) {
        String roomId = sessionToRoom.remove(session);
        sessionToPlayer.remove(session);

        if (roomId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
        }
    }

    // Utility method to get active sessions count for a room
    public int getActiveSessionsCount(String roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }

    // Utility method to check if a room has any active WebSocket connections
    public boolean hasActiveSessions(String roomId) {
        return getActiveSessionsCount(roomId) > 0;
    }
}