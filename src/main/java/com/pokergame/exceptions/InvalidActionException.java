package com.pokergame.exceptions;

/**
 * Thrown when a player attempts an invalid poker action
 */
public class InvalidActionException extends RuntimeException {

    public InvalidActionException() {
        super();
    }

    public InvalidActionException(String message) {
        super(message);
    }

    public InvalidActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
