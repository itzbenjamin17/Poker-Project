package com.pokergame.dto;

public record WebSocketMessage(
        String type, // "ROOM_UPDATE", "PLAYER_JOINED", "ROOM_CLOSED"
        String roomId,
        Object data // Room data, player info, etc.
) {
}
