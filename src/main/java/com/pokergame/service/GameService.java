package com.pokergame.service;

import com.pokergame.dto.CreateRoomRequest;
import com.pokergame.dto.JoinRoomRequest;
import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.model.Room;
import com.pokergame.dto.PlayerDecision;
import com.pokergame.dto.WebSocketMessage;
import com.pokergame.websocket.RoomWebSocketHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private HandEvaluatorService handEvaluator;
    private final Map<String, Game> activeGames = new HashMap<>();
    private Map<String, Room> rooms = new ConcurrentHashMap<>();
    private Map<String, String> roomHosts = new ConcurrentHashMap<>();
    @Autowired
    private RoomWebSocketHandler webSocketHandler;

    /**
     * Create a room/lobby (NOT a game yet)
     */
    public String createRoom(CreateRoomRequest request) {
        // Check if room name already exists
        if (isRoomNameTaken(request.getRoomName())) {
            throw new IllegalArgumentException(
                    "Room name '" + request.getRoomName() + "' is already taken. Please choose a different name.");
        }

        String roomId = UUID.randomUUID().toString(); // Reuse the ID generator

        Room room = new Room(
                roomId,
                request.getRoomName(),
                request.getPlayerName(), // Host name
                request.getMaxPlayers(),
                request.getSmallBlind(),
                request.getBigBlind(),
                request.getBuyIn(),
                request.getPassword());

        // Add the host as the first player
        room.addPlayer(request.getPlayerName());

        rooms.put(roomId, room);
        roomHosts.put(roomId, request.getPlayerName());

        webSocketHandler.broadcastToRoom(roomId,
                new WebSocketMessage("ROOM_CREATED", roomId, room));

        return roomId;
    }

    /**
     * Join a room (NOT a game)
     */
    public void joinRoom(JoinRoomRequest joinRequest) {
        Room room = rooms.get(joinRequest.roomId());
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check password if room is private
        if (room.hasPassword() && !room.checkPassword(joinRequest.password())) {
            throw new IllegalArgumentException("Invalid password");
        }

        // Check if room is full
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("Room is full");
        }

        // Check if player name already exists
        if (room.hasPlayer(joinRequest.playerName())) {
            throw new IllegalArgumentException("Player name already taken");
        }

        room.addPlayer(joinRequest.playerName());

        webSocketHandler.broadcastToRoom(joinRequest.roomId(),
                new WebSocketMessage("PLAYER_JOINED", joinRequest.roomId(), getRoomData(joinRequest.roomId())));
    }

    /**
     * Get room information
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<Room> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * Get formatted room data for API responses and WebSocket broadcasts
     */
    public Map<String, Object> getRoomData(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        // Convert player names to player objects with isHost flag
        List<Map<String, Object>> playerObjects = room.getPlayers().stream()
                .map(playerName -> {
                    Map<String, Object> playerMap = new HashMap<>();
                    playerMap.put("name", playerName);
                    playerMap.put("isHost", isRoomHost(roomId, playerName));
                    playerMap.put("joinedAt", "recently");
                    return playerMap;
                })
                .collect(java.util.stream.Collectors.toList());

        // Create the room data map
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomId", roomId);
        roomData.put("roomName", room.getRoomName());
        roomData.put("maxPlayers", room.getMaxPlayers());
        roomData.put("buyIn", room.getBuyIn());
        roomData.put("smallBlind", room.getSmallBlind());
        roomData.put("bigBlind", room.getBigBlind());
        roomData.put("createdAt", room.getCreatedAt());
        roomData.put("hostName", room.getHostName());
        roomData.put("players", playerObjects);
        roomData.put("currentPlayers", playerObjects.size());
        roomData.put("canStartGame", playerObjects.size() >= 2);

        return roomData;
    }

    /**
     * Create actual game from room (when 2+ players ready)
     */
    public String createGameFromRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        if (room.getPlayers().size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players to start game");
        }

        // Create the actual poker game
        List<String> playerNames = new ArrayList<>(room.getPlayers());
        List<Player> players = playerNames.stream()
                .map(name -> new Player(name, UUID.randomUUID().toString(), room.getBuyIn()))
                .collect(Collectors.toList());

        Game game = new Game(roomId, players, room.getSmallBlind(), room.getBigBlind(), handEvaluator);
        activeGames.put(roomId, game);

        room.setGameStarted();

        // Broadcast to all players in the room that the game has started
        Map<String, Object> gameStartMessage = new HashMap<>();
        gameStartMessage.put("gameId", roomId);
        gameStartMessage.put("message", "Game started! Redirecting to game...");

        webSocketHandler.broadcastToRoom(roomId,
                new WebSocketMessage("GAME_STARTED", roomId, gameStartMessage));

        // Start the first hand after a brief delay to allow players to navigate
        System.out.println("Game created and broadcasted: " + roomId);

        // Initialize the game properly
        startNewHand(roomId);

        return roomId;
    }

    /**
     * Check if player is room host
     */
    public boolean isRoomHost(String roomId, String playerName) {
        String hostName = roomHosts.get(roomId);
        return hostName != null && hostName.equals(playerName);
    }

    /**
     * Check if room name is already taken
     */
    private boolean isRoomNameTaken(String roomName) {
        return rooms.values().stream()
                .anyMatch(room -> room.getRoomName().equalsIgnoreCase(roomName));
    }

    /**
     * Find room by name
     */
    public Room findRoomByName(String roomName) {
        return rooms.values().stream()
                .filter(room -> room.getRoomName().equalsIgnoreCase(roomName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Join room by name instead of ID
     */
    public String joinRoomByName(String roomName, String playerName, String password) {
        Room room = findRoomByName(roomName);
        if (room == null) {
            throw new IllegalArgumentException("Room '" + roomName + "' not found");
        }

        String roomId = room.getRoomId();
        JoinRoomRequest joinRequest = new JoinRoomRequest(roomId, playerName, password);
        joinRoom(joinRequest);
        return roomId;
    }

    /**
     * Remove player from room
     */
    public void leaveRoom(String roomId, String playerName) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check if the leaving player is the host
        if (isRoomHost(roomId, playerName)) {
            // Host is leaving - destroy the entire Room
            webSocketHandler.broadcastToRoom(roomId,
                    new WebSocketMessage("ROOM_CLOSED", roomId, null));
            destroyRoom(roomId);
        } else {
            // Regular player leaving - just remove them from the room
            room.removePlayer(playerName);
            webSocketHandler.broadcastToRoom(roomId,
                    new WebSocketMessage("PLAYER_LEFT", roomId, getRoomData(roomId)));

            // If no players left after removal, also destroy the room
            if (room.getPlayers().isEmpty()) {
                webSocketHandler.broadcastToRoom(roomId,
                        new WebSocketMessage("ROOM_CLOSED", roomId, null));
                destroyRoom(roomId);
            }
        }
    }

    /**
     * Leave room by room name instead of ID
     */
    public void leaveRoomByName(String roomName, String playerName) {
        Room room = findRoomByName(roomName);
        if (room == null) {
            throw new IllegalArgumentException("Room '" + roomName + "' not found");
        }

        leaveRoom(room.getRoomId(), playerName);
    }

    /**
     * Destroy/delete a room completely
     */
    private void destroyRoom(String roomId) {
        rooms.remove(roomId);
        roomHosts.remove(roomId);
    }

    public void startNewHand(String gameId) {
        Game game = getGame(gameId);
        if (game.isGameOver()) {
            return;
        }

        game.resetForNewHand();
        game.dealHoleCards();
        game.postBlinds();

        // Broadcast the initial game state to all players
        broadcastGameState(gameId);

        System.out.println("New hand started for game: " + gameId +
                ", current player: " + game.getCurrentPlayer().getName());
    }

    public void processPlayerAction(String gameId, PlayerActionRequest actionRequest) {
        Game game = getGame(gameId);
        Player currentPlayer = game.getCurrentPlayer();

        // Verify that the requesting player is the current player
        if (!currentPlayer.getName().equals(actionRequest.playerName())) {
            throw new SecurityException("It's not your turn. Current player is: " + currentPlayer.getName());
        }

        PlayerDecision decision = new PlayerDecision(
                actionRequest.action(),
                actionRequest.amount() != null ? actionRequest.amount() : 0,
                currentPlayer.getPlayerId());

        game.processPlayerDecision(currentPlayer, decision);

        // Broadcast game state after player action
        broadcastGameState(gameId);

        if (game.isBettingRoundComplete()) {
            advanceGame(gameId);
        } else {
            game.nextPlayer();
            // Broadcast again after moving to next player
            broadcastGameState(gameId);
        }
    }

    private void conductBettingRound(String gameId) {
        Game game = getGame(gameId);

        while (!game.isHandOver() && !game.isBettingRoundComplete()) {
            Player currentPlayer = game.getCurrentPlayer();
            if (currentPlayer.getHasFolded() || currentPlayer.getIsAllIn()) {
                game.nextPlayer();
                continue;
            }
            game.nextPlayer();
        }

        advanceGame(gameId);
    }

    private void advanceGame(String gameId) {
        Game game = getGame(gameId);
        if (game.isHandOver()) {
            List<Player> winners = game.conductShowdown();
            // Broadcast showdown results
            broadcastGameState(gameId);
            game.cleanupAfterHand();
            game.advancePositions();
            startNewHand(gameId);
            return;
        }

        switch (game.getCurrentPhase()) {
            case PRE_FLOP:
                game.dealFlop();
                broadcastGameState(gameId);
                break;
            case FLOP:
                game.dealTurn();
                broadcastGameState(gameId);
                break;
            case TURN:
                game.dealRiver();
                broadcastGameState(gameId);
                break;
            case RIVER:
                List<Player> winners = game.conductShowdown();
                broadcastGameState(gameId);
                game.cleanupAfterHand();
                game.advancePositions();
                startNewHand(gameId);
                break;
            case SHOWDOWN:
                // Handle showdown phase
                break;
        }
    }

    public Game getGame(String gameId) {
        return activeGames.get(gameId);
    }

    /**
     * Broadcast current game state to all players in the game
     */
    public void broadcastGameState(String gameId) {
        Game game = getGame(gameId);
        if (game == null) {
            return;
        }

        // Create game state data for WebSocket broadcast
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", gameId);
        gameState.put("pot", game.getPot());
        gameState.put("phase", game.getCurrentPhase().toString());
        gameState.put("currentBet", game.getCurrentHighestBet());
        gameState.put("communityCards", game.getCommunityCards());

        // Add current player information
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != null) {
            gameState.put("currentPlayerName", currentPlayer.getName());
            gameState.put("currentPlayerId", currentPlayer.getPlayerId());
        }

        // Convert players to frontend format (without showing other players' cards)
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (var player : game.getPlayers()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("id", player.getPlayerId());
            playerData.put("name", player.getName());
            playerData.put("chips", player.getChips());
            playerData.put("currentBet", player.getCurrentBet());
            playerData.put("status", player.getHasFolded() ? "folded" : "active");
            playerData.put("isCurrentPlayer", currentPlayer != null && currentPlayer.equals(player));
            playerData.put("isAllIn", player.getIsAllIn());

            // Only show cards to the player themselves (for security)
            if (currentPlayer != null && currentPlayer.equals(player)) {
                playerData.put("cards", player.getHoleCards());
            } else {
                playerData.put("cards", new ArrayList<>()); // Empty for other players
            }
            playersList.add(playerData);
        }
        gameState.put("players", playersList);

        // Broadcast to all players
        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("GAME_STATE_UPDATE", gameId, gameState));

        System.out.println("Broadcasted game state for game: " + gameId + ", current player: " +
                (currentPlayer != null ? currentPlayer.getName() : "none"));
    }

    private Player findPlayer(Game game, String playerId) {
        return game.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
    }
}