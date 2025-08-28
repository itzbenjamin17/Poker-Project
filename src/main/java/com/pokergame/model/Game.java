package com.pokergame.model;

import com.pokergame.dto.PlayerDecision;
import com.pokergame.service.HandEvaluatorService;

import java.util.*;

public class Game {

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
    private final int smallBlind;
    private final int bigBlind;

    // Track which players have acted in the current betting round
    private final Set<String> playersWhoActedThisRound;

    public Game(String gameId, List<Player> players, int smallBlind, int bigBlind, HandEvaluatorService handEvaluator) {
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
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.handEvaluator = handEvaluator;
        this.playersWhoActedThisRound = new HashSet<>();
    }

    public void resetForNewHand() {
        deck = new Deck();
        communityCards.clear();
        pot = 0;
        currentHighestBet = 0;
        currentPhase = GamePhase.PRE_FLOP;
        playersWhoActedThisRound.clear(); // Clear action tracking for new hand

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

            this.pot = smallBlindPlayer.doAction(PlayerAction.BET, smallBlind, this.pot);
            this.pot = bigBlindPlayer.doAction(PlayerAction.BET, bigBlind, this.pot);
            currentHighestBet = bigBlind;

            // Mark blind players as having acted (they've made their forced bets)
            playersWhoActedThisRound.add(smallBlindPlayer.getPlayerId());
            playersWhoActedThisRound.add(bigBlindPlayer.getPlayerId());
        }
    }

    public void processPlayerDecision(Player player, PlayerDecision decision) {
        // Validate raise amounts
        if (decision.action() == PlayerAction.RAISE) {
            int totalBetAfterRaise = player.getCurrentBet() + decision.amount();
            if (totalBetAfterRaise <= currentHighestBet) {
                throw new IllegalArgumentException(
                        "Raise amount must result in a bet higher than current highest bet of " + currentHighestBet +
                                ". Your current bet is " + player.getCurrentBet() +
                                ", so you need to raise by at least "
                                + (currentHighestBet - player.getCurrentBet() + 1));
            }
        }

        switch (decision.action()) {
            case FOLD, CHECK -> {
                player.doAction(decision.action(), 0, this.pot);
                // Track that this player has acted in this betting round
                playersWhoActedThisRound.add(player.getPlayerId());
            }
            case CALL, BET, RAISE -> {
                int amount = calculateActualAmount(player, decision);
                this.pot = player.doAction(decision.action(), amount, this.pot);
                if (player.getCurrentBet() > currentHighestBet) {
                    currentHighestBet = player.getCurrentBet();
                    // When someone raises, all other players need to act again
                    if (decision.action() == PlayerAction.RAISE) {
                        String raisingPlayerId = player.getPlayerId();
                        playersWhoActedThisRound.clear();
                        playersWhoActedThisRound.add(raisingPlayerId); // Only the raising player has acted
                    } else {
                        // For CALL and BET, just track that this player has acted
                        playersWhoActedThisRound.add(player.getPlayerId());
                    }
                } else {
                    // Track that this player has acted in this betting round
                    playersWhoActedThisRound.add(player.getPlayerId());
                }
            }
            case ALL_IN -> {
                this.pot = player.doAction(PlayerAction.ALL_IN, 0, this.pot);
                // Track that this player has acted in this betting round
                playersWhoActedThisRound.add(player.getPlayerId());
            }
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
        // Get all players who should have a chance to act (not folded, not all-in)
        List<Player> playersWhoShouldAct = activePlayers.stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .toList();

        System.out.println("Checking if betting round complete:");
        System.out.println("  Players who should act: " + playersWhoShouldAct.stream().map(Player::getName).toList());
        System.out.println("  Players who have acted: " + playersWhoActedThisRound);
        System.out.println("  Current highest bet: " + currentHighestBet);

        // If only 0 or 1 players can act, betting round is complete
        if (playersWhoShouldAct.size() <= 1) {
            System.out.println("  Result: Complete (only " + playersWhoShouldAct.size() + " players can act)");
            return true;
        }

        // Check if all active players have either:
        // 1. Folded or gone all-in, OR
        // 2. Matched the current highest bet AND have acted this round
        boolean allBetsEqual = playersWhoShouldAct.stream()
                .allMatch(p -> p.getCurrentBet() == currentHighestBet);

        boolean allPlayersHaveActed = playersWhoShouldAct.stream()
                .allMatch(p -> playersWhoActedThisRound.contains(p.getPlayerId()));

        System.out.println("  All bets equal (" + currentHighestBet + "): " + allBetsEqual);
        System.out.println("  All players have acted: " + allPlayersHaveActed);

        playersWhoShouldAct.forEach(p -> System.out.println("    " + p.getName() + ": bet=" + p.getCurrentBet() +
                ", acted=" + playersWhoActedThisRound.contains(p.getPlayerId())));

        boolean result = allBetsEqual && allPlayersHaveActed;
        System.out.println("  Result: " + (result ? "Complete" : "Not complete"));
        return result;
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
        System.out.println("=== CONDUCTING SHOWDOWN ===");
        currentPhase = GamePhase.SHOWDOWN;

        List<Player> showdownPlayers = activePlayers.stream()
                .filter(p -> !p.getHasFolded())
                .toList();

        System.out.println("Showdown players: " + showdownPlayers.stream().map(Player::getName).toList());
        System.out.println("Current pot: " + pot);

        if (showdownPlayers.size() == 1) {
            System.out.println("Only one player remaining, auto-win");
            distributePot(showdownPlayers);
            return showdownPlayers;
        }

        System.out.println("Evaluating hands...");
        evaluateHands(showdownPlayers);

        System.out.println("Hand rankings:");
        showdownPlayers.forEach(
                p -> System.out.println("  " + p.getName() + ": " + p.getHandRank() + " with " + p.getBestHand()));

        System.out.println("Determining winners...");
        List<Player> winners = determineWinners(showdownPlayers);

        System.out.println("Winners: " + winners.stream().map(Player::getName).toList());
        System.out.println("Distributing pot of " + pot + " to " + winners.size() + " winner(s)");
        distributePot(winners);

        System.out.println("Pot after distribution: " + pot);
        winners.forEach(p -> System.out.println("  " + p.getName() + " now has " + p.getChips() + " chips"));

        System.out.println("=== SHOWDOWN COMPLETE ===");
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
        // Create a mutable copy of the list for sorting
        List<Player> sortablePlayers = new ArrayList<>(players);
        sortablePlayers.sort(Comparator.comparing(Player::getHandRank).reversed());

        if (sortablePlayers.isEmpty()) {
            return new ArrayList<>();
        }

        Player bestPlayer = sortablePlayers.getFirst();
        List<Player> winners = new ArrayList<>();
        winners.add(bestPlayer);

        for (int i = 1; i < sortablePlayers.size(); i++) {
            Player currentPlayer = sortablePlayers.get(i);
            if (currentPlayer.getHandRank() == bestPlayer.getHandRank()) {
                if (!handEvaluator.isBetterHandOfSameRank(bestPlayer.getBestHand(), currentPlayer.getBestHand(),
                        bestPlayer.getHandRank()) &&
                        !handEvaluator.isBetterHandOfSameRank(currentPlayer.getBestHand(), bestPlayer.getBestHand(),
                                bestPlayer.getHandRank())) {
                    winners.add(currentPlayer);
                }
            } else {
                break;
            }
        }
        return winners;
    }

    private void distributePot(List<Player> winners) {
        System.out.println("=== DISTRIBUTING POT ===");
        if (winners.isEmpty()) {
            System.out.println("❌ No winners to distribute pot to!");
            return;
        }

        System.out.println("Pot to distribute: " + pot);
        System.out.println("Number of winners: " + winners.size());

        int potShare = pot / winners.size();
        System.out.println("Each winner gets: " + potShare + " chips");

        for (Player winner : winners) {
            int chipsBefore = winner.getChips();
            winner.addChips(potShare);
            System.out.println("  " + winner.getName() + ": " + chipsBefore + " -> " + winner.getChips() + " chips");
        }

        pot = pot % winners.size(); // Any remainder stays for the next hand

        System.out.println("Pot remainder for next hand: " + pot);
        System.out.println("=== POT DISTRIBUTION COMPLETE ===");
    }

    public void advancePositions() {
        dealerPosition = (dealerPosition + 1) % activePlayers.size();
        smallBlindPosition = (dealerPosition + 1) % activePlayers.size();
        bigBlindPosition = (smallBlindPosition + 1) % activePlayers.size();
        currentPlayerPosition = (bigBlindPosition + 1) % activePlayers.size();
    }

    public void cleanupAfterHand() {
        System.out.println("Cleaning up after hand...");
        System.out.println("Players before cleanup:");
        players.forEach(
                p -> System.out.println("  " + p.getName() + ": " + p.getChips() + " chips, isOut: " + p.getIsOut()));

        players.forEach(p -> {
            if (p.getChips() == 0) {
                System.out.println("Setting " + p.getName() + " as out (0 chips)");
                p.setIsOut();
            }
        });

        int sizeBefore = activePlayers.size();
        activePlayers.removeIf(Player::getIsOut);
        int sizeAfter = activePlayers.size();

        System.out.println("Active players: " + sizeBefore + " -> " + sizeAfter);
        System.out.println("Active players after cleanup: " + activePlayers.stream().map(Player::getName).toList());

        if (activePlayers.size() <= 1) {
            System.out.println("Game over - only " + activePlayers.size() + " active players remaining");
            gameOver = true;
        } else {
            System.out.println("Game continues with " + activePlayers.size() + " active players");
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
        playersWhoActedThisRound.clear(); // Clear the tracking for the new round
    }

    // Getters and Setters
    public String getGameId() {
        return gameId;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public List<Player> getActivePlayers() {
        return activePlayers;
    }

    public Player getCurrentPlayer() {
        return activePlayers.get(currentPlayerPosition);
    }

    public void nextPlayer() {
        currentPlayerPosition = (currentPlayerPosition + 1) % activePlayers.size();
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public int getPot() {
        return pot;
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getCurrentHighestBet() {
        return currentHighestBet;
    }
}