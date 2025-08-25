package com.pokergame.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.ArrayList;
import java.util.Arrays;

class PlayerTest {

    private Player player;
    private final String validName = "Alice";
    private final String validPlayerId = "player-123";
    private final int validChips = 100;

    @BeforeEach
    void setUp() {
        player = new Player(validName, validPlayerId, validChips);
    }

    // Constructor Tests
    @Test
    void whenValidPlayerCreated_shouldSetPropertiesCorrectly() {
        assertEquals(validName, player.getName());
        assertEquals(validPlayerId, player.getPlayerId());
        assertEquals(validChips, player.getChips());
        assertEquals(0, player.getCurrentBet());
        assertEquals(HandRank.NO_HAND, player.getHandRank());
        assertFalse(player.getHasFolded());
        assertFalse(player.getIsAllIn());
        assertFalse(player.getIsOut());
        assertTrue(player.getHoleCards().isEmpty());
        assertTrue(player.getBestHand().isEmpty());
    }

    @Test
    void whenPlayerNameIsNull_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Player(null, validPlayerId, validChips);
        });
        assertTrue(exception.getMessage().contains("Player name required"));
    }

    @Test
    void whenPlayerNameIsEmpty_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Player("", validPlayerId, validChips);
        });
        assertTrue(exception.getMessage().contains("Player name required"));
    }

    @Test
    void whenPlayerNameIsBlank_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Player("   ", validPlayerId, validChips);
        });
        assertTrue(exception.getMessage().contains("Player name required"));
    }

    @Test
    void whenChipsAreNegative_shouldThrowException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new Player(validName, validPlayerId, -10);
        });
        assertTrue(exception.getMessage().contains("Chips cannot be negative"));
    }

    @Test
    void whenChipsAreZero_shouldCreatePlayerSuccessfully() {
        Player zeroChipPlayer = new Player(validName, validPlayerId, 0);
        assertEquals(0, zeroChipPlayer.getChips());
    }

    // Reset Methods Tests
    @Test
    void whenResetAttributes_shouldResetAllPlayerState() {
        // Set up some state
        player.getHoleCards().add(new Card(14, "Hearts", "Ace"));
        player.getBestHand().add(new Card(13, "Hearts", "King"));
        player.doAction(PlayerAction.FOLD, 0, 0);

        // Reset
        player.resetAttributes();

        // Verify reset
        assertTrue(player.getHoleCards().isEmpty());
        assertTrue(player.getBestHand().isEmpty());
        assertEquals(HandRank.NO_HAND, player.getHandRank());
        assertFalse(player.getHasFolded());
        assertFalse(player.getIsAllIn());
        assertEquals(0, player.getCurrentBet());
    }

    @Test
    void whenResetCurrentBet_shouldResetBetToZero() {
        // Set up a current bet
        player.payChips(0, 20);
        assertEquals(20, player.getCurrentBet());

        // Reset current bet
        player.resetCurrentBet();
        assertEquals(0, player.getCurrentBet());
    }

    // PayChips Tests
    @Test
    void whenPayChips_shouldUpdateChipsAndBetAndPot() {
        int initialPot = 10;
        int betAmount = 25;

        int newPot = player.payChips(initialPot, betAmount);

        assertEquals(75, player.getChips()); // 100 - 25
        assertEquals(25, player.getCurrentBet());
        assertEquals(35, newPot); // 10 + 25
    }

    @Test
    void whenPayChipsMultipleTimes_shouldAccumulateCurrentBet() {
        player.payChips(0, 10);
        player.payChips(0, 15);

        assertEquals(75, player.getChips()); // 100 - 10 - 15
        assertEquals(25, player.getCurrentBet()); // 10 + 15
    }

    // AddChips Tests
    @Test
    void whenAddChips_shouldIncreaseChipCount() {
        player.addChips(50);
        assertEquals(150, player.getChips());
    }

    @Test
    void whenAddZeroChips_shouldNotChangeChipCount() {
        player.addChips(0);
        assertEquals(100, player.getChips());
    }

    // DoAction Tests
    @Test
    void whenDoActionFold_shouldSetHasFoldedTrue() {
        int pot = player.doAction(PlayerAction.FOLD, 0, 10);

        assertTrue(player.getHasFolded());
        assertEquals(10, pot); // Pot unchanged
        assertEquals(100, player.getChips()); // Chips unchanged
    }

    @Test
    void whenDoActionCheck_shouldNotChangeState() {
        int pot = player.doAction(PlayerAction.CHECK, 0, 10);

        assertFalse(player.getHasFolded());
        assertEquals(10, pot); // Pot unchanged
        assertEquals(100, player.getChips()); // Chips unchanged
        assertEquals(0, player.getCurrentBet()); // Bet unchanged
    }

    @Test
    void whenDoActionCall_shouldPayChips() {
        int pot = player.doAction(PlayerAction.CALL, 20, 10);

        assertEquals(80, player.getChips()); // 100 - 20
        assertEquals(20, player.getCurrentBet());
        assertEquals(30, pot); // 10 + 20
    }

    @Test
    void whenDoActionBet_shouldPayChips() {
        int pot = player.doAction(PlayerAction.BET, 30, 5);

        assertEquals(70, player.getChips()); // 100 - 30
        assertEquals(30, player.getCurrentBet());
        assertEquals(35, pot); // 5 + 30
    }

    @Test
    void whenDoActionRaise_shouldPayChips() {
        int pot = player.doAction(PlayerAction.RAISE, 40, 15);

        assertEquals(60, player.getChips()); // 100 - 40
        assertEquals(40, player.getCurrentBet());
        assertEquals(55, pot); // 15 + 40
    }

    @Test
    void whenDoActionAllIn_shouldMoveAllChipsToPot() {
        int pot = player.doAction(PlayerAction.ALL_IN, 0, 20);

        assertEquals(0, player.getChips());
        assertEquals(100, player.getCurrentBet()); // All chips moved to current bet
        assertEquals(120, pot); // 20 + 100
        assertTrue(player.getIsAllIn());
    }

    // State Management Tests
    @Test
    void whenSetIsOut_shouldMarkPlayerAsOut() {
        assertFalse(player.getIsOut());
        player.setIsOut();
        assertTrue(player.getIsOut());
    }

    // Hole Cards Tests
    @Test
    void whenAddingCardsToHoleCards_shouldBeAccessible() {
        Card card1 = new Card(14, "Hearts", "Ace");
        Card card2 = new Card(13, "Spades", "King");

        player.getHoleCards().add(card1);
        player.getHoleCards().add(card2);

        assertEquals(2, player.getHoleCards().size());
        assertTrue(player.getHoleCards().contains(card1));
        assertTrue(player.getHoleCards().contains(card2));
    }

    // Best Hand Tests
    @Test
    void whenAddingCardsToBestHand_shouldBeAccessible() {
        Card card = new Card(12, "Diamonds", "Queen");
        player.getBestHand().add(card);

        assertEquals(1, player.getBestHand().size());
        assertTrue(player.getBestHand().contains(card));
    }

    // Edge Cases and Integration Tests
    @Test
    void whenPlayerGoesAllInWithSmallChipCount_shouldWorkCorrectly() {
        Player smallChipPlayer = new Player("Bob", "player-456", 25);

        int pot = smallChipPlayer.doAction(PlayerAction.ALL_IN, 0, 5);

        assertEquals(0, smallChipPlayer.getChips());
        assertEquals(25, smallChipPlayer.getCurrentBet());
        assertEquals(30, pot);
        assertTrue(smallChipPlayer.getIsAllIn());
    }

    @Test
    void whenPlayerWithZeroChipsGoesAllIn_shouldNotChangeAnything() {
        Player brokePlayer = new Player("Broke", "player-000", 0);

        int pot = brokePlayer.doAction(PlayerAction.ALL_IN, 0, 10);

        assertEquals(0, brokePlayer.getChips());
        assertEquals(0, brokePlayer.getCurrentBet());
        assertEquals(10, pot); // Pot unchanged
        assertTrue(brokePlayer.getIsAllIn());
    }

    @Test
    void whenMultipleActionsInSequence_shouldMaintainCorrectState() {
        // Simulate a betting sequence
        player.doAction(PlayerAction.BET, 10, 0);
        assertEquals(90, player.getChips());
        assertEquals(10, player.getCurrentBet());

        // Reset for new round
        player.resetCurrentBet();
        assertEquals(0, player.getCurrentBet());
        assertEquals(90, player.getChips()); // Chips remain reduced

        // Another action
        player.doAction(PlayerAction.RAISE, 20, 5);
        assertEquals(70, player.getChips());
        assertEquals(20, player.getCurrentBet());
    }
}
