package com.pokergame.model;

/**
 * Represents a playing card with a suit, rank, and numeric value.
 * Cards are immutable once created.
 *
 * <p><b>WARNING:</b> Almost no validation is done in this class,
 * validation should be done where this class is being used when creating it</p>
 */
public class Card {
    private final int value;
    private final String suit;
    private final String rank;

    /**
     * Creates a new Card with the specified value, suit, and rank.
     *
     * @param value the numeric value of the card
     * @param suit the suit of the card (e.g., "Hearts", "Spades")
     * @param rank the rank of the card (e.g., "Ace", "King", "2")
     */
    public Card(int value, String suit, String rank) {
        if (value < 2 || value > 14) {
            throw new IllegalArgumentException("Invalid card value: " + value);
        }
        if (suit == null || suit.trim().isEmpty()) {
            throw new IllegalArgumentException("Card suit cannot be null or empty");
        }
        if (rank == null || rank.trim().isEmpty()){
            throw new IllegalArgumentException("Card rank cannot be null or empty");
        }
        this.value = value;
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * Returns the numeric value of this card.
     *
     * @return the card's numeric value
     */
    public int getValue() {return value;}

    /**
     * Returns the suit of this card.
     *
     * @return the card's suit
     */
    public String getSuit() {return suit;}

    /**
     * Returns the rank of this card.
     *
     * @return the card's rank
     */
    public String getRank() {return rank;}

    /**
     * Returns a string representation of this card in "Rank of Suit" format.
     *
     * @return formatted string (e.g., "Ace of Hearts")
     */
    public String toString() {return rank + " of " + suit;}
}
