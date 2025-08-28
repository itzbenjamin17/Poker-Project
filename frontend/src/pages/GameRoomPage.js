"use client"

import { useState, useEffect, useCallback } from "react"
import { useParams, useNavigate, useLocation } from "react-router-dom"
import { useGameWebSocket } from "../hooks/useGameWebSocket"

function GameRoomPage() {
    // Helper function to format card objects for display
    const formatCard = (card) => {
        if (!card || typeof card !== 'object') return '?';
        return `${card.rank || '?'} of ${card.suit || '?'}`;
    };
    const { id: gameId } = useParams()
    const navigate = useNavigate()
    const location = useLocation()
    const { playerName } = location.state || {}
    
    // State for real game data (replace hardcoded demo data)
    const [gameState, setGameState] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")
    const [currentPlayer, setCurrentPlayer] = useState(null)

    // Handle WebSocket game state updates
    const handleGameStateUpdate = useCallback((newGameState) => {
        console.log('Received game state update:', newGameState);
        setGameState(newGameState);
        
        // Find the current player data
        const player = newGameState.players?.find(p => p.name === playerName);
        setCurrentPlayer(player);
        
        setError(""); // Clear any errors
        setLoading(false);
    }, [playerName]);

    // WebSocket connection for real-time game updates
    useGameWebSocket(gameId, playerName, handleGameStateUpdate);

    // Fetch initial game state when component mounts
    const fetchGameState = useCallback(async () => {
        try {
            setLoading(true)
            setError("")
            
            console.log('Fetching game state for player:', playerName);
            const url = `http://localhost:8080/api/game/${gameId}/state${playerName ? `?playerName=${encodeURIComponent(playerName)}` : ''}`;
            console.log('Request URL:', url);
            const response = await fetch(url)
            
            if (response.ok) {
                const gameData = await response.json()
                console.log('Received game data:', gameData);
                handleGameStateUpdate(gameData);
            } else if (response.status === 404) {
                setError("Game not found")
                // Redirect back to home after a delay
                setTimeout(() => navigate("/"), 3000)
            } else {
                const errorText = await response.text()
                setError(`Failed to load game: ${errorText}`)
            }
        } catch (error) {
            console.error('Error fetching game state:', error)
            setError('Failed to connect to server')
        } finally {
            setLoading(false)
        }
    }, [gameId, navigate, handleGameStateUpdate, playerName])

    useEffect(() => {
        // Fetch initial game state when component mounts
        fetchGameState()
    }, [fetchGameState])

    const handleAction = async (action, amount = 0) => {
        console.log(`Player action: ${action}`, amount)
        
        if (!currentPlayer) {
            setError("Player not found")
            return;
        }
        
        // Make actual API call to perform action
        try {
            const response = await fetch(`http://localhost:8080/api/game/${gameId}/action`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    playerName: playerName,
                    action,
                    amount
                })
            })
            
            if (response.ok) {
                // Game state will be updated via WebSocket, no need to manually refresh
                console.log(`Action ${action} submitted successfully`);
            } else {
                const errorText = await response.text()
                setError(`Action failed: ${errorText}`)
            }
        } catch (error) {
            console.error('Error performing action:', error)
            setError('Failed to perform action')
        }
    }

    const leaveRoom = () => {
        navigate("/")
    }

    // Show loading state
    if (loading) {
        return (
            <div className="game-room loading">
                <h2>Loading game...</h2>
            </div>
        )
    }

    // Show error state
    if (error) {
        return (
            <div className="game-room error">
                <h2>Error</h2>
                <p>{error}</p>
                <button onClick={() => navigate("/")} className="btn btn-primary">
                    Back to Home
                </button>
            </div>
        )
    }

    // Show message if no game state loaded
    if (!gameState) {
        return (
            <div className="game-room">
                <h2>Game not found</h2>
                <button onClick={() => navigate("/")} className="btn btn-primary">
                    Back to Home
                </button>
            </div>
        )
    }

    const getPlayerPosition = (index, totalPlayers) => {
        const angle = (index / totalPlayers) * 2 * Math.PI - Math.PI / 2; // Start at top
        const radius = 35; // Percentage from center
        const x = 50 + radius * Math.cos(angle);
        const y = 50 + radius * Math.sin(angle);
        return { left: `${x}%`, top: `${y}%`, transform: 'translate(-50%, -50%)' };
    };

    return (
        <div className="game-room">
            <div className="game-header">
                <div className="room-info">
                    <h2>{gameState.roomName}</h2>
                    <div className="game-stats">
            <span className="stat">
              Players: {gameState.players.length}/{gameState.maxPlayers}
            </span>
                        <span className="stat">Pot: ${gameState.pot}</span>
                        <span className="stat">Phase: {gameState.phase}</span>
                    </div>
                </div>
                <button onClick={leaveRoom} className="btn btn-danger">
                    Leave Room
                </button>
            </div>

            <div className="poker-table">
                <div className="table-felt">
                    {/* Community Cards */}
                    <div className="community-cards">
                        <h3>Community Cards</h3>
                        <div className="cards-container">
                            {gameState.communityCards.map((card, index) => (
                                <div key={index} className="card community-card">
                                    {formatCard(card)}
                                </div>
                            ))}
                            {/* Placeholder cards */}
                            {Array.from({ length: 5 - gameState.communityCards.length }).map((_, index) => (
                                <div key={`placeholder-${index}`} className="card card-placeholder">
                                    ?
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Pot */}
                    <div className="pot-area">
                        <div className="pot-chips">
                            <span className="pot-label">POT</span>
                            <span className="pot-amount">${gameState.pot}</span>
                        </div>
                    </div>

                    {/* Players */}
                    <div className="players-area">
                        {gameState.players.map((player, index) => (
                            <div
                                key={player.id}
                                className={`player-seat ${player.status} ${player.isCurrentPlayer ? "current-player" : ""}`}
                                style={getPlayerPosition(index, gameState.players.length)}
                            >
                                <div className="player-info">
                                    <span className="player-name">{player.name}</span>
                                    <span className="player-chips">${player.chips}</span>
                                    <span className={`player-status ${player.status}`}>{player.status}</span>
                                </div>
                                <div className="player-cards">
                                    {player.cards && player.cards.length > 0 ? (
                                        player.cards.map((card, cardIndex) => (
                                            <div key={cardIndex} className={`card ${player.name === playerName ? "my-card" : "opponent-card"}`}>
                                                {player.name === playerName ? formatCard(card) : "🂠"}
                                            </div>
                                        ))
                                    ) : (
                                        // Show placeholder cards for players without visible cards
                                        Array.from({ length: 2 }).map((_, cardIndex) => (
                                            <div key={cardIndex} className="card opponent-card">
                                                🂠
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Action Panel */}
            <div className="action-panel">
                <div className="my-hand">
                    <h3>Your Hand</h3>
                    <div className="hand-cards">
                        {gameState.players
                            .find((p) => p.name === playerName)
                            ?.cards?.map((card, index) => (
                                <div key={index} className="card my-card-large">
                                    {formatCard(card)}
                                </div>
                            )) || (
                            // Show placeholder if no cards found
                            <div className="no-cards">No cards dealt yet</div>
                        )}
                    </div>
                </div>

                {/* Only show betting controls if it's the current player's turn */}
                {gameState.players.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="betting-controls">
                        <div className="action-buttons">
                            <button onClick={() => handleAction("FOLD")} className="btn btn-danger action-btn">
                                Fold
                            </button>
                            <button onClick={() => handleAction("CHECK")} className="btn btn-secondary action-btn">
                                Check
                            </button>
                            <button onClick={() => handleAction("CALL")} className="btn btn-primary action-btn">
                                Call ${gameState.currentBet}
                            </button>
                            <button
                                onClick={() => handleAction("RAISE", gameState.currentBet * 2)}
                                className="btn btn-success action-btn"
                            >
                                Raise
                            </button>
                        </div>

                        <div className="bet-amount">
                            <label>Bet Amount:</label>
                            <input type="number" min={gameState.currentBet} defaultValue={gameState.currentBet * 2} />
                        </div>
                    </div>
                )}

                {/* Show waiting message if it's not the player's turn */}
                {!gameState.players.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="waiting-message">
                        <p>Waiting for {gameState.players.find(p => p.isCurrentPlayer)?.name || 'other player'} to act...</p>
                    </div>
                )}
            </div>
        </div>
    )
}

export default GameRoomPage
