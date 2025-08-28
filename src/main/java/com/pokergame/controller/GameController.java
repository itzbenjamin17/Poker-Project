package com.pokergame.controller;

import com.pokergame.dto.CreateRoomRequest;
import com.pokergame.dto.JoinRoomRequest;
import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.model.Room;
import com.pokergame.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {

    @Autowired
    private GameService gameService;

    /**
     * CREATE ROOM (not game yet)
     */
    @PostMapping("/create-room")
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest createRequest) {
        try {
            String roomId = gameService.createRoom(createRequest);

            Map<String, Object> response = Map.of(
                    "roomId", roomId,
                    "hostName", createRequest.getPlayerName(),
                    "message", "Room created successfully");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * JOIN ROOM (not game) - BY ROOM NAME
     */
    @PostMapping("/room/join-by-name")
    public ResponseEntity<?> joinRoomByName(@RequestBody Map<String, String> request) {
        try {
            String roomName = request.get("roomName");
            String playerName = request.get("playerName");
            String password = request.get("password"); // Optional

            if (roomName == null || roomName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Room name is required");
            }
            if (playerName == null || playerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Player name is required");
            }

            String roomId = gameService.joinRoomByName(roomName.trim(), playerName.trim(), password);

            Map<String, Object> response = Map.of(
                    "message", "Successfully joined room",
                    "roomId", roomId,
                    "roomName", roomName,
                    "playerName", playerName);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to join room: " + e.getMessage());
        }
    }

    /**
     * JOIN ROOM (not game) - BY ROOM ID (keep for backwards compatibility)
     */
    @PostMapping("/room/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest joinRequest) {
        try {
            // Validate that the roomId in the path matches the one in the request body
            if (!roomId.equals(joinRequest.roomId())) {
                return ResponseEntity.badRequest().body("Room ID in path does not match request body");
            }

            gameService.joinRoom(joinRequest);

            Map<String, Object> response = Map.of(
                    "message", "Successfully joined room",
                    "roomId", roomId,
                    "playerName", joinRequest.playerName());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET ROOM INFO AND PLAYERS
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getRoomInfo(@PathVariable String roomId) {
        try {
            Map<String, Object> roomData = gameService.getRoomData(roomId);
            if (roomData == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(roomData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to get room info: " + e.getMessage());
        }
    }

    /**
     * START ACTUAL GAME FROM ROOM
     */
    @PostMapping("/room/{roomId}/start-game")
    public ResponseEntity<?> startGameFromRoom(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");

            // Verify player is the host
            if (!gameService.isRoomHost(roomId, playerName)) {
                System.out.println("Not the host");
                return ResponseEntity.status(403).body("Only the room host can start the game");
            }
            System.out.println("Host verified");
            String gameId = gameService.createGameFromRoom(roomId);
            System.out.println("Game ID: " + gameId);

            Map<String, Object> response = Map.of(
                    "gameId", gameId,
                    "message", "Game started successfully");

            System.out.println("Response: " + response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * LEAVE ROOM BY ROOM ID
     */
    @PostMapping("/room/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");

            if (playerName == null || playerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Player name is required");
            }

            gameService.leaveRoom(roomId, playerName.trim());

            Map<String, Object> response = Map.of(
                    "message", "Successfully left room",
                    "playerName", playerName);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to leave room: " + e.getMessage());
        }
    }

    /**
     * LEAVE ROOM BY ROOM NAME
     */
    @PostMapping("/room/leave-by-name")
    public ResponseEntity<?> leaveRoomByName(@RequestBody Map<String, String> request) {
        try {
            String roomName = request.get("roomName");
            String playerName = request.get("playerName");

            if (roomName == null || roomName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Room name is required");
            }
            if (playerName == null || playerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Player name is required");
            }

            gameService.leaveRoomByName(roomName.trim(), playerName.trim());

            Map<String, Object> response = Map.of(
                    "message", "Successfully left room",
                    "roomName", roomName,
                    "playerName", playerName);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to leave room: " + e.getMessage());
        }
    }

    @PostMapping("/{gameId}/action")
    public ResponseEntity<?> performAction(
            @PathVariable String gameId,
            @RequestBody PlayerActionRequest actionRequest) {
        try {
            gameService.processPlayerAction(gameId, actionRequest);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET GAME STATE (for GameRoomPage.js)
     */
    @GetMapping("/{gameId}/state")
    public ResponseEntity<?> getGameState(
            @PathVariable String gameId,
            @RequestParam(required = false) String playerName) {
        try {
            var game = gameService.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }

            System.out.println("Getting game state for game: " + gameId + ", requesting player: " + playerName);

            // Create game state response for frontend
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("gameId", gameId);
            gameState.put("roomName", "Poker Game"); // You might want to get this from Room
            gameState.put("maxPlayers", game.getPlayers().size()); // Adjust as needed
            gameState.put("pot", game.getPot());
            gameState.put("phase", game.getCurrentPhase().toString());
            gameState.put("currentBet", game.getCurrentHighestBet());
            gameState.put("communityCards", game.getCommunityCards());

            // Convert players to frontend format
            List<Map<String, Object>> playersList = new ArrayList<>();
            for (var player : game.getPlayers()) {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("id", player.getPlayerId());
                playerData.put("name", player.getName());
                playerData.put("chips", player.getChips());
                playerData.put("status", player.getHasFolded() ? "folded" : "active");
                playerData.put("isCurrentPlayer", game.getCurrentPlayer().equals(player));

                // Only send cards to the player who owns them, or if no playerName specified
                // (for debugging)
                if (playerName == null || playerName.trim().isEmpty() || player.getName().equals(playerName)) {
                    playerData.put("cards", player.getHoleCards());
                    System.out.println("Sending cards for player: " + player.getName() + ", cards: "
                            + player.getHoleCards().size());
                } else {
                    // Send empty cards for other players to maintain structure
                    playerData.put("cards", new ArrayList<>());
                    System.out.println("Hiding cards for player: " + player.getName());
                }
                playersList.add(playerData);
            }
            gameState.put("players", playersList);

            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to get game state: " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public String test() {
        return "Poker backend is working!";
    }
}