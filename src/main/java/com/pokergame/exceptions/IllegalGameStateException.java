package com.pokergame.exceptions;

/**
 * Thrown when game is in an invalid state for the requested operation
 */
public class IllegalGameStateException extends RuntimeException {

    public IllegalGameStateException(String message) {
        super(message);
    }

    public IllegalGameStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
