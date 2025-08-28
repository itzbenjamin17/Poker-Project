import { useEffect, useRef, useState } from 'react';

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
                    // Confirmation that we successfully joined - could update connection status
                    console.log('Successfully joined room:', message.roomId);
                    break;
                case 'ROOM_CLOSED':
                    onRoomUpdate(null); // Signal room was closed
                    break;
                case 'GAME_STARTED':
                    console.log('Game started! Redirecting to game...', message.data);
                    if (onGameStarted) {
                        onGameStarted(message.data);
                    }
                    break;
                default: 
                    console.warn('Unknown WebSocket message type:', message.type);
            }
        };

        // Connect to WebSocket
        const ws = new WebSocket('ws://localhost:8080/ws/room');
        wsRef.current = ws;

        ws.onopen = () => {
            console.log('WebSocket connected to room:', roomId);
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
                console.log('WebSocket message received:', message);
                handleWebSocketMessage(message);
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
            }
        };

        ws.onclose = (event) => {
            console.log('WebSocket disconnected:', event.code, event.reason);
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