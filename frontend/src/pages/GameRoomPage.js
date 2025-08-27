"use client"

import { useState, useEffect, useCallback } from "react"
import { useParams, useNavigate, useLocation } from "react-router-dom"

function GameRoomPage() {
    const { id: gameId } = useParams()
    const navigate = useNavigate()
    const location = useLocation()
    const { playerName } = location.state || {}
    
    // State for real game data (replace hardcoded demo data)
    const [gameState, setGameState] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")
    const [currentPlayer, setCurrentPlayer] = useState(null)

    // Fetch initial game state when component mounts
    const fetchGameState = useCallback(async () => {
        try {
            setLoading(true)
            setError("")
            
            const response = await fetch(`http://localhost:8080/api/game/${gameId}/state`)
            
            if (response.ok) {
                const gameData = await response.json()
                setGameState(gameData)
                
                // Find the current player
                const player = gameData.players?.find(p => p.name === playerName)
                setCurrentPlayer(player)
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
    }, [gameId, playerName, navigate])

    useEffect(() => {
        // Fetch initial game state when component mounts
        fetchGameState()
        
        // You might want to set up WebSocket for real-time game updates here
        // Similar to how LobbyPage uses useRoomWebSocket
    }, [fetchGameState])

    const handleAction = async (action, amount = 0) => {
        console.log(`Player action: ${action}`, amount)
        // Make actual API call to perform action
        try {
            const response = await fetch(`http://localhost:8080/api/game/${gameId}/action`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${currentPlayer?.secretToken}` // You'll need this from game state
                },
                body: JSON.stringify({
                    action,
                    amount
                })
            })
            
            if (response.ok) {
                // Refresh game state after action
                fetchGameState()
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
                                    {card}
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
                                    {player.cards.map((card, cardIndex) => (
                                        <div key={cardIndex} className={`card ${player.isCurrentPlayer ? "my-card" : "opponent-card"}`}>
                                            {player.isCurrentPlayer ? card : "🂠"}
                                        </div>
                                    ))}
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
                            .find((p) => p.isCurrentPlayer)
                            ?.cards.map((card, index) => (
                                <div key={index} className="card my-card-large">
                                    {card}
                                </div>
                            ))}
                    </div>
                </div>

                <div className="betting-controls">
                    <div className="action-buttons">
                        <button onClick={() => handleAction("fold")} className="btn btn-danger action-btn">
                            Fold
                        </button>
                        <button onClick={() => handleAction("check")} className="btn btn-secondary action-btn">
                            Check
                        </button>
                        <button onClick={() => handleAction("call")} className="btn btn-primary action-btn">
                            Call ${gameState.currentBet}
                        </button>
                        <button
                            onClick={() => handleAction("raise", gameState.currentBet * 2)}
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
            </div>
        </div>
    )
}

export default GameRoomPage
