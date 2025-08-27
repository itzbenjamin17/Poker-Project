"use client"

import { useState, useEffect, useCallback } from "react"
import { useNavigate, useParams, useLocation, Link } from "react-router-dom"
import { useRoomWebSocket } from "../hooks/useRoomWebSocket"

function LobbyPage() {
    const navigate = useNavigate()
    const { id: roomId } = useParams()
    const location = useLocation()
    const { playerName, formData } = location.state || {}
    
    const [players, setPlayers] = useState([])
    const [roomInfo, setRoomInfo] = useState(null) // Add this state
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")



    const handleRoomUpdate = useCallback((roomData) => {
        if (roomData === null) {
            // Room was closed
            navigate("/", { 
                state: { 
                    message: "The room has been closed by the host." 
                } 
            });
            return;
        }

        // Smart merge: preserve existing room info but update players
        if (roomData.players) {
            setPlayers(roomData.players);
        }
        
        // Only update room info if we have meaningful data
        // This prevents WebSocket updates from overriding REST API data
        setRoomInfo(prevRoomInfo => {
            if (!prevRoomInfo) {
                return roomData;
            }
            
            // Merge, keeping existing room metadata but updating dynamic data
            return {
                ...prevRoomInfo,
                players: roomData.players || prevRoomInfo.players,
                // Keep the original room creation data intact
                roomName: prevRoomInfo.roomName,
                maxPlayers: prevRoomInfo.maxPlayers,
                buyIn: prevRoomInfo.buyIn,
                smallBlind: prevRoomInfo.smallBlind,
                bigBlind: prevRoomInfo.bigBlind,
                createdAt: prevRoomInfo.createdAt,
                hostName: prevRoomInfo.hostName
            };
        });
        
        setError("");
        }, [navigate]);

    const { connected } = useRoomWebSocket(roomId, playerName, handleRoomUpdate);

    // Fetch initial room data when component first loads (for direct navigation/refresh)
    const fetchInitialRoomData = useCallback(async () => {
        try {
            console.log('Fetching initial room info for roomId:', roomId);
            console.log('Current playerName:', playerName);
            
            const response = await fetch(`http://localhost:8080/api/game/room/${roomId}`);
            
            console.log('Response status:', response.status);
            
            if (response.ok) {
                const roomData = await response.json();
                console.log('Initial room data received:', roomData);
                
                setPlayers(roomData.players || []);
                setRoomInfo(roomData);
                setError(""); // Clear any previous errors
            } else {
                console.error('Failed to fetch initial room info:', response.status, response.statusText);
                const errorText = await response.text();
                console.error('Error response:', errorText);
                
                if (response.status === 404) {
                    // Room was destroyed (likely host left) - redirect to home
                    console.log('Room not found - redirecting to home');
                    navigate("/", { 
                        state: { 
                            message: "The room has been closed by the host." 
                        } 
                    });
                    return; // Exit early to prevent setting error state
                } else {
                    setError(`Failed to load room info: ${response.status} - ${errorText}`);
                }
            }
        } catch (error) {
            console.error('Error fetching initial room info:', error);
            setError('Failed to connect to server');
        }
    }, [roomId, playerName, navigate]);

    useEffect(() => {
        // Fetch initial room info when component mounts (for page refresh/direct navigation)
        fetchInitialRoomData();
        
        // No polling - WebSocket will handle real-time updates
    }, [fetchInitialRoomData]);

    const handleStartGame = async () => {
        try {
            setLoading(true);
            setError("");
            
            if (players.length < 2) {
                setError('Need at least 2 players to start the game');
                return;
            }

            // START ACTUAL GAME from room
            const response = await fetch(`http://localhost:8080/api/game/room/${roomId}/start-game`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    playerName: playerName,
                })
            });

            if (!response.ok) {
                const errorData = await response.text();
                throw new Error(errorData || 'Failed to start game');
            }

            // Now navigate to the actual game
            navigate(`/game/${roomId}`);
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    const handleLeaveRoom = async () => {
        try {
            // Call the API to properly leave the room
            await fetch(`http://localhost:8080/api/game/room/${roomId}/leave`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    playerName: playerName
                })
            });

            // Navigate back to home regardless of API response
            // (in case the room was already destroyed)
            navigate("/");
        } catch (error) {
            console.error('Error leaving room:', error);
            // Still navigate home even if API call fails
            navigate("/");
        }
    }


    // Update the host check to use roomInfo data
    const isHost = roomInfo && playerName ? 
        players.find(p => p.name === playerName)?.isHost || false : 
        false;
    const canStartGame = isHost && players.length >= 2

    return (
        <div className="lobby-page">
            <div className="page-header">
                <Link to="/" className="back-button">
                    ← Leave Room
                </Link>
                <h1>Game Lobby</h1>
            </div>

            <div className="lobby-container">
                <div className="room-info-card">
                    <h2>{roomInfo?.roomName || formData?.roomName || "Poker Room"}</h2>
                    <div className="connection-status">
                        <span className={`status-indicator ${connected ? 'connected' : 'disconnected'}`}></span>
                        {connected ? 'Connected' : 'Connecting...'}
                    </div>
                    <div className="room-details">
                        <div className="detail-item">
                            <span className="label">Max Players:</span>
                            <span className="value">{roomInfo?.maxPlayers || formData?.maxPlayers || 6}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Buy-in:</span>
                            <span className="value">${roomInfo?.buyIn || formData?.buyIn || 100}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Blinds:</span>
                            <span className="value">${roomInfo?.smallBlind || formData?.smallBlind || 1}/${roomInfo?.bigBlind || formData?.bigBlind || 2}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Type:</span>
                            <span className="value">{formData?.password ? "Private" : "Public"}</span>
                        </div>
                    </div>
                </div>

                <div className="players-section">
                    <div className="players-header">
                        <h3>Players ({players.length}/{roomInfo?.maxPlayers || formData?.maxPlayers || 6})</h3>
                        <div className="waiting-indicator">
                            {players.length < 2 ? "Waiting for more players..." : "Ready to start!"}
                        </div>
                    </div>

                    <div className="players-list">
                        {players.map((player, index) => (
                            <div key={index} className={`player-card ${player?.isHost ? 'host' : ''}`}>
                                <div className="player-avatar">
                                    {player?.name ? player.name.charAt(0).toUpperCase() : '?'}
                                </div>
                                <div className="player-details">
                                    <div className="player-name">
                                        {player?.name || 'Unknown Player'}
                                        {player?.isHost && <span className="host-badge">HOST</span>}
                                    </div>
                                    <div className="join-time">Joined {player?.joinedAt || "recently"}</div>
                                </div>
                                <div className="player-status">
                                    <span className="status-indicator ready"></span>
                                    Ready
                                </div>
                            </div>
                        ))}

                        {/* Empty slots */}
                        {Array.from({ length: (roomInfo?.maxPlayers || formData?.maxPlayers || 6) - players.length }).map((_, index) => (
                            <div key={`empty-${index}`} className="player-card empty">
                                <div className="player-avatar empty">
                                    ?
                                </div>
                                <div className="player-details">
                                    <div className="player-name">Waiting for player...</div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {error && <p className="error-message">{error}</p>}

                <div className="lobby-actions">
                    {isHost ? (
                        <button
                            onClick={handleStartGame}
                            disabled={!canStartGame || loading}
                            className={`btn ${canStartGame ? 'btn-primary' : 'btn-secondary'}`}
                        >
                            {loading ? "Starting Game..." : "Start Game"}
                        </button>
                    ) : (
                        <div className="waiting-message">
                            Waiting for the host to start the game...
                        </div>
                    )}
                    
                    <button onClick={handleLeaveRoom} className="btn btn-danger">
                        Leave Room
                    </button>
                </div>
            </div>
        </div>
    )
}

export default LobbyPage