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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service class that manages poker game logic, room management, and WebSocket
 * communications.
 * Handles game lifecycle from room creation to game completion, including
 * player actions,
 * betting rounds, and showdown resolution.
 */
@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    @Autowired
    private HandEvaluatorService handEvaluator;

    @Autowired
    private RoomWebSocketHandler webSocketHandler;

    // Game state storage
    private final Map<String, Game> activeGames = new HashMap<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomHosts = new ConcurrentHashMap<>();

    // Betting round state tracking
    private final Map<String, Set<String>> playersWhoActedInInitialTurn = new HashMap<>();

    /**
     * Creates a new poker room with the specified configuration.
     * 
     * @param request The room creation request containing room name, host, and game
     *                settings
     * @return The unique room ID for the created room
     * @throws IllegalArgumentException if room name is already taken
     */
    public String createRoom(CreateRoomRequest request) {
        if (isRoomNameTaken(request.getRoomName())) {
            throw new IllegalArgumentException(
                    "Room name '" + request.getRoomName() + "' is already taken. Please choose a different name.");
        }

        String roomId = UUID.randomUUID().toString();

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

        logger.info("Game created and started for room: {} with {} players", roomId, players.size());

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

    /**
     * Remove player from active game
     */
    public void leaveGame(String gameId, String playerName) {
        Game game = getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Find and remove the player from the game
        Player playerToRemove = game.getPlayers().stream()
                .filter(p -> p.getName().equals(playerName))
                .findFirst()
                .orElse(null);

        if (playerToRemove == null) {
            throw new IllegalArgumentException("Player not found in game");
        }

        // Check if the leaving player was the current player
        boolean wasCurrentPlayer = game.getCurrentPlayer() != null && game.getCurrentPlayer().equals(playerToRemove);

        // Remove player from both lists
        game.getPlayers().remove(playerToRemove);
        game.getActivePlayers().remove(playerToRemove);

        logger.info("Player {} left game {} | Remaining players: {}",
                playerName, gameId, game.getPlayers().size());

        // Check if no players left in the game
        if (game.getPlayers().isEmpty()) {
            logger.info("All players left game {}, destroying game and room", gameId);

            // Notify any remaining WebSocket connections that the room is closed
            webSocketHandler.broadcastToRoom(gameId,
                    new WebSocketMessage("ROOM_CLOSED", gameId, Map.of("reason", "All players left the game")));

            // Clean up game and room data
            activeGames.remove(gameId);
            Room room = rooms.remove(gameId);
            if (room != null) {
                roomHosts.remove(gameId);
                logger.info("Room {} has been destroyed - all players left", room.getRoomName());
            }
        } else {
            logger.info("Game {} continues with {} players", gameId, game.getPlayers().size());

            // If only one player remains, end the game immediately
            if (game.getPlayers().size() == 1) {
                logger.info("Only one player remaining in game {}, ending game", gameId);
                handleGameEnd(gameId);
                return;
            }

            // If the leaving player was the current player, advance to next player
            if (wasCurrentPlayer && !game.getActivePlayers().isEmpty()) {
                game.nextPlayer();
            }

            broadcastGameState(gameId);
        }
    }

    /**
     * Starts a new hand of poker for the specified game.
     * Resets game state, deals cards, posts blinds, and broadcasts initial state.
     * 
     * @param gameId The unique identifier of the game
     */
    public void startNewHand(String gameId) {
        Game game = getGame(gameId);
        logger.info("Starting new hand for game: {}", gameId);

        if (game.isGameOver()) {
            logger.warn("Cannot start new hand - game {} is over", gameId);
            return;
        }

        game.resetForNewHand();

        if (game.isGameOver()) {
            logger.warn("Game {} became over after reset", gameId);
            return;
        }

        game.dealHoleCards();
        game.postBlinds();
        broadcastGameState(gameId);

        logger.info("New hand started successfully for game: {} | Current player: {} | Phase: {} | Pot: {}",
                gameId, game.getCurrentPlayer().getName(), game.getCurrentPhase(), game.getPot());
    }

    /**
     * Processes a player action request and advances the game state accordingly.
     * Validates the request, processes the decision, and handles game progression.
     * 
     * @param gameId        the unique identifier of the game
     * @param actionRequest the action request containing player name, action type,
     *                      and amount
     * @throws SecurityException if the requesting player is not the current player
     */
    public void processPlayerAction(String gameId, PlayerActionRequest actionRequest) {
        Game game = getGame(gameId);
        Player currentPlayer = game.getCurrentPlayer();

        logger.debug("Processing player action - Game: {}, Player: {}, Action: {}",
                gameId, currentPlayer.getName(), actionRequest.action());
        logger.debug("Game state - Phase: {}, Current bet: {}",
                game.getCurrentPhase(), game.getCurrentHighestBet());

        // Verify that the requesting player is the current player
        if (!currentPlayer.getName().equals(actionRequest.playerName())) {
            logger.warn("Player name mismatch: expected {}, got {}",
                    currentPlayer.getName(), actionRequest.playerName());
            throw new SecurityException("It's not your turn. Current player is: " + currentPlayer.getName());
        }

        PlayerDecision decision = new PlayerDecision(
                actionRequest.action(),
                actionRequest.amount() != null ? actionRequest.amount() : 0,
                currentPlayer.getPlayerId());

        logger.debug("Processing decision: {}", decision);

        // Process the decision first - this is the critical operation that must succeed
        String conversionMessage = game.processPlayerDecision(currentPlayer, decision);
        logger.debug("Decision processed successfully");

        // If there was a conversion, notify the player
        if (conversionMessage != null) {
            logger.info("Sending conversion message to player {}: {}", currentPlayer.getName(), conversionMessage);
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
                logger.debug("Everyone has had their initial turn for game {}, enabling Phase 2 logic", gameId);
                game.setEveryoneHasHadInitialTurn(true);
            }

            // Broadcast game state after player action
            broadcastGameState(gameId);

            logger.debug("Checking if betting round is complete for game {}...", gameId);
            if (game.isBettingRoundComplete()) {
                logger.debug("Betting round complete for game {}, advancing game", gameId);
                // Clear the initial turn tracking for next round
                playersWhoActedInInitialTurn.remove(gameId);
                advanceGame(gameId);
            } else {
                logger.debug("Betting round not complete for game {}, moving to next player", gameId);
                game.nextPlayer();
                // Broadcast again after moving to next player
                broadcastGameState(gameId);
            }
        } catch (Exception e) {
            logger.error("Error in post-processing for game {} (action was successful): {}", gameId, e.getMessage(), e);
            // Re-broadcast to ensure clients have updated state
            try {
                broadcastGameState(gameId);
            } catch (Exception broadcastError) {
                logger.error("Failed to broadcast after error for game {}: {}", gameId, broadcastError.getMessage());
            }
        }

        logger.debug("Player action processing complete for game {}", gameId);
    }

    /**
     * Sends a notification message to a specific player in the game.
     * 
     * @param gameId     the unique identifier of the game
     * @param playerName the name of the player to notify
     * @param message    the notification message to send
     */
    private void sendPlayerNotification(String gameId, String playerName, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PLAYER_NOTIFICATION");
        notification.put("message", message);
        notification.put("playerName", playerName);
        notification.put("gameId", gameId);

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("PLAYER_NOTIFICATION", gameId, notification));
    }

    /**
     * Handles the end of a game when all players except one have been eliminated.
     * Broadcasts the game end event to all participants and cleans up game
     * resources.
     * 
     * @param gameId the unique identifier of the game
     */
    private void handleGameEnd(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        // Find the winner (last remaining player)
        Player winner = game.getActivePlayers().stream()
                .findFirst()
                .orElse(null);

        if (winner == null) {
            // Edge case: no active players (should not happen)
            winner = game.getPlayers().stream()
                    .filter(p -> !p.getIsOut())
                    .findFirst()
                    .orElse(game.getPlayers().get(0)); // Fallback to first player
        }

        logger.info("Game {} completed - Winner: {} with {} chips",
                gameId, winner.getName(), winner.getChips());

        // Broadcast game end message
        Map<String, Object> gameEndData = new HashMap<>();
        gameEndData.put("type", "GAME_END");
        gameEndData.put("winner", winner.getName());
        gameEndData.put("winnerChips", winner.getChips());
        gameEndData.put("gameId", gameId);
        gameEndData.put("message", "🏆 " + winner.getName() + " wins the game with " + winner.getChips() + " chips!");

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("GAME_END", gameId, gameEndData));

        // Wait a few seconds for players to see the result, then destroy the room
        new Thread(() -> {
            try {
                Thread.sleep(10000); // 10 second delay to show winner
                logger.info("Destroying room after game completion: {}", gameId);

                // Notify players that room is being destroyed
                webSocketHandler.broadcastToRoom(gameId,
                        new WebSocketMessage("ROOM_CLOSED", gameId, Map.of("reason", "Game completed")));

                // Clean up game and room data
                activeGames.remove(gameId);
                Room room = rooms.remove(gameId);
                if (room != null) {
                    roomHosts.remove(gameId);
                    logger.info("Room {} has been destroyed after game completion", room.getRoomName());
                }

            } catch (InterruptedException e) {
                logger.warn("Interrupted while cleaning up game {}: {}", gameId, e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Advances the game to the next phase or conducts showdown if hand is over.
     * Handles progression through betting rounds and manages game state
     * transitions.
     * 
     * @param gameId The unique identifier of the game to advance
     */
    private void advanceGame(String gameId) {
        Game game = getGame(gameId);
        logger.info("Advancing game {} from phase: {}", gameId, game.getCurrentPhase());

        if (game.isHandOver()) {
            logger.info("Hand is over for game {}, conducting showdown", gameId);
            int potBeforeDistribution = game.getPot();
            List<Player> winners = game.conductShowdown();
            logger.info("Showdown complete for game {} | Winners: {}",
                    gameId, winners.stream().map(Player::getName).toList());

            int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
            broadcastShowdownResults(gameId, winners, winningsPerPlayer);

            // Delay before starting new hand to allow winner display
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    game.cleanupAfterHand();

                    if (game.isGameOver()) {
                        logger.info("Game {} is over, handling end of game", gameId);
                        handleGameEnd(gameId);
                        return;
                    }

                    game.advancePositions();
                    startNewHand(gameId);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting to start new hand for game {}: {}", gameId, e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }).start();
            return;
        }

        // Check if we need to auto-advance because of all-in situation
        long playersAbleToAct = game.getActivePlayers().stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .count();

        logger.debug("Game {} status | Players able to act: {} | Betting round complete: {}",
                gameId, playersAbleToAct, game.isBettingRoundComplete());

        // Auto-advance if betting round is complete AND most players are all-in
        if (game.isBettingRoundComplete() && playersAbleToAct <= 1) {
            logger.info("All-in situation detected for game {}, auto-advancing to showdown", gameId);
            broadcastAutoAdvanceNotification(gameId);
            autoAdvanceToShowdown(gameId);
            return;
        }

        // Normal advancement logic
        switch (game.getCurrentPhase()) {
            case PRE_FLOP:
                logger.info("Game {} advancing to FLOP phase", gameId);
                game.dealFlop();
                broadcastGameState(gameId);
                break;
            case FLOP:
                logger.info("Game {} advancing to TURN phase", gameId);
                game.dealTurn();
                broadcastGameState(gameId);
                break;
            case TURN:
                logger.info("Game {} advancing to RIVER phase", gameId);
                game.dealRiver();
                broadcastGameState(gameId);
                break;
            case RIVER:
                logger.info("RIVER betting complete for game {}, conducting showdown", gameId);
                int potBeforeDistribution = game.getPot();
                List<Player> winners = game.conductShowdown();
                logger.info("Showdown complete for game {} | Winners: {}",
                        gameId, winners.stream().map(Player::getName).toList());

                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                broadcastShowdownResults(gameId, winners, winningsPerPlayer);

                // Delay before starting new hand to allow winner display
                new Thread(() -> {
                    try {
                        Thread.sleep(10000);
                        game.cleanupAfterHand();

                        if (game.isGameOver()) {
                            logger.info("Game {} is over after river, handling end of game", gameId);
                            handleGameEnd(gameId);
                            return;
                        }

                        game.advancePositions();
                        startNewHand(gameId);
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while waiting to start new hand for game {}: {}", gameId,
                                e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }).start();
                break;
            case SHOWDOWN:
                logger.warn("Game {} already in showdown phase", gameId);
                break;
        }
    }

    /**
     * Automatically advances the game to showdown when all active players are
     * all-in.
     * Deals remaining community cards with appropriate timing and conducts
     * showdown.
     * 
     * @param gameId The unique identifier of the game to advance
     */
    private void autoAdvanceToShowdown(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        logger.info("Starting auto-advance to showdown for game: {}", gameId);
        broadcastGameStateWithAutoAdvance(gameId, true, "All players are all-in. Auto-advancing to showdown...");

        new Thread(() -> {
            try {
                // Deal remaining cards with delays for dramatic effect
                if (game.getCurrentPhase() == GamePhase.PRE_FLOP) {
                    Thread.sleep(3000);
                    game.dealFlop();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the flop...");
                }
                if (game.getCurrentPhase() == GamePhase.FLOP) {
                    Thread.sleep(3000);
                    game.dealTurn();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the turn...");
                }
                if (game.getCurrentPhase() == GamePhase.TURN) {
                    Thread.sleep(3000);
                    game.dealRiver();
                    broadcastGameStateWithAutoAdvance(gameId, true, "Dealing the river...");
                }

                // Conduct showdown
                Thread.sleep(2000);
                broadcastGameStateWithAutoAdvance(gameId, true, "Revealing hands...");
                Thread.sleep(2000);

                int potBeforeDistribution = game.getPot();
                List<Player> winners = game.conductShowdown();
                logger.info("Auto-advance showdown complete for game: {} | Winners: {}",
                        gameId, winners.stream().map(Player::getName).toList());

                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                broadcastShowdownResults(gameId, winners, winningsPerPlayer);

                // Turn off auto-advance state
                broadcastAutoAdvanceComplete(gameId);

                // Wait to let winner display show properly before starting the next hand
                Thread.sleep(10000);
                game.cleanupAfterHand();

                // Check if game is over after cleanup
                if (game.isGameOver()) {
                    logger.info("Game {} is over after auto-advance, handling end of game", gameId);
                    handleGameEnd(gameId);
                    return;
                }

                game.advancePositions();
                startNewHand(gameId);

            } catch (InterruptedException e) {
                logger.warn("Auto-advance thread interrupted for game {}: {}", gameId, e.getMessage());
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

    private void broadcastAutoAdvanceComplete(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "AUTO_ADVANCE_COMPLETE");
        notification.put("message", "");
        notification.put("gameId", gameId);

        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("AUTO_ADVANCE_COMPLETE", gameId, notification));
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

        // Send personalized game state to each player
        for (var targetPlayer : game.getPlayers()) {
            broadcastGameStateToPlayer(gameId, targetPlayer.getName());
        }
    }

    /**
     * Broadcast personalized game state to a specific player
     */
    public void broadcastGameStateToPlayer(String gameId, String playerName) {
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

        // Convert players to frontend format, showing cards only to the target player
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

            // Show cards based on game phase and target player
            if (game.getCurrentPhase() == GamePhase.SHOWDOWN) {
                // During showdown, show all non-folded players' best hands to everyone
                if (!player.getHasFolded()) {
                    List<Card> bestHand = player.getBestHand();
                    playerData.put("cards", bestHand != null ? bestHand : new ArrayList<>());
                } else {
                    playerData.put("cards", new ArrayList<>());
                }
            } else {
                // Only show cards to the player themselves (for security)
                if (player.getName().equals(playerName)) {
                    playerData.put("cards", player.getHoleCards());
                } else {
                    playerData.put("cards", new ArrayList<>()); // Empty for other players
                }
            }
            playersList.add(playerData);
        }
        gameState.put("players", playersList);

        // Send to specific player
        webSocketHandler.sendToPlayer(gameId, playerName,
                new WebSocketMessage("GAME_STATE_UPDATE", gameId, gameState));

        logger.debug("Broadcasted game state for game {} to player {}", gameId, playerName);
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
                logger.debug("Showdown results: Sending best hand for {} with {} cards",
                        player.getName(), bestHand != null ? bestHand.size() : 0);
            } else {
                playerData.put("cards", new ArrayList<>());
            }

            playersList.add(playerData);
        }
        gameState.put("players", playersList);

        // Broadcast showdown results to all players
        webSocketHandler.broadcastToRoom(gameId,
                new WebSocketMessage("SHOWDOWN_RESULTS", gameId, gameState));

        logger.info("Broadcasted showdown results for game {} with {} winner(s): {}",
                gameId, winnerNames.size(), winnerNames);
        logger.debug("Showdown game state - winners: {}, winnerCount: {}, winnings per player: {}",
                gameState.get("winners"), gameState.get("winnerCount"), winningsPerPlayer);
    }

}