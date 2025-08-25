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
        else if (players == null || players.size() < 3) {
            throw new IllegalArgumentException("At least 3 players are required to start a game");
        }
        else if (players.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Player list cannot contain null elements");
        }
        this.gameId = gameId;
        this.players = players;
        this.activePlayers = players;
        this.deck = new Deck();
        this.communityCards = new ArrayList<>();
        this.pot = 0;
        this.dealerPosition = 0;
        this.smallBlindPosition = 1;
        this.bigBlindPosition = 2;
        this.currentPlayerPosition = 3;
        this.currentHighestBet = 0;
        this.currentPhase = GamePhase.PRE_FLOP;
        this.gameOver = false;
        this.handEvaluator = handEvaluator;
    }

    /**
     * Starts a new hand/round
     */
    public void startNewHand() {
        // Check if only one player left
        if (activePlayers.size() == 1) {
            gameOver = true;
            // In the full-stack app: send winner notification via WebSocket
            return;
        }

        resetForNewHand();
        dealHoleCards();
        postBlinds();
        currentPhase = GamePhase.PRE_FLOP;
        currentHighestBet = BIG_BLIND;

        // Pre-flop betting round
        conductBettingRound();

        if (!isHandOver()) {
            dealFlop();
            conductBettingRound();
        }

        if (!isHandOver()) {
            dealTurn();
            conductBettingRound();
        }

        if (!isHandOver()) {
            dealRiver();
            conductBettingRound();
        }

        if (!isHandOver()) {
            conductShowdown();
        }

        cleanupAfterHand();
        advancePositions();
    }

    private void resetForNewHand() {
        // Reset deck and community cards
        deck = new Deck();
        communityCards.clear();

        // Remove players who are out
        activePlayers.clear();
        for (Player player : players) {
            if (!player.getIsOut()) {
                player.resetAttributes();
                activePlayers.add(player);
            }
        }

        if (activePlayers.size() == 1) {
            gameOver = true;
            // do something when the game is over
        }
    }

    private void dealHoleCards() {
        for (Player player : activePlayers) {
            deck.dealCards(player.getHoleCards(), 2);
        }
        // In the full-stack app: send cards to clients via WebSocket
    }

    private void postBlinds() {
        if (activePlayers.size() >= 2) {
            Player smallBlindPlayer = activePlayers.get(smallBlindPosition);
            Player bigBlindPlayer = activePlayers.get(bigBlindPosition);

            pot += smallBlindPlayer.payChips(pot,SMALL_BLIND);
            pot += bigBlindPlayer.payChips(pot,BIG_BLIND);

            // In full-stack app: notify clients of blind posts
        }
    }

    private void conductBettingRound() {
        resetBetsForRound();

        int firstPlayer = getNextActivePlayer(dealerPosition);
        int currentPlayer = firstPlayer;
        boolean everyoneHadTurn = false;

        do {
            Player player = activePlayers.get(currentPlayer);

            if (!player.getHasFolded() && !player.getIsAllIn()) {
                // In the full-stack app: this comes from the client via API/WebSocket
                PlayerDecision decision = getPlayerDecision(player);
                processPlayerDecision(player, decision);

                // In the full-stack app: broadcast action to all clients
            }

            currentPlayer = getNextActivePlayer(currentPlayer);

            if (currentPlayer == firstPlayer) {
                everyoneHadTurn = true;
            }

        } while (!isBettingRoundComplete() || !everyoneHadTurn);
    }

    /**
     * Template method - in real app this gets decision from frontend
     */
    private PlayerDecision getPlayerDecision(Player player) {
        // This would be replaced by actual client input handling
        // For now, return a placeholder decision
        if (player.getCurrentBet() < currentHighestBet) {
            return new PlayerDecision(PlayerAction.CALL,
                    currentHighestBet - player.getCurrentBet(), player.getPlayerId());
        } else {
            return new PlayerDecision(PlayerAction.CHECK, 0, player.getPlayerId());
        }
    }

    public void processPlayerDecision(Player player, PlayerDecision decision) {
        switch (decision.action()) {
            case FOLD -> player.doAction(PlayerAction.FOLD, 0, 0);

            case CHECK -> // No chips moved
                    player.doAction(PlayerAction.CHECK, 0, 0);

            case CALL, BET, RAISE -> {
                int amount = calculateActualAmount(player, decision);
                pot += player.doAction(decision.action(), amount, pot);
                if (amount > currentHighestBet) {
                    currentHighestBet = amount;
                }
            }
            case ALL_IN -> pot += player.doAction(PlayerAction.ALL_IN, 0, pot);
        }
    }

    private int calculateActualAmount(Player player, PlayerDecision decision) {
        // Calculate the actual amount needed based on the action type
        switch (decision.action()) {
            case CALL -> {
                return currentHighestBet - player.getCurrentBet();
            }
            case BET, RAISE -> {
                return decision.amount();
            }
            default -> throw new IllegalArgumentException("Invalid, cannot calculate the amount needed for decision: " + decision.action());
        }
    }

    private boolean isBettingRoundComplete() {
        return activePlayers.stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .allMatch(p -> p.getCurrentBet() >= currentHighestBet);
    }

    private void dealFlop() {
        deck.dealCards(communityCards, 3);
        currentPhase = GamePhase.FLOP;
        // In the full-stack app: send flop state to clients
    }

    private void dealTurn() {
        deck.dealCards(communityCards, 1);
        currentPhase = GamePhase.TURN;
        // In the full-stack app: send turn state to clients
    }

    private void dealRiver() {
        deck.dealCards(communityCards, 1);
        currentPhase = GamePhase.RIVER;
        // In the full-stack app: send river state to clients
    }

    private void conductShowdown() {
        currentPhase = GamePhase.SHOWDOWN;

        List<Player> showdownPlayers = activePlayers.stream()
                .filter(p -> !p.getHasFolded())
                .toList();

        // Template for hand evaluation - you'll implement this
        evaluateHands(showdownPlayers);
        List<Player> winners = determineWinners(showdownPlayers);
        distributePot(winners);

        // In full-stack app: send showdown results to clients
        // broadcastShowdownResults(winners, showdownPlayers);
    }

    /**
     * Template method - implement your hand evaluation here
     */
    private void evaluateHands(List<Player> players) {
        for (Player player : players) {
            // Use injected HandEvaluatorService
            HandEvaluationResult result = handEvaluator.getBestHand(communityCards, player.getHoleCards());
            player.setBestHand(result.bestHand());
            player.setHandRank(result.handRank());
        }
    }

    /**
     * Template method - implement winner determination here
     */
    private List<Player> determineWinners(List<Player> players) {
        // Sort players by hand strength
        players.sort((p1, p2) -> {
            // Compare hand ranks first
            int rankComparison = p1.getHandRank().compareTo(p2.getHandRank());
            if (rankComparison != 0) {
                return -rankComparison; // Best rank first
            }

            // Same rank - use detailed comparison from service
            if (handEvaluator.isBetterHandOfSameRank(p1.getBestHand(), p2.getBestHand(), p1.getHandRank())) {
                return -1; // p1 wins
            } else if (handEvaluator.isBetterHandOfSameRank(p2.getBestHand(), p1.getBestHand(), p2.getHandRank())) {
                return 1; // p2 wins
            }
            return 0; // Tie
        });

        // Collect all winners (including ties)
        List<Player> winners = new ArrayList<>();
        Player bestPlayer = players.get(0);
        winners.add(bestPlayer);

        for (int i = 1; i < players.size(); i++) {
            Player current = players.get(i);

            if (bestPlayer.getHandRank() != current.getHandRank()) {
                break; // Different rank = not a winner
            }

            // Check if hands are identical (tie)
            boolean p1Better = handEvaluator.isBetterHandOfSameRank(bestPlayer.getBestHand(), current.getBestHand(), bestPlayer.getHandRank());
            boolean p2Better = handEvaluator.isBetterHandOfSameRank(current.getBestHand(), bestPlayer.getBestHand(), current.getHandRank());

            if (!p1Better && !p2Better) {
                winners.add(current); // It's a tie
            } else {
                break; // Current player lost
            }
        }

        return winners;
    }

    private void distributePot(List<Player> winners) {
        if (winners.size() == 1) {
            winners.getFirst().addChips(pot);
        } else {
            int payoutPerWinner = pot / winners.size();
            int remainder = pot % winners.size();

            for (Player winner : winners) {
                winner.addChips(payoutPerWinner);
            }
            // Remainder stays in pot for next hand
            pot = remainder;
        }

        if (winners.size() == 1) {
            pot = 0;
        }
    }

    private void cleanupAfterHand() {
        // Mark players as out if they have no chips and then remove them from active players
        for (Player player : players) {
            if (player.getChips() == 0) {
                player.setIsOut();
            }
        }
    }

    private void advancePositions() {
        // Move dealer button and blinds
        if (activePlayers.size() == 2) {
            // Heads up rules
            dealerPosition = (dealerPosition + 1) % activePlayers.size();
            smallBlindPosition = dealerPosition;
            bigBlindPosition = (dealerPosition + 1) % activePlayers.size();
        } else {
            dealerPosition = getNextActivePlayer(dealerPosition);
            smallBlindPosition = getNextActivePlayer(dealerPosition);
            bigBlindPosition = getNextActivePlayer(smallBlindPosition);
        }
        currentPlayerPosition = getNextActivePlayer(bigBlindPosition);
    }

    private int getNextActivePlayer(int currentPosition) {
        int nextPosition = (currentPosition + 1) % activePlayers.size();
        while (activePlayers.get(nextPosition).getHasFolded()) {
            nextPosition = (nextPosition + 1) % activePlayers.size();
        }
        return nextPosition;
    }

    private void resetBetsForRound() {
        for (Player player : activePlayers) {
            player.resetCurrentBet();
        }
        currentHighestBet = 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isHandOver() {
        long activePlayers = this.activePlayers.stream()
                .filter(p -> !p.getHasFolded() && !p.getIsOut())
                .count();
        return activePlayers <= 1;
    }

    // Getters and setters
    public String getGameId() { return gameId; }
    public List<Player> getPlayers() { return players; }
    public List<Player> getActivePlayers() { return activePlayers; }
    public List<Card> getCommunityCards() { return communityCards; }
    public int getPot() { return pot; }
    public GamePhase getCurrentPhase() { return currentPhase; }
    public boolean isGameOver() { return gameOver; }
    public int getCurrentHighestBet() { return currentHighestBet; }
}
