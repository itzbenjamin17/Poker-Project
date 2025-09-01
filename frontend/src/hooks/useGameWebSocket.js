import { useEffect, useRef, useState } from 'react';

/**
 * Custom hook for managing WebSocket connections during active poker games.
 * Handles real-time game state updates, player actions, and game events.
 * 
 * @param {string} gameId - The unique identifier for the game
 * @param {string} playerName - The name of the current player
 * @param {function} onGameStateUpdate - Callback for game state changes
 * @returns {object} Connection status and utilities
 */
export const useGameWebSocket = (gameId, playerName, onGameStateUpdate) => {
    const [connected, setConnected] = useState(false);
    const wsRef = useRef(null);

    useEffect(() => {
        if (!gameId || !playerName) return;

        const handleWebSocketMessage = (message) => {
            switch (message.type) {
                case 'GAME_STATE_UPDATE':
                    if (onGameStateUpdate) {
                        onGameStateUpdate(message.data);
                    }
                    break;
                case 'SHOWDOWN_RESULTS':
                    if (onGameStateUpdate) {
                        onGameStateUpdate(message.data);
                    }
                    break;
                case 'PLAYER_ACTION':
                    if (onGameStateUpdate) {
                        onGameStateUpdate(message.data);
                    }
                    break;
                case 'GAME_END':
                    if (onGameStateUpdate) {
                        // Send game end information
                        onGameStateUpdate({
                            gameEnded: true,
                            winner: message.data.winner,
                            winnerChips: message.data.winnerChips,
                            gameEndMessage: message.data.message,
                            _isNotificationOnly: true
                        });
                    }
                    break;
                case 'ROOM_CLOSED':
                    if (onGameStateUpdate) {
                        onGameStateUpdate({
                            roomClosed: true,
                            closeReason: message.data.reason || "Room closed",
                            _isNotificationOnly: true
                        });
                    }
                    break;
                case 'GAME_ENDED':
                    // Handle legacy game ending
                    break;
                case 'AUTO_ADVANCE_NOTIFICATION':
                    if (onGameStateUpdate) {
                        // Send auto-advance notification without overriding game state
                        onGameStateUpdate({
                            isAutoAdvancing: true,
                            autoAdvanceMessage: message.data.message || "Auto-advancing...",
                            _isNotificationOnly: true
                        });
                    }
                    break;
                case 'AUTO_ADVANCE_COMPLETE':
                    if (onGameStateUpdate) {
                        // Turn off auto-advance state without affecting game state
                        onGameStateUpdate({
                            isAutoAdvancing: false,
                            autoAdvanceMessage: "",
                            _isNotificationOnly: true
                        });
                    }
                    break;
                case 'PLAYER_NOTIFICATION':
                    if (onGameStateUpdate) {
                        // Send player notification without overriding game state
                        onGameStateUpdate({
                            playerNotification: message.data.message,
                            _isNotificationOnly: true
                        });
                    }
                    break;
                default: 
                    // Unknown message type - log in development only
                    if (process.env.NODE_ENV === 'development') {
                        console.warn('Unknown game WebSocket message type:', message.type);
                    }
            }
        };

        // Connect to WebSocket (reuse the same endpoint as rooms)
        const ws = new WebSocket('ws://localhost:8080/ws/room');
        wsRef.current = ws;

        ws.onopen = () => {
            setConnected(true);
            // Join the game room for real-time updates
            ws.send(JSON.stringify({
                type: 'JOIN_ROOM',
                roomId: gameId, // Using gameId as roomId since they're the same
                playerName
            }));
        };

        ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                handleWebSocketMessage(message);
            } catch (error) {
                console.error('Error parsing game WebSocket message:', error);
            }
        };

        ws.onclose = (event) => {
            setConnected(false);
        };

        ws.onerror = (error) => {
            console.error('Game WebSocket error:', error);
            setConnected(false);
        };

        return () => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'LEAVE_ROOM',
                    roomId: gameId,
                    playerName
                }));
            }
            ws.close();
        };
    }, [gameId, playerName, onGameStateUpdate]);

    return { connected };
};
