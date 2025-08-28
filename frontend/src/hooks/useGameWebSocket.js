import { useEffect, useRef, useState } from 'react';

export const useGameWebSocket = (gameId, playerName, onGameStateUpdate) => {
    const [connected, setConnected] = useState(false);
    const wsRef = useRef(null);

    useEffect(() => {
        if (!gameId || !playerName) return;

        const handleWebSocketMessage = (message) => {
            switch (message.type) {
                case 'GAME_STATE_UPDATE':
                    console.log('Game state update received:', message.data);
                    if (onGameStateUpdate) {
                        onGameStateUpdate(message.data);
                    }
                    break;
                case 'PLAYER_ACTION':
                    console.log('Player action:', message.data);
                    if (onGameStateUpdate) {
                        onGameStateUpdate(message.data);
                    }
                    break;
                case 'GAME_ENDED':
                    console.log('Game ended:', message.data);
                    // Handle game ending
                    break;
                default: 
                    console.warn('Unknown game WebSocket message type:', message.type);
            }
        };

        // Connect to WebSocket (reuse the same endpoint as rooms)
        const ws = new WebSocket('ws://localhost:8080/ws/room');
        wsRef.current = ws;

        ws.onopen = () => {
            console.log('Game WebSocket connected to game:', gameId);
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
                console.log('Game WebSocket message received:', message);
                handleWebSocketMessage(message);
            } catch (error) {
                console.error('Error parsing game WebSocket message:', error);
            }
        };

        ws.onclose = (event) => {
            console.log('Game WebSocket disconnected:', event.code, event.reason);
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
