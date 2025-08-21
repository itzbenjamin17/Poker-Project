package com.pokergame.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a standard 52-card deck for poker games.
 * The deck is automatically shuffled upon creation and provides methods
 * to deal and remove cards.
 *
 *
 * @author Benjamin Adubofour
 * @version 1.0
 */
public class Deck {
    private List<Card> cards;

    /**
     * Creates a new shuffled deck containing all 52 standard playing cards.
     * Cards are created with values: Ace=14, King=13, Queen=12, Jack=11, 2-10=face value.
     */
    public Deck() {
        String[] suits = {"Clubs", "Diamonds", "Hearts", "Spades"};
        String[] ranks = {"Ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King"};
        Map<String, Integer> rankMap = Map.ofEntries(
                Map.entry("2", 2),
                Map.entry("3", 3),
                Map.entry("4", 4),
                Map.entry("5", 5),
                Map.entry("6", 6),
                Map.entry("7", 7),
                Map.entry("8", 8),
                Map.entry("9", 9),
                Map.entry("10", 10),
                Map.entry("Jack", 11),
                Map.entry("Queen", 12),
                Map.entry("King", 13),
                Map.entry("Ace", 14)
        );

        cards = new ArrayList<>();
        for (String suit : suits) {
            for (String rank : ranks) {
                cards.add(new Card(rankMap.get(rank), suit, rank));
            }
        }
        Collections.shuffle(cards);
    }

    /**
     * Returns the number of cards remaining in the deck.
     *
     * @return the count of undealt cards
     */
    public int getNumberOfCards() {return cards.size();}

    /**
     * Deals the specified number of cards from the top of the deck into the given list.
     *
     * @param cards the list to receive the dealt cards
     * @param numberOfCards the number of cards to deal
     * @throws IllegalStateException if there aren't enough cards in the deck
     */
    public void dealCards(List<Card> cards, int numberOfCards) {
        for (int i = 0; i < numberOfCards; i++) {
            cards.add(this.cards.removeFirst());
        }
    }

    /**
     * Removes the specified card from the deck if present.
     *
     * @param card the card to remove from the deck
     */
    public void removeCard(Card card) {this.cards.remove(card);}
}
