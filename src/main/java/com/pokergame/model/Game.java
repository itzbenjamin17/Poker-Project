package com.pokergame.model;

import com.pokergame.dto.PlayerDecision;
import com.pokergame.service.HandEvaluatorService;

import java.util.*;

public class Game {

    // Constants
    private static final int SMALL_BLIND = 1;
    private static final int BIG_BLIND = 2;

    private final String gameId;
    private final List<Player> players;
    private final List<Player> activePlayers;
    private Deck deck;
    private final List<Card> communityCards;
    private int pot;
    private int dealerPosition;
    private int smallBlindPosition;
    private int bigBlindPosition;
    private int currentPlayerPosition;
    private int currentHighestBet;
    private GamePhase currentPhase;
    private boolean gameOver;
    private final HandEvaluatorService handEvaluator;

    public Game(String gameId, List<Player> players, HandEvaluatorService handEvaluator) {
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required to start a game");
        }
        if (players.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Player list cannot contain null elements");
        }
        this.gameId = gameId;
        this.players = new ArrayList<>(players);
        this.activePlayers = new ArrayList<>(players);
        this.deck = new Deck();
        this.communityCards = new ArrayList<>();
        this.pot = 0;
        this.dealerPosition = 0;
        this.smallBlindPosition = 1 % players.size();
        this.bigBlindPosition = 2 % players.size();
        this.currentPlayerPosition = 3 % players.size();
        this.currentHighestBet = 0;
        this.currentPhase = GamePhase.PRE_FLOP;
        this.gameOver = false;
        this.handEvaluator = handEvaluator;
    }

    public void resetForNewHand() {
        deck = new Deck();
        communityCards.clear();
        pot = 0;
        currentHighestBet = 0;
        currentPhase = GamePhase.PRE_FLOP;

        activePlayers.clear();
        for (Player player : players) {
            if (!player.getIsOut()) {
                player.resetAttributes();
                activePlayers.add(player);
            }
        }

        if (activePlayers.size() <= 1) {
            gameOver = true;
        }
    }

    public void dealHoleCards() {
        for (Player player : activePlayers) {
            deck.dealCards(player.getHoleCards(), 2);
        }
    }

    public void postBlinds() {
        if (activePlayers.size() >= 2) {
            Player smallBlindPlayer = activePlayers.get(smallBlindPosition);
            Player bigBlindPlayer = activePlayers.get(bigBlindPosition);

            this.pot = smallBlindPlayer.doAction(PlayerAction.BET, SMALL_BLIND, this.pot);
            this.pot = bigBlindPlayer.doAction(PlayerAction.BET, BIG_BLIND, this.pot);
            currentHighestBet = BIG_BLIND;
        }
    }

    public void processPlayerDecision(Player player, PlayerDecision decision) {
        switch (decision.action()) {
            case FOLD, CHECK -> player.doAction(decision.action(), 0, this.pot);
            case CALL, BET, RAISE -> {
                int amount = calculateActualAmount(player, decision);
                this.pot = player.doAction(decision.action(), amount, this.pot);
                if (player.getCurrentBet() > currentHighestBet) {
                    currentHighestBet = player.getCurrentBet();
                }
            }
            case ALL_IN -> this.pot = player.doAction(PlayerAction.ALL_IN, 0, this.pot);
        }
    }

    private int calculateActualAmount(Player player, PlayerDecision decision) {
        return switch (decision.action()) {
            case CALL -> currentHighestBet - player.getCurrentBet();
            case BET, RAISE -> decision.amount();
            default -> 0;
        };
    }

    public boolean isBettingRoundComplete() {
        // All active players have either folded, are all-in, or have matched the current highest bet.
        return activePlayers.stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .allMatch(p -> p.getCurrentBet() == currentHighestBet);
    }

    public void dealFlop() {
        deck.dealCards(communityCards, 3);
        currentPhase = GamePhase.FLOP;
        resetBetsForRound();
    }

    public void dealTurn() {
        deck.dealCards(communityCards, 1);
        currentPhase = GamePhase.TURN;
        resetBetsForRound();
    }

    public void dealRiver() {
        deck.dealCards(communityCards, 1);
        currentPhase = GamePhase.RIVER;
        resetBetsForRound();
    }

    public List<Player> conductShowdown() {
        currentPhase = GamePhase.SHOWDOWN;

        List<Player> showdownPlayers = activePlayers.stream()
                .filter(p -> !p.getHasFolded())
                .toList();

        if (showdownPlayers.size() == 1) {
            distributePot(showdownPlayers);
            return showdownPlayers;
        }

        evaluateHands(showdownPlayers);
        List<Player> winners = determineWinners(showdownPlayers);
        distributePot(winners);

        return winners;
    }

    private void evaluateHands(List<Player> players) {
        for (Player player : players) {
            HandEvaluationResult result = handEvaluator.getBestHand(communityCards, player.getHoleCards());
            player.setBestHand(result.bestHand());
            player.setHandRank(result.handRank());
        }
    }

    private List<Player> determineWinners(List<Player> players) {
        players.sort(Comparator.comparing(Player::getHandRank).reversed());
        if (players.isEmpty()) {
            return new ArrayList<>();
        }
        Player bestPlayer = players.getFirst();
        List<Player> winners = new ArrayList<>();
        winners.add(bestPlayer);

        for (int i = 1; i < players.size(); i++) {
            Player currentPlayer = players.get(i);
            if (currentPlayer.getHandRank() == bestPlayer.getHandRank()) {
                if (!handEvaluator.isBetterHandOfSameRank(bestPlayer.getBestHand(), currentPlayer.getBestHand(), bestPlayer.getHandRank()) &&
                        !handEvaluator.isBetterHandOfSameRank(currentPlayer.getBestHand(), bestPlayer.getBestHand(), bestPlayer.getHandRank())) {
                    winners.add(currentPlayer);
                }
            } else {
                break;
            }
        }
        return winners;
    }

    private void distributePot(List<Player> winners) {
        if (winners.isEmpty()) {
            return;
        }
        int potShare = pot / winners.size();
        for (Player winner : winners) {
            winner.addChips(potShare);
        }
        pot %= winners.size(); // Any remainder stays for the next hand
    }

    public void advancePositions() {
        dealerPosition = (dealerPosition + 1) % activePlayers.size();
        smallBlindPosition = (dealerPosition + 1) % activePlayers.size();
        bigBlindPosition = (smallBlindPosition + 1) % activePlayers.size();
        currentPlayerPosition = (bigBlindPosition + 1) % activePlayers.size();
    }

    public void cleanupAfterHand() {
        players.forEach(p -> {
            if (p.getChips() == 0) {
                p.setIsOut();
            }
        });
        activePlayers.removeIf(Player::getIsOut);

        if (activePlayers.size() <= 1) {
            gameOver = true;
        }
    }

    public boolean isHandOver() {
        long activePlayerCount = activePlayers.stream().filter(p -> !p.getHasFolded()).count();
        return activePlayerCount <= 1;
    }

    public void resetBetsForRound() {
        for (Player player : activePlayers) {
            player.resetCurrentBet();
        }
        currentHighestBet = 0;
    }

    // Getters and Setters
    public String getGameId() { return gameId; }
    public List<Player> getPlayers() { return players; }
    public List<Player> getActivePlayers() { return activePlayers; }
    public Player getCurrentPlayer() {
        return activePlayers.get(currentPlayerPosition);
    }
    public void nextPlayer() {
        currentPlayerPosition = (currentPlayerPosition + 1) % activePlayers.size();
    }
    public List<Card> getCommunityCards() { return communityCards; }
    public int getPot() { return pot; }
    public GamePhase getCurrentPhase() { return currentPhase; }
    public boolean isGameOver() { return gameOver; }
    public int getCurrentHighestBet() { return currentHighestBet; }
}