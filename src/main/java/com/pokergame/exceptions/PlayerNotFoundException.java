package com.pokergame.exceptions;

/**
 * Thrown when a player cannot be found in the game
 */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(String playerId) {
        super("Player not found: " + playerId);
    }

    public PlayerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
