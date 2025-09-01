package com.pokergame.model;

import com.pokergame.dto.PlayerDecision;
import com.pokergame.service.HandEvaluatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Represents a poker game instance with players, betting rounds, and game state
 * management.
 * Handles game progression, betting logic, and hand evaluation.
 */
public class Game {

    private static final Logger logger = LoggerFactory.getLogger(Game.class);

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

    // Track if everyone has had their initial turn in current betting round
    private boolean everyoneHasHadInitialTurn;

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
        this.everyoneHasHadInitialTurn = false;
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

            this.pot = smallBlindPlayer.doAction(PlayerAction.BET, smallBlind, this.pot);
            this.pot = bigBlindPlayer.doAction(PlayerAction.BET, bigBlind, this.pot);
            currentHighestBet = bigBlind;
        }
    }

    public String processPlayerDecision(Player player, PlayerDecision decision) {
        // Check if there are all-in players that would limit raises/all-ins
        boolean hasAllInPlayers = activePlayers.stream()
                .anyMatch(p -> p.getIsAllIn() && !p.getHasFolded());

        // If there are all-in players, convert raises and all-ins to calls (unless
        // player has fewer chips)
        PlayerDecision actualDecision = decision;
        String conversionMessage = null;

        if (hasAllInPlayers && (decision.action() == PlayerAction.RAISE || decision.action() == PlayerAction.ALL_IN)) {
            // Check if the player has enough chips to actually call the current bet
            int callAmount = currentHighestBet - player.getCurrentBet();

            if (decision.action() == PlayerAction.ALL_IN && player.getChips() <= callAmount) {
                // Player doesn't have enough chips to call, so legitimate all-in
                logger.debug("Player {} going all-in with insufficient chips to call - allowing all-in",
                        player.getName());
            } else {
                // Player has enough chips to call or is trying to raise - convert to call
                logger.info("Player {} attempted {} but there are all-in players. Converting to call",
                        player.getName(), decision.action());
                actualDecision = new PlayerDecision(PlayerAction.CALL, 0, decision.playerId());
                conversionMessage = "Your " + decision.action().toString().toLowerCase()
                        + " was converted to a call because there are all-in players.";
            }
        }

        // Validate raise amounts (only if still a raise after conversion)
        if (actualDecision.action() == PlayerAction.RAISE) {
            int totalBetAfterRaise = player.getCurrentBet() + actualDecision.amount();
            if (totalBetAfterRaise <= currentHighestBet) {
                throw new IllegalArgumentException(
                        "Raise amount must result in a bet higher than current highest bet of " + currentHighestBet +
                                ". Your current bet is " + player.getCurrentBet() +
                                ", so you need to raise by at least "
                                + (currentHighestBet - player.getCurrentBet() + 1));
            }
        }

        switch (actualDecision.action()) {
            case FOLD, CHECK -> {
                player.doAction(actualDecision.action(), 0, this.pot);
            }
            case CALL, BET, RAISE -> {
                int amount = calculateActualAmount(player, actualDecision);
                this.pot = player.doAction(actualDecision.action(), amount, this.pot);
                if (player.getCurrentBet() > currentHighestBet) {
                    currentHighestBet = player.getCurrentBet();
                }
            }
            case ALL_IN -> {
                this.pot = player.doAction(PlayerAction.ALL_IN, 0, this.pot);
                if (player.getCurrentBet() > currentHighestBet) {
                    currentHighestBet = player.getCurrentBet();
                }
            }
        }

        return conversionMessage; // Return null if no conversion, or the message if converted
    }

    private int calculateActualAmount(Player player, PlayerDecision decision) {
        return switch (decision.action()) {
            case CALL -> currentHighestBet - player.getCurrentBet();
            case BET, RAISE -> decision.amount();
            default -> 0;
        };
    }

    public boolean isBettingRoundComplete() {
        // Phase 1: Everyone must have their initial turn first (like your VB.NET logic)
        if (!everyoneHasHadInitialTurn) {
            logger.debug("Betting round not complete: Not everyone has had initial turn");
            return false;
        }

        // Phase 2: Based on your VB.NET logic: While Table.Any(Function(item)
        // item.CurrentBet < CurrentHighestBetCopy AndAlso item.HasFolded = False
        // AndAlso item.IsAllIn = False)
        // Betting round is complete when NO such player exists
        boolean hasPlayerWhoNeedsToAct = activePlayers.stream()
                .anyMatch(p -> p.getCurrentBet() < currentHighestBet &&
                        !p.getHasFolded() &&
                        !p.getIsAllIn());

        logger.debug("Checking if betting round complete - Current highest bet: {}, Everyone has had initial turn: {}",
                currentHighestBet, everyoneHasHadInitialTurn);

        if (logger.isDebugEnabled()) {
            activePlayers.forEach(p -> {
                logger.debug("Player {}: bet={}, folded={}, all-in={}, needsToAct={}",
                        p.getName(), p.getCurrentBet(), p.getHasFolded(), p.getIsAllIn(),
                        (p.getCurrentBet() < currentHighestBet && !p.getHasFolded() && !p.getIsAllIn()));
            });
        }

        boolean isComplete = !hasPlayerWhoNeedsToAct;
        logger.debug("Betting round result: {} (hasPlayerWhoNeedsToAct={})",
                (isComplete ? "Complete" : "Not complete"), hasPlayerWhoNeedsToAct);

        return isComplete;
    }

    public void setEveryoneHasHadInitialTurn(boolean value) {
        this.everyoneHasHadInitialTurn = value;
        logger.debug("Set everyoneHasHadInitialTurn = {}", value);
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
        logger.info("Conducting showdown for game {}", gameId);
        currentPhase = GamePhase.SHOWDOWN;

        List<Player> showdownPlayers = activePlayers.stream()
                .filter(p -> !p.getHasFolded())
                .toList();

        logger.info("Showdown players: {}, Current pot: {}",
                showdownPlayers.stream().map(Player::getName).toList(), pot);

        if (showdownPlayers.size() == 1) {
            logger.info("Only one player remaining, auto-win for {}", showdownPlayers.get(0).getName());
            distributePot(showdownPlayers);
            return showdownPlayers;
        }

        logger.debug("Evaluating hands for {} players", showdownPlayers.size());
        evaluateHands(showdownPlayers);

        if (logger.isDebugEnabled()) {
            showdownPlayers
                    .forEach(p -> logger.debug("Player {}: {} with {}", p.getName(), p.getHandRank(), p.getBestHand()));
        }

        logger.debug("Determining winners from {} players", showdownPlayers.size());
        List<Player> winners = determineWinners(showdownPlayers);

        logger.info("Winners: {}", winners.stream().map(Player::getName).toList());
        logger.info("Distributing pot of {} to {} winner(s)", pot, winners.size());
        distributePot(winners);

        logger.debug("Pot after distribution: {}", pot);
        if (logger.isDebugEnabled()) {
            winners.forEach(p -> logger.debug("Player {} now has {} chips", p.getName(), p.getChips()));
        }

        logger.info("Showdown complete");
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
        logger.info("Distributing pot for game {}", gameId);
        if (winners.isEmpty()) {
            logger.warn("No winners to distribute pot to!");
            return;
        }

        logger.info("Pot to distribute: {}, Number of winners: {}", pot, winners.size());

        int potShare = pot / winners.size();
        logger.info("Each winner gets: {} chips", potShare);

        for (Player winner : winners) {
            int chipsBefore = winner.getChips();
            winner.addChips(potShare);
            logger.debug("Player {}: {} -> {} chips", winner.getName(), chipsBefore, winner.getChips());
        }

        pot = pot % winners.size(); // Any remainder stays for the next hand

        logger.debug("Pot remainder for next hand: {}", pot);
        logger.info("Pot distribution complete");
    }

    public void advancePositions() {
        dealerPosition = (dealerPosition + 1) % activePlayers.size();
        smallBlindPosition = (dealerPosition + 1) % activePlayers.size();
        bigBlindPosition = (smallBlindPosition + 1) % activePlayers.size();
        currentPlayerPosition = (bigBlindPosition + 1) % activePlayers.size();
    }

    public void cleanupAfterHand() {
        logger.debug("Cleaning up after hand for game {}", gameId);

        if (logger.isDebugEnabled()) {
            logger.debug("Players before cleanup:");
            players.forEach(p -> logger.debug("  {}: {} chips, isOut: {}", p.getName(), p.getChips(), p.getIsOut()));
        }

        players.forEach(p -> {
            if (p.getChips() == 0) {
                logger.info("Setting {} as out (0 chips)", p.getName());
                p.setIsOut();
            }
        });

        int sizeBefore = activePlayers.size();
        activePlayers.removeIf(Player::getIsOut);
        int sizeAfter = activePlayers.size();

        logger.info("Active players: {} -> {}", sizeBefore, sizeAfter);
        logger.debug("Active players after cleanup: {}", activePlayers.stream().map(Player::getName).toList());

        if (activePlayers.size() <= 1) {
            logger.info("Game over - only {} active players remaining", activePlayers.size());
            gameOver = true;
        } else {
            logger.debug("Game continues with {} active players", activePlayers.size());
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
        everyoneHasHadInitialTurn = false; // Reset for new betting round
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