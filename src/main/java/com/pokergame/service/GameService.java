package com.pokergame.service;

import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.dto.PlayerDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private HandEvaluatorService handEvaluator;
    private Map<String, Game> activeGames = new HashMap<>();

    // Service-level operations - THESE BELONG HERE
    public String createGame(List<String> playerNames) {
        String gameId = UUID.randomUUID().toString();
        List<Player> players = playerNames.stream()
                .map(name -> new Player(name, UUID.randomUUID().toString(), 100))
                .collect(Collectors.toList());

        Game game = new Game(gameId, players, handEvaluator);  // Inject service
        activeGames.put(gameId, game);

        return gameId;
    }
    //TODO Fix this as it should correspond to process player decision
    public void processPlayerAction(PlayerDecision decision, String gameId) {
        Game game = getGame(gameId);
        Player player = findPlayer(game, decision.playerId());

        // Validate the decision
        validateDecision(player, game, decision);

        game.processPlayerDecision(player, decision);

        // Handle external concerns
        broadcastGameState(game);  // WebSocket broadcasting
        saveGame(game);           // Persistence
    }

    public Game getGame(String gameId) {
        return activeGames.get(gameId);
    }

    // External concerns
    private void broadcastGameState(Game game) { ... }
    private void saveGame(Game game) { ... }
}

