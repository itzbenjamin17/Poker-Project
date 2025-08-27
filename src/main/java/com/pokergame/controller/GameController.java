package com.pokergame.controller;

import com.pokergame.dto.CreateRoomRequest;
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
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");
            String password = request.get("password");

            if (playerName == null || playerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Player name is required");
            }

            gameService.joinRoom(roomId, playerName.trim(), password);

            Map<String, Object> response = Map.of(
                    "message", "Successfully joined room",
                    "roomId", roomId,
                    "playerName", playerName);

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
                return ResponseEntity.status(403).body("Only the room host can start the game");
            }

            String gameId = gameService.createGameFromRoom(roomId);

            Map<String, Object> response = Map.of(
                    "gameId", gameId,
                    "message", "Game started successfully");

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
            @RequestHeader("Authorization") String token,
            @RequestBody PlayerActionRequest actionRequest) {
        try {
            String secretToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            gameService.processPlayerAction(gameId, secretToken, actionRequest);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/test")
    public String test() {
        return "Poker backend is working!";
    }
}