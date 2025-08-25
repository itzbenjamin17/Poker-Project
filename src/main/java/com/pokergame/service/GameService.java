package com.pokergame.service;

import com.pokergame.dto.PlayerActionRequest;
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
    private final Map<String, Game> activeGames = new HashMap<>();

    public String createGame(List<String> playerNames) {
        String gameId = UUID.randomUUID().toString();
        List<Player> players = playerNames.stream()
                .map(name -> new Player(name, UUID.randomUUID().toString(), 100))
                .collect(Collectors.toList());

        Game game = new Game(gameId, players, handEvaluator);
        activeGames.put(gameId, game);

        startNewHand(gameId);

        return gameId;
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

        // --- SECURITY CHECK ---
        if (!currentPlayer.getSecretToken().equals(secretToken)) {
            throw new SecurityException("Invalid token. You are not authorized to perform this action.");
        }
        // --- END OF CHECK ---

        PlayerDecision decision = new PlayerDecision(
                actionRequest.action(),
                actionRequest.amount() != null ? actionRequest.amount() : 0,
                currentPlayer.getPlayerId()
        );

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
            game.conductShowdown();
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