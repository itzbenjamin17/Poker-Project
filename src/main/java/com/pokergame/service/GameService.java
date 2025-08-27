package com.pokergame.service;

import com.pokergame.dto.CreateGameRequest;
import com.pokergame.dto.CreateRoomRequest;
import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.model.Room;
import com.pokergame.dto.PlayerDecision;
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

        return roomId;
    }

    /**
     * Join a room (NOT a game)
     */
    public void joinRoom(String roomId, String playerName, String password) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        // Check password if room is private
        if (room.hasPassword() && !room.checkPassword(password)) {
            throw new IllegalArgumentException("Invalid password");
        }

        // Check if room is full
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("Room is full");
        }

        // Check if player name already exists
        if (room.hasPlayer(playerName)) {
            throw new IllegalArgumentException("Player name already taken");
        }

        room.addPlayer(playerName);
    }

    /**
     * Get room information
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
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

        // Start the first hand
        startNewHand(roomId);

        // Remove room since game has started
        rooms.remove(roomId);
        roomHosts.remove(roomId);

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
        joinRoom(roomId, playerName, password);
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
            // Host is leaving - destroy the entire room
            destroyRoom(roomId);
        } else {
            // Regular player leaving - just remove them from the room
            room.removePlayer(playerName);

            // If no players left after removal, also destroy the room
            if (room.getPlayers().isEmpty()) {
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

        conductBettingRound(gameId);
    }

    public void processPlayerAction(String gameId, String secretToken, PlayerActionRequest actionRequest) {
        Game game = getGame(gameId);
        Player currentPlayer = game.getCurrentPlayer();

        if (!currentPlayer.getSecretToken().equals(secretToken)) {
            throw new SecurityException("Invalid token. You are not authorized to perform this action.");
        }

        PlayerDecision decision = new PlayerDecision(
                actionRequest.action(),
                actionRequest.amount() != null ? actionRequest.amount() : 0,
                currentPlayer.getPlayerId());

        game.processPlayerDecision(currentPlayer, decision);

        if (game.isBettingRoundComplete()) {
            advanceGame(gameId);
        } else {
            game.nextPlayer();
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
            // broadcast winners
            game.cleanupAfterHand();
            game.advancePositions();
            startNewHand(gameId);
            return;
        }

        switch (game.getCurrentPhase()) {
            case PRE_FLOP:
                game.dealFlop();
                conductBettingRound(gameId);
                break;
            case FLOP:
                game.dealTurn();
                conductBettingRound(gameId);
                break;
            case TURN:
                game.dealRiver();
                conductBettingRound(gameId);
                break;
            case RIVER:
                game.conductShowdown();
                List<Player> winners = game.conductShowdown();
                // broadcast winners
                game.cleanupAfterHand();
                game.advancePositions();
                startNewHand(gameId);
                break;
        }
    }

    public Game getGame(String gameId) {
        return activeGames.get(gameId);
    }

    private Player findPlayer(Game game, String playerId) {
        return game.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
    }
}