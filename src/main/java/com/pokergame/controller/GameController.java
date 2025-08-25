package com.pokergame.controller;

import com.pokergame.dto.PlayerActionRequest;
import com.pokergame.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {

    @Autowired
    private GameService gameService;

    @PostMapping("/{gameId}/action")
    public ResponseEntity<?> performAction(
            @PathVariable String gameId,
            @RequestHeader("Authorization") String token,
            @RequestBody PlayerActionRequest actionRequest) {
        try {
            // Assumes token is sent as "Bearer <token>"
            String secretToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            gameService.processPlayerAction(gameId, secretToken, actionRequest);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage()); // 403 Forbidden for security errors
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/test")
    public String test() {
        return "Poker backend is working!";
    }
}