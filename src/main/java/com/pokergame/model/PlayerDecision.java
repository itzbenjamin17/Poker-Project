package com.pokergame.model;

public class PlayerDecision {
    private final PlayerAction action;
    private final int amount;
    private final String playerId;

    public PlayerDecision(PlayerAction action, int amount, String playerId ){
        this.action = action;
        this.amount = amount;
        this.playerId = playerId;
    }

    // Getters and setters
    public PlayerAction getAction() { return action; }
    public int getAmount() { return amount; }
    public String getPlayerId() { return playerId; }
}