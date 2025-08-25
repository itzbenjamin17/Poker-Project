package com.pokergame.model;

import java.util.*;

public record HandEvaluationResult(List<Card> bestHand, HandRank handRank) {

    // Constructor with validation
    public HandEvaluationResult {
        if (bestHand == null || handRank == null) {
            throw new IllegalArgumentException("Best hand and rank cannot be null");
        }
        bestHand = List.copyOf(bestHand);
    }
}


