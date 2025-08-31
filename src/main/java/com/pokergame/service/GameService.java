package com.pokergame.service;

import com.pokergame.dto.CreateRoomRequest;
import com.pokergame.dto.JoinRoomRequest;
import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.model.Card;
import com.pokergame.model.Game;
import com.pokergame.model.GamePhase;
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
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomHosts = new ConcurrentHashMap<>();
    @Autowired
    private RoomWebSocketHandler webSocketHandler;

    // Track betting round state for each game
    private final Map<String, Set<String>> playersWhoActedInInitialTurn = new HashMap<>();

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
        System.out.println("=== STARTING NEW HAND ===");
        System.out.println("Game ID: " + gameId);

        if (game.isGameOver()) {
            System.out.println("❌ Game is over, cannot start new hand");
            return;
        }

        System.out.println("✅ Game is not over, proceeding with new hand");
        System.out.println("Resetting game state for new hand...");
        game.resetForNewHand();

        if (game.isGameOver()) {
            System.out.println("❌ Game became over after reset, stopping");
            return;
        }

        System.out.println("Dealing hole cards...");
        game.dealHoleCards();

        System.out.println("Posting blinds...");
        game.postBlinds();

        // Broadcast the initial game state to all players
        System.out.println("Broadcasting new hand state...");
        broadcastGameState(gameId);

        System.out.println("✅ New hand started successfully!");
        System.out.println("  Game: " + gameId);
        System.out.println("  Current player: " + game.getCurrentPlayer().getName());
        System.out.println("  Phase: " + game.getCurrentPhase());
        System.out.println("  Pot: " + game.getPot());
        System.out.println("=== NEW HAND COMPLETE ===");
    }

    public void processPlayerAction(String gameId, PlayerActionRequest actionRequest) {
        Game game = getGame(gameId);
        Player currentPlayer = game.getCurrentPlayer();

        System.out.println("=== PROCESSING PLAYER ACTION ===");
        System.out.println(
                "Game: " + gameId + ", Player: " + currentPlayer.getName() + ", Action: " + actionRequest.action());
        System.out.println("Phase: " + game.getCurrentPhase() + ", Current bet: " + game.getCurrentHighestBet());

        // Verify that the requesting player is the current player
        if (!currentPlayer.getName().equals(actionRequest.playerName())) {
            System.out.println("❌ Player name mismatch: expected " + currentPlayer.getName() + ", got "
                    + actionRequest.playerName());
            throw new SecurityException("It's not your turn. Current player is: " + currentPlayer.getName());
        }

        PlayerDecision decision = new PlayerDecision(
                actionRequest.action(),
                actionRequest.amount() != null ? actionRequest.amount() : 0,
                currentPlayer.getPlayerId());

        System.out.println("Processing decision: " + decision);

        // Process the decision first - this is the critical operation that must succeed
        String conversionMessage = game.processPlayerDecision(currentPlayer, decision);
        System.out.println("Decision processed successfully");

        // If there was a conversion, notify the player
        if (conversionMessage != null) {
            System.out.println("Sending conversion message to player: " + conversionMessage);
            sendPlayerNotification(gameId, currentPlayer.getName(), conversionMessage);
        }

        // After successful processing, handle game progression and broadcasting
        // This is done in a try-catch to ensure that even if broadcasting fails,
        // the action itself was successful
        try {
            // Track who has acted in the initial turn
            Set<String> actedPlayers = playersWhoActedInInitialTurn.computeIfAbsent(gameId, k -> new HashSet<>());
            actedPlayers.add(currentPlayer.getPlayerId());

            // Check if everyone has had their initial turn (Phase 1 of your VB.NET logic)
            List<Player> playersWhoShouldAct = game.getActivePlayers().stream()
                    .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                    .toList();

            boolean everyoneHasActed = playersWhoShouldAct.stream()
                    .allMatch(p -> actedPlayers.contains(p.getPlayerId()));

            if (everyoneHasActed && !game.isBettingRoundComplete()) {
                System.out.println("✅ Everyone has had their initial turn, enabling Phase 2 logic");
                game.setEveryoneHasHadInitialTurn(true);
            }

            // Broadcast game state after player action
            broadcastGameState(gameId);

            System.out.println("Checking if betting round is complete...");
            if (game.isBettingRoundComplete()) {
                System.out.println("✅ Betting round complete, advancing game");
                // Clear the initial turn tracking for next round
                playersWhoActedInInitialTurn.remove(gameId);
                advanceGame(gameId);
            } else {
                System.out.println("❌ Betting round not complete, moving to next player");
                game.nextPlayer();
                // Broadcast again after moving to next player
                broadcastGameState(gameId);
            }
        } catch (Exception e) {
            System.err.println("Error in post-processing (action was successful): " + e.getMessage());
            e.printStackTrace();
            // Re-broadcast to ensure clients have updated state
            try {
                broadcastGameState(gameId);
            } catch (Exception broadcastError) {
                System.err.println("Failed to broadcast after error: " + broadcastError.getMessage());
            }
        }

        System.out.println("=== PLAYER ACTION COMPLETE ===");
    }

    private void sendPlayerNotification(String gameId, String playerName, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PLAYER_NOTIFICATION");
        notification.put("message", message);
        notification.put("playerName", playerName);
        notification.put("gameId", gameId);

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("PLAYER_NOTIFICATION", gameId, notification));
    }

    private void advanceGame(String gameId) {
        Game game = getGame(gameId);
        System.out.println("Advancing game " + gameId + " from phase: " + game.getCurrentPhase());

        if (game.isHandOver()) {
            System.out.println("Hand is over, conducting showdown...");
            int potBeforeDistribution = game.getPot(); // Capture pot before showdown
            List<Player> winners = game.conductShowdown();
            System.out.println("Showdown complete. Winners: " + winners.stream().map(Player::getName).toList());
            // Broadcast showdown results with winner information and actual winnings
            int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
            broadcastShowdownResults(gameId, winners, winningsPerPlayer);

            // Add delay before starting new hand to allow frontend to display results
            System.out.println("Waiting 15 seconds before starting new hand...");
            new Thread(() -> {
                try {
                    Thread.sleep(15000); // 15 second delay
                    System.out.println("Proceeding to cleanup and new hand...");
                    game.cleanupAfterHand();
                    game.advancePositions();
                    System.out.println("Starting new hand...");
                    startNewHand(gameId);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting to start new hand: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }).start();
            return;
        }

        // Check if we need to auto-advance because of all-in situation
        long playersAbleToAct = game.getActivePlayers().stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .count();

        System.out.println("Players able to act: " + playersAbleToAct + ", Betting round complete: "
                + game.isBettingRoundComplete());

        // Auto-advance if betting round is complete AND most players are all-in
        if (game.isBettingRoundComplete() && playersAbleToAct <= 1) {
            System.out.println("All-in situation detected. Auto-advancing to showdown slowly.");
            // Send auto-advancing notification to frontend
            broadcastAutoAdvanceNotification(gameId);
            autoAdvanceToShowdown(gameId);
            return; // Use the dedicated slow-advance method
        }

        // Normal advancement logic
        switch (game.getCurrentPhase()) {
            case PRE_FLOP:
                System.out.println("Moving to FLOP phase");
                game.dealFlop();
                broadcastGameState(gameId);
                break;
            case FLOP:
                System.out.println("Moving to TURN phase");
                game.dealTurn();
                broadcastGameState(gameId);
                break;
            case TURN:
                System.out.println("Moving to RIVER phase");
                game.dealRiver();
                broadcastGameState(gameId);
                break;
            case RIVER:
                System.out.println("RIVER betting complete, conducting showdown...");
                int potBeforeDistribution = game.getPot(); // Capture pot before showdown
                List<Player> winners = game.conductShowdown();
                System.out.println("Showdown complete. Winners: " + winners.stream().map(Player::getName).toList());
                // Broadcast showdown results with winner information and actual winnings
                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                broadcastShowdownResults(gameId, winners, winningsPerPlayer);

                // Add delay before starting new hand to allow frontend to display results
                System.out.println("Waiting 15 seconds before starting new hand...");
                new Thread(() -> {
                    try {
                        Thread.sleep(15000); // 15 second delay
                        System.out.println("Proceeding to cleanup and new hand...");
                        game.cleanupAfterHand();
                        game.advancePositions();
                        System.out.println("Starting new hand after river...");
                        startNewHand(gameId);
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting to start new hand: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }).start();
                break;
            case SHOWDOWN:
                System.out.println("Already in showdown phase");
                // Handle showdown phase
                break;
        }
    }

    private void autoAdvanceToShowdown(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        System.out.println("Starting auto-advance to showdown for game: " + gameId);

        // Broadcast initial auto-advancing state
        broadcastGameStateWithAutoAdvance(gameId, true, "All players are all-in. Auto-advancing to showdown...");

        new Thread(() -> {
            try {
                // Deal remaining cards with delays
                if (game.getCurrentPhase() == GamePhase.PRE_FLOP) {
                    Thread.sleep(3000);
                    System.out.println("Auto-advancing: Dealing FLOP");
                    game.dealFlop();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the flop...");
                }
                if (game.getCurrentPhase() == GamePhase.FLOP) {
                    Thread.sleep(3000);
                    System.out.println("Auto-advancing: Dealing TURN");
                    game.dealTurn();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the turn...");
                }
                if (game.getCurrentPhase() == GamePhase.TURN) {
                    Thread.sleep(3000);
                    System.out.println("Auto-advancing: Dealing RIVER");
                    game.dealRiver();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the river...");
                }

                // All cards are dealt, proceed to showdown
                Thread.sleep(2000);
                System.out.println("Auto-advancing: Conducting SHOWDOWN");
                broadcastGameStateWithAutoAdvance(gameId, true, "Revealing hands...");
                Thread.sleep(2000); // Give more time for frontend to process

                System.out.println("Auto-advancing: Executing showdown logic");
                int potBeforeDistribution = game.getPot();
                List<Player> winners = game.conductShowdown();
                System.out.println("Auto-advancing: Showdown complete, winners: "
                        + winners.stream().map(Player::getName).toList());

                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                System.out.println("Auto-advancing: Broadcasting showdown results");
                broadcastShowdownResults(gameId, winners, winningsPerPlayer);

                System.out.println("Auto-advancing: Showdown results broadcast complete");

                // Turn off auto-advance mode after showdown
                Thread.sleep(1000);
                broadcastGameStateWithAutoAdvance(gameId, false, "");
                System.out.println("Auto-advancing: Auto-advance mode disabled");

                // Wait before starting the next hand
                Thread.sleep(15000);
                System.out.println("Auto-advancing: Cleaning up and starting new hand");
                game.cleanupAfterHand();
                game.advancePositions();
                startNewHand(gameId);

            } catch (InterruptedException e) {
                System.err.println("Auto-advance thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void broadcastAutoAdvanceNotification(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "AUTO_ADVANCE_START");
        notification.put("message", "All players are all-in. Auto-advancing to showdown...");
        notification.put("gameId", gameId);

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("AUTO_ADVANCE_NOTIFICATION", gameId, notification));
    }

    private void broadcastGameStateWithAutoAdvance(String gameId, boolean isAutoAdvancing, String message) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        // Create normal game state
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", gameId);
        gameState.put("pot", game.getPot());
        gameState.put("phase", game.getCurrentPhase().toString());
        gameState.put("currentBet", game.getCurrentHighestBet());
        gameState.put("communityCards", game.getCommunityCards());
        gameState.put("isAutoAdvancing", isAutoAdvancing);
        gameState.put("autoAdvanceMessage", message);

        // Add current player information
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != null) {
            gameState.put("currentPlayerName", currentPlayer.getName());
            gameState.put("currentPlayerId", currentPlayer.getPlayerId());
        }

        // Convert players to frontend format
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (var player : game.getPlayers()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("id", player.getPlayerId());
            playerData.put("name", player.getName());
            playerData.put("chips", player.getChips());
            playerData.put("currentBet", player.getCurrentBet());
            playerData.put("status", player.getHasFolded() ? "folded" : (player.getIsAllIn() ? "all-in" : "active"));
            playerData.put("isCurrentPlayer", currentPlayer != null && currentPlayer.equals(player));
            playerData.put("isAllIn", player.getIsAllIn());
            playerData.put("hasFolded", player.getHasFolded());

            // Add hand rank during showdown
            if (game.getCurrentPhase() == GamePhase.SHOWDOWN && !player.getHasFolded()) {
                playerData.put("handRank", player.getHandRank() != null ? player.getHandRank().toString() : "UNKNOWN");
            }

            // Show cards based on game phase
            if (game.getCurrentPhase() == GamePhase.SHOWDOWN) {
                if (!player.getHasFolded()) {
                    List<Card> bestHand = player.getBestHand();
                    playerData.put("cards", bestHand != null ? bestHand : new ArrayList<>());
                } else {
                    playerData.put("cards", new ArrayList<>());
                }
            } else {
                if (currentPlayer != null && currentPlayer.equals(player)) {
                    playerData.put("cards", player.getHoleCards());
                } else {
                    playerData.put("cards", new ArrayList<>());
                }
            }
            playersList.add(playerData);
        }
        gameState.put("players", playersList);

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("GAME_STATE_UPDATE", gameId, gameState));
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
            playerData.put("status", player.getHasFolded() ? "folded" : (player.getIsAllIn() ? "all-in" : "active"));
            playerData.put("isCurrentPlayer", currentPlayer != null && currentPlayer.equals(player));
            playerData.put("isAllIn", player.getIsAllIn());
            playerData.put("hasFolded", player.getHasFolded());

            // Add hand rank during showdown
            if (game.getCurrentPhase() == GamePhase.SHOWDOWN && !player.getHasFolded()) {
                playerData.put("handRank", player.getHandRank() != null ? player.getHandRank().toString() : "UNKNOWN");
            }

            // Show cards based on game phase
            if (game.getCurrentPhase() == GamePhase.SHOWDOWN) {
                // During showdown, show all non-folded players' best hands to everyone
                if (!player.getHasFolded()) {
                    List<Card> bestHand = player.getBestHand();
                    playerData.put("cards", bestHand != null ? bestHand : new ArrayList<>());
                    System.out.println("Sending best hand for " + player.getName() + ": " +
                            (bestHand != null ? bestHand.size() + " cards" : "null"));
                } else {
                    playerData.put("cards", new ArrayList<>());
                }
            } else {
                // Only show cards to the player themselves (for security)
                if (currentPlayer != null && currentPlayer.equals(player)) {
                    playerData.put("cards", player.getHoleCards());
                } else {
                    playerData.put("cards", new ArrayList<>()); // Empty for other players
                }
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

    /**
     * Broadcast showdown results with winner information to all players
     */
    public void broadcastShowdownResults(String gameId, List<Player> winners, int winningsPerPlayer) {
        Game game = getGame(gameId);
        if (game == null) {
            return;
        }

        // Create showdown-specific game state data
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", gameId);
        gameState.put("pot", game.getPot());
        gameState.put("phase", game.getCurrentPhase().toString());
        gameState.put("currentBet", game.getCurrentHighestBet());
        gameState.put("communityCards", game.getCommunityCards());

        // Add winner information with actual winnings
        List<String> winnerNames = winners.stream().map(Player::getName).toList();
        gameState.put("winners", winnerNames);
        gameState.put("winnerCount", winners.size());
        gameState.put("winningsPerPlayer", winningsPerPlayer); // Add actual winnings amount

        // Add current player information
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != null) {
            gameState.put("currentPlayerName", currentPlayer.getName());
            gameState.put("currentPlayerId", currentPlayer.getPlayerId());
        }

        // Convert players to frontend format for showdown
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (var player : game.getPlayers()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("id", player.getPlayerId());
            playerData.put("name", player.getName());
            playerData.put("chips", player.getChips());
            playerData.put("currentBet", player.getCurrentBet());
            playerData.put("status", player.getHasFolded() ? "folded" : (player.getIsAllIn() ? "all-in" : "active"));
            playerData.put("isCurrentPlayer", currentPlayer != null && currentPlayer.equals(player));
            playerData.put("isAllIn", player.getIsAllIn());
            playerData.put("hasFolded", player.getHasFolded());
            playerData.put("isWinner", winnerNames.contains(player.getName()));

            // Add winnings information only for winners
            if (winnerNames.contains(player.getName())) {
                playerData.put("chipsWon", winningsPerPlayer);
            } else {
                playerData.put("chipsWon", 0);
            }

            // Add hand rank and best hand during showdown
            if (!player.getHasFolded()) {
                playerData.put("handRank", player.getHandRank() != null ? player.getHandRank().toString() : "UNKNOWN");
                // Send best hand instead of hole cards for showdown
                List<Card> bestHand = player.getBestHand();
                playerData.put("cards", bestHand != null ? bestHand : new ArrayList<>());
                playerData.put("bestHand", bestHand != null ? bestHand : new ArrayList<>());
                System.out.println("SHOWDOWN RESULTS: Sending best hand for " + player.getName() + ": " +
                        (bestHand != null ? bestHand.size() + " cards - " + bestHand : "null"));
            } else {
                playerData.put("cards", new ArrayList<>());
            }

            playersList.add(playerData);
        }
        gameState.put("players", playersList);

        // Broadcast showdown results to all players
        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("SHOWDOWN_RESULTS", gameId, gameState));

        System.out.println("Broadcasted showdown results for game: " + gameId + ", winners: " + winnerNames);
        System.out.println("Showdown gameState contains winners: " + gameState.get("winners"));
        System.out.println("Showdown gameState contains winnerCount: " + gameState.get("winnerCount"));
        System.out.println("Actual winnings per player: " + winningsPerPlayer);
    }

}