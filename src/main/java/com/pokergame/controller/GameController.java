package com.pokergame.controller;

import com.pokergame.dto.CreateGameRequest;
import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Make sure to import List

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {

    @Autowired
    private GameService gameService;

    /**
     * NEW ENDPOINT for creating a game with custom settings.
     */
    @PostMapping("/create")
    public ResponseEntity<String> createGame(@RequestBody CreateGameRequest createRequest) {
        try {
            // In a real application, you would get player names from your session,
            // user accounts, or the request itself. For now, we'll create placeholders.
            List<String> playerNames = List.of("Player 1", "Player 2", "Player 3");
            String gameId = gameService.createGame(playerNames, createRequest);
            return ResponseEntity.ok(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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