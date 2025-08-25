package com.pokergame.dto;

import jakarta.validation.constraints.*;

/**
 * DTO for creating a new poker game
 */
public record CreateGameRequest(

        @NotBlank(message = "Game name is required")
        @Size(min = 3, max = 50, message = "Game name must be between 3 and 50 characters")
        String gameName,

        @Min(value = 2, message = "Minimum 2 players required")
        @Max(value = 10, message = "Maximum 10 players allowed")
        int maxPlayers,

        @Min(value = 10, message = "Starting chips must be at least 10")
        @Max(value = 10000, message = "Starting chips cannot exceed 10,000")
        int startingChips,

        @Min(value = 1, message = "Small blind must be at least 1")
        @Max(value = 100, message = "Small blind cannot exceed 100")
        int smallBlind,

        @Min(value = 2, message = "Big blind must be at least 2")
        @Max(value = 200, message = "Big blind cannot exceed 200")
        int bigBlind,

        @Size(max = 200, message = "Game description cannot exceed 200 characters")
        String description

) {
    public CreateGameRequest {
        // Validate big blind is at least double small blind
        if (bigBlind < smallBlind * 2) {
            throw new IllegalArgumentException("Big blind must be at least double the small blind");
        }

        // Validate starting chips are reasonable compared to blinds
        if (startingChips < bigBlind * 20) {
            throw new IllegalArgumentException("Starting chips should be at least 20 times the big blind");
        }
    }
}
