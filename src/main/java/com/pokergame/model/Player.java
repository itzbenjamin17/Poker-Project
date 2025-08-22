package com.pokergame.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a poker player with hole cards, chips, and betting capabilities.
 * Manages player state including folding, all-in status, and current bets.
 *
 * <p><b>WARNING:</b> Very little validation is done in this class, make sure inputs to methods in this class are valid</p>
 *
 * @author Your Name
 * @version 1.0
 */
public class Player {
    private final String name;
    private final String playerId;
    private List<Card> holeCards;
    private List<Card> bestHand;
    private HandRank handRank;
    private int chips;
    private int currentBet;
    private boolean hasFolded;
    private boolean isAllIn;
    private boolean isOut;

    /**
     * Creates a new player with the specified name and starting chip count.
     *
     * @param name the player's name
     * @param chips the starting number of chips for the player
     */
    public Player(String name, String playerId, int chips) {
        this.name = name;
        this.playerId = playerId;
        this.chips = chips;
        this.holeCards = new ArrayList<>();
        this.bestHand = new ArrayList<>();
        this.currentBet = 0;
        this.handRank = HandRank.NO_HAND;
        this.hasFolded = false;
        this.isAllIn = false;
        this.isOut = false;
    }

    /**
     * Resets all player attributes for a new hand.
     * Clears hole cards, best hand, and resets betting status.
     */
    public void resetAttributes() {
        this.holeCards = new ArrayList<>();
        this.bestHand = new ArrayList<>();
        this.handRank = HandRank.NO_HAND;
        this.hasFolded = false;
        this.isAllIn = false;
        this.currentBet = 0;
    }

    /**
     * Resets the current bet to zero for a new betting round.
     */
    public void resetCurrentBet() {
        this.currentBet = 0;
    }

    /**
     * Moves all remaining chips to the pot and marks player as all-in.
     *
     * @param pot the current pot value
     * @return the updated pot value after going all-in
     */
    private int goAllIn(int pot){
        isAllIn = true;
        pot += chips;
        currentBet += chips;
        chips = 0;
        return pot;
    }

    /**
     * Executes a player action and returns the updated pot value.
     *
     * <p><b>IMPORTANT:</b> The caller is responsible for calculating the correct
     *  amount needed for betting actions (BET, RAISE, CALL). This method does NOT
     *  validate or calculate bet amounts - it uses the provided amount directly.</p>
     * @param action the poker action to perform (FOLD, CHECK, CALL, BET, RAISE, ALL_IN)
     * @param amount the chip amount for betting actions (ignored for FOLD, CHECK, ALL_IN)
     * @param pot the current pot value
     * @return the updated pot value after the action
     * @throws IllegalArgumentException if the action is not recognized
     */
    public int doAction(PlayerAction action, int amount, int pot) {
        switch (action) {
            case RAISE, BET, CALL -> {
                return payChips(pot, amount);
            }
            case ALL_IN -> {
                return goAllIn(pot);
            }
            case FOLD -> {
                hasFolded = true;
                return pot;
            }
            case CHECK -> {
                return pot;
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    /**
     * Moves chips from player to pot and updates the current bet.
     *
     * @param pot the current pot value
     * @param amount the amount of chips to pay
     * @return the updated pot value
     */
    public int payChips(int pot, int amount) {
        this.chips -= amount;
        this.currentBet += amount;
        pot += amount;
        return pot;
    }

    /**
     *Adds the amount (most likely from the pot) and adds it to the player's chips
     * @param amount the amount to add to the chips
     */
    public void addChips(int amount) {
        this.chips += amount;
    }

    /**
     * Returns the player's hole cards.
     *
     * @return the player's hole card list
     */
     public List<Card> getHoleCards(){return holeCards;}

    /**
     * Returns the player's current bet in this round.
     *
     * @return the current bet amount
     */
    public int getCurrentBet() {
        return currentBet;
    }

    /**
     * Returns the number of chips the player has remaining.
     *
     * @return the chip count
     */
    public int getChips() {
        return chips;
    }

    /**
     * Returns the player's name.
     *
     * @return the player's name
     */
    public String getName(){
        return name;
    }

    /**
     * Returns whether the player has folded in the current hand.
     *
     * @return true if the player has folded, false otherwise
     */
    public boolean getHasFolded() {
        return hasFolded;
    }

    /**
     * Returns whether the player is all-in.
     *
     * @return true if the player is all-in, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean getIsAllIn() {
        return isAllIn;
    }

    /**
     * Returns whether the player is out of the game (no chips).
     *
     * @return true if the player is out, false otherwise
     */
    public boolean getIsOut() {
        return isOut;
    }

    /**
     * Sets the player to be out, should only be used when the player has no chips
     *
     */
    public void setIsOut() {
        this.isOut = true;
    }

    /**
     * Returns the player's best five-card poker hand.
     *
     * @return list of cards representing the best hand
     */
    public List<Card> getBestHand() {return bestHand;}

    /**
     * Returns the player's ID
     *
     * @return returns the player's ID
     */
    public String getPlayerId() {return playerId;}

    /**
     * Returns the poker hand ranking for this player's best hand.
     * Rankings from strongest to weakest: ROYAL_FLUSH, STRAIGHT_FLUSH,
     * FOUR_OF_A_KIND, FULL_HOUSE, FLUSH, STRAIGHT, THREE_OF_A_KIND,
     * TWO_PAIR, ONE_PAIR, HIGH_CARD.
     *
     * @return the HandRank enum representing the player's best hand
     */
    public HandRank getHandRank() {return handRank;}
}
