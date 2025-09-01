package com.pokergame.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pokergame.dto.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for managing real-time communication between poker game
 * clients.
 * Handles room-based messaging, player connections, and game state
 * broadcasting.
 */
@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomWebSocketHandler.class);
    private final ObjectMapper objectMapper;

    // Track which users are connected to which rooms
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    // Track which session belongs to which room and player
    private final Map<WebSocketSession, String> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionToPlayer = new ConcurrentHashMap<>();

    public RoomWebSocketHandler() {
        logger.info("RoomWebSocketHandler created and initialized");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Called when a new WebSocket connection is established.
     * 
     * @param session the WebSocket session that was established
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        logger.debug("WebSocket connection established: {}", session.getId());
    }

    /**
     * Handles incoming text messages from WebSocket clients.
     * Routes messages based on type to appropriate handler methods.
     * 
     * @param session the WebSocket session that sent the message
     * @param message the text message received
     * @throws Exception if message processing fails
     */
    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            logger.debug("Received WebSocket message: {}", payload);

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
                    logger.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message: {}", e.getMessage(), e);
        }
    }

    /**
     * Called when a WebSocket connection is closed.
     * Cleans up session mappings and removes the session from room tracking.
     * 
     * @param session the WebSocket session that was closed
     * @param status  the close status
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        logger.debug("WebSocket connection closed: {}", session.getId());

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

            logger.info("Player {} disconnected from room {}", playerName, roomId);
        }
    }

    /**
     * Handles a player joining a room via WebSocket.
     * 
     * @param session    the WebSocket session of the joining player
     * @param roomId     the ID of the room to join
     * @param playerName the name of the player joining
     */
    private void handleJoinRoom(WebSocketSession session, String roomId, String playerName) {
        logger.info("Player {} joining room {} via WebSocket", playerName, roomId);

        // Add session to room tracking
        roomSessions.computeIfAbsent(roomId, k -> new HashSet<>()).add(session);
        sessionToRoom.put(session, roomId);
        sessionToPlayer.put(session, playerName);

        // Send confirmation to the joining player
        sendToSession(session, new WebSocketMessage("JOINED_ROOM", roomId,
                Map.of("message", "Successfully connected to room", "playerName", playerName)));
    }

    /**
     * Handles a player leaving a room via WebSocket.
     * 
     * @param session    the WebSocket session of the leaving player
     * @param roomId     the ID of the room to leave
     * @param playerName the name of the player leaving
     */
    private void handleLeaveRoom(WebSocketSession session, String roomId, String playerName) {
        logger.info("Player {} leaving room {} via WebSocket", playerName, roomId);

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

    /**
     * Broadcasts a message to all players in a specific room.
     * 
     * @param roomId  the ID of the room to broadcast to
     * @param message the message to broadcast
     */
    public void broadcastToRoom(String roomId, WebSocketMessage message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            logger.debug("No WebSocket sessions found for room: {}", roomId);
            return;
        }

        logger.debug("Broadcasting to room {}: {}", roomId, message.type());

        // Create a copy to avoid concurrent modification
        Set<WebSocketSession> sessionsCopy = new HashSet<>(sessions);

        for (WebSocketSession session : sessionsCopy) {
            sendToSession(session, message);
        }
    }

    /**
     * Sends a message to a specific player in a room.
     * 
     * @param roomId     the ID of the room
     * @param playerName the name of the target player
     * @param message    the message to send
     */
    public void sendToPlayer(String roomId, String playerName, WebSocketMessage message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            logger.debug("No WebSocket sessions found for room: {}", roomId);
            return;
        }

        logger.debug("Sending to player {} in room {}: {}", playerName, roomId, message.type());

        // Find the session for the specific player
        for (WebSocketSession session : sessions) {
            String sessionPlayerName = sessionToPlayer.get(session);
            if (playerName.equals(sessionPlayerName)) {
                sendToSession(session, message);
                return;
            }
        }

        logger.warn("Player {} not found in room {}", playerName, roomId);
    }

    /**
     * Sends a message to a specific WebSocket session.
     * 
     * @param session the WebSocket session to send to
     * @param message the message to send
     */
    private void sendToSession(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } else {
                logger.debug("Attempted to send to closed session: {}", session.getId());
                // Clean up closed session
                cleanupClosedSession(session);
            }
        } catch (Exception e) {
            logger.error("Error sending WebSocket message: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up tracking data for a closed WebSocket session.
     * 
     * @param session the session to clean up
     */
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