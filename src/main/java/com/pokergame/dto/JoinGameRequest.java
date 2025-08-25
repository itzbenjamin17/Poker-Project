package com.pokergame.dto;

import jakarta.validation.constraints.*;

/**
 * DTO for joining an existing poker game
 */
public record JoinGameRequest(

        @NotBlank(message = "Game ID is required")
        @Size(min = 8, max = 36, message = "Invalid game ID format")
        String gameId,

        @Size(max = 100, message = "Join message cannot exceed 100 characters")
        String joinMessage,

        @Min(value = 0, message = "Buy-in amount cannot be negative")
        @Max(value = 10000, message = "Buy-in amount cannot exceed 10,000")
        Integer buyInAmount // Optional - null means use game default

) {
}
