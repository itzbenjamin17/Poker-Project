import { useEffect, useRef, useState } from 'react';

/**
 * Custom hook for managing WebSocket connections to poker game rooms.
 * Handles room updates, player joining/leaving events, and game start notifications.
 * 
 * @param {string} roomId - The unique identifier for the room
 * @param {string} playerName - The name of the current player
 * @param {function} onRoomUpdate - Callback for room state updates
 * @param {function} onGameStarted - Callback when game starts
 * @returns {object} Connection status and utilities
 */
export const useRoomWebSocket = (roomId, playerName, onRoomUpdate, onGameStarted) => {
    const [connected, setConnected] = useState(false);
    const wsRef = useRef(null);

    useEffect(() => {
        if (!roomId || !playerName) return;

        const handleWebSocketMessage = (message) => {
            switch (message.type) {
                case 'ROOM_UPDATE':
                case 'PLAYER_JOINED':
                case 'PLAYER_LEFT':
                    onRoomUpdate(message.data);
                    break;
                case 'JOINED_ROOM':
                    // Confirmation that we successfully joined the room
                    setConnected(true);
                    break;
                case 'ROOM_CLOSED':
                    onRoomUpdate(null); // Signal room was closed
                    break;
                case 'GAME_STARTED':
                    if (onGameStarted) {
                        onGameStarted(message.data);
                    }
                    break;
                default: 
                    // Unknown message type - could be logged in development
                    if (process.env.NODE_ENV === 'development') {
                        console.warn('Unknown WebSocket message type:', message.type);
                    }
            }
        };

        // Connect to WebSocket
        const ws = new WebSocket('ws://localhost:8080/ws/room');
        wsRef.current = ws;

        ws.onopen = () => {
            setConnected(true);
            // Join room on connection
            ws.send(JSON.stringify({
                type: 'JOIN_ROOM',
                roomId,
                playerName
            }));
        };

        ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                handleWebSocketMessage(message);
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
            }
        };

        ws.onclose = (event) => {
            setConnected(false);
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            setConnected(false);
        };

        return () => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'LEAVE_ROOM',
                    roomId,
                    playerName
                }));
            }
            ws.close();
        };
    }, [roomId, playerName, onRoomUpdate, onGameStarted]);

    return { connected };
};