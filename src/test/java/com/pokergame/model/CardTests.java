package com.pokergame.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CardTest {

    @Test
    void whenValidCardCreated_shouldSetPropertiesCorrectly() {
        // Valid card creation
        Card card = new Card(14, "Hearts", "Ace");

        assertEquals(14, card.getValue());
        assertEquals("Hearts", card.getSuit());
        assertEquals("Ace", card.getRank());
    }

    @Test
    void whenCardValueTooLow_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Card(1, "Hearts", "Ace");  // Invalid: value too low
        });

        assertTrue(exception.getMessage().contains("Invalid card value: 1"));
    }

    @Test
    void whenCardValueTooHigh_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Card(15, "Hearts", "Ace");  // Invalid: value too high
        });

        assertTrue(exception.getMessage().contains("Invalid card value: 15"));
    }

    @Test
    void whenSuitIsNull_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Card(10, null, "Ten");  // Invalid: null suit
        });

        assertTrue(exception.getMessage().contains("Card suit cannot be null or empty"));
    }

    @Test
    void whenSuitIsEmpty_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Card(10, "", "Ten");  // Invalid: empty suit
        });

        assertTrue(exception.getMessage().contains("Card suit cannot be null or empty"));
    }

    @Test
    void whenRankIsNull_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Card(10, "Hearts", null);  // Invalid: null rank
        });

        assertTrue(exception.getMessage().contains("Card rank cannot be null or empty"));
    }
}
