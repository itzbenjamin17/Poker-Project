"use client"

import { useState, useEffect, useCallback, useRef } from "react"
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
    const [betAmount, setBetAmount] = useState(0) // Add state for bet amount
    
    // State for showdown display timing
    const [showdownState, setShowdownState] = useState(null)
    const [showdownStartTime, setShowdownStartTime] = useState(null)
    const [isShowingShowdown, setIsShowingShowdown] = useState(false)
    
    // State for auto-advance
    const [isAutoAdvancing, setIsAutoAdvancing] = useState(false)
    const [autoAdvanceMessage, setAutoAdvanceMessage] = useState("")
    
    // Use refs to avoid useCallback dependency issues
    const showdownStateRef = useRef(null)
    const showdownStartTimeRef = useRef(null)
    const isShowingShowdownRef = useRef(false)

    // Handle WebSocket game state updates
    const handleGameStateUpdate = useCallback((newGameState) => {
        console.log('Received game state update:', newGameState);
        
        // Check for auto-advance state
        if (newGameState.isAutoAdvancing !== undefined) {
            console.log('Auto-advance state:', newGameState.isAutoAdvancing, newGameState.autoAdvanceMessage);
            setIsAutoAdvancing(newGameState.isAutoAdvancing);
            setAutoAdvanceMessage(newGameState.autoAdvanceMessage || "");
        }
        
        // If this is just an auto-advance notification without full game state, don't update gameState
        if (newGameState._isNotificationOnly) {
            console.log('Received auto-advance notification only, not updating full game state');
            return;
        }
        
        // Extra debugging for showdown
        if (newGameState.phase === 'SHOWDOWN') {
            console.log('SHOWDOWN detected!');
            console.log('Winners:', newGameState.winners);
            console.log('Winner count:', newGameState.winnerCount);
            console.log('Players:', newGameState.players);
            console.log('Any winners in players?', newGameState.players?.some(p => p.isWinner));
            
            // Check if any player has isWinner flag
            if (newGameState.players) {
                newGameState.players.forEach(player => {
                    console.log(`Player ${player.name}: isWinner=${player.isWinner}, handRank=${player.handRank}`);
                });
            }
            
            // Capture showdown state and start timer
            setShowdownState(newGameState);
            setShowdownStartTime(Date.now());
            setIsShowingShowdown(true);
            showdownStateRef.current = newGameState;
            showdownStartTimeRef.current = Date.now();
            isShowingShowdownRef.current = true;
        } else if (isShowingShowdownRef.current && newGameState.phase === 'PRE_FLOP') {
            // If we receive a new hand state while showing showdown, delay the transition
            console.log('New hand state received while showing showdown, delaying transition...');
            const elapsed = Date.now() - showdownStartTimeRef.current;
            const remainingTime = Math.max(0, 12000 - elapsed); // Show showdown for at least 12 seconds
            
            setTimeout(() => {
                console.log('Showdown display time complete, switching to new hand');
                setIsShowingShowdown(false);
                setShowdownState(null);
                setShowdownStartTime(null);
                isShowingShowdownRef.current = false;
                showdownStateRef.current = null;
                showdownStartTimeRef.current = null;
                setGameState(newGameState);
            }, remainingTime);
            
            return; // Don't update game state immediately
        } else {
            // Normal state update
            setIsShowingShowdown(false);
            setShowdownState(null);
            setShowdownStartTime(null);
            isShowingShowdownRef.current = false;
            showdownStateRef.current = null;
            showdownStartTimeRef.current = null;
        }
        
        setGameState(newGameState);
        
        // Find the current player data
        const player = newGameState.players?.find(p => p.name === playerName);
        setCurrentPlayer(player);
        
        // Set default bet amount (minimum raise or double current bet)
        const currentPlayerData = newGameState.players?.find(p => p.name === playerName);
        const playerCurrentBet = currentPlayerData?.currentBet || 0;
        const minRaise = (newGameState.currentBet || 0) - playerCurrentBet + 1;
        const defaultRaise = Math.max(minRaise, (newGameState.currentBet || 0) * 2);
        setBetAmount(defaultRaise);
        
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

    // Auto-clear validation errors after 5 seconds
    useEffect(() => {
        if (error && error.includes("Action failed")) {
            const timer = setTimeout(() => {
                setError("")
            }, 5000)
            
            return () => clearTimeout(timer)
        }
    }, [error])

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

    // Show error state only for critical errors (not validation errors)
    if (error && (error.includes("Failed to load game") || error.includes("Failed to connect"))) {
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
    if (!gameState || !gameState.players || !gameState.communityCards) {
        return (
            <div className="game-room">
                <h2>Loading game state...</h2>
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
              Players: {gameState.players?.length || 0}/{gameState.maxPlayers}
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
                            {(gameState.communityCards || []).map((card, index) => (
                                <div key={index} className="card community-card">
                                    {formatCard(card)}
                                </div>
                            ))}
                            {/* Placeholder cards */}
                            {Array.from({ length: 5 - (gameState.communityCards?.length || 0) }).map((_, index) => (
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
                        {(gameState.players || []).map((player, index) => {
                            // Check if this player is a winner (during showdown or using cached showdown state)
                            const displayState = isShowingShowdown && showdownState ? showdownState : gameState;
                            const isWinner = player.isWinner || (displayState.winners && displayState.winners.includes(player.name));
                            const isShowdown = isShowingShowdown || gameState.phase === 'SHOWDOWN';
                            
                            return (
                                <div
                                    key={player.id}
                                    className={`player-seat ${player.status} ${player.isCurrentPlayer ? "current-player" : ""} ${isWinner && isShowdown ? "winner-player" : ""}`}
                                    style={getPlayerPosition(index, gameState.players?.length || 1)}
                                >
                                    {/* Winner overlay */}
                                    {isWinner && isShowdown && (
                                        <div className="winner-overlay">
                                            <div className="winner-badge">
                                                🏆 WINNER
                                            </div>
                                            <div className="winner-hand">
                                                {player.handRank || "Best Hand"}
                                            </div>
                                            {player.chipsWon && (
                                                <div className="winner-amount">
                                                    +${player.chipsWon}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    
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
                            );
                        })}
                    </div>
                </div>
            </div>

            {/* Action Panel */}
            <div className="action-panel">
                <div className="my-hand">
                    <h3>Your Hand</h3>
                    <div className="hand-cards">
                        {gameState.players
                            ?.find((p) => p.name === playerName)
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

                {/* Only show betting controls if it's the current player's turn AND not in showdown AND not auto-advancing */}
                {!isAutoAdvancing && gameState.phase !== 'SHOWDOWN' && gameState.players?.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="betting-controls">
                        <div className="action-buttons">
                            <button onClick={() => handleAction("FOLD")} className="btn btn-danger action-btn">
                                Fold
                            </button>
                            
                            {/* Show CHECK only if player's current bet matches highest bet */}
                            {(() => {
                                const currentPlayer = gameState.players?.find(p => p.name === playerName);
                                const playerCurrentBet = currentPlayer?.currentBet || 0;
                                const canCheck = playerCurrentBet === gameState.currentBet;
                                const needsToCall = gameState.currentBet > playerCurrentBet;
                                
                                return (
                                    <>
                                        {canCheck && (
                                            <button onClick={() => handleAction("CHECK")} className="btn btn-secondary action-btn">
                                                Check
                                            </button>
                                        )}
                                        
                                        {needsToCall && (
                                            <button onClick={() => handleAction("CALL")} className="btn btn-primary action-btn">
                                                Call ${gameState.currentBet - playerCurrentBet}
                                            </button>
                                        )}
                                        
                                        <button
                                            onClick={() => handleAction("RAISE", betAmount)}
                                            className="btn btn-success action-btn"
                                        >
                                            Raise to ${betAmount}
                                        </button>
                                        
                                        <button 
                                            onClick={() => handleAction("ALL_IN")} 
                                            className="btn btn-warning action-btn"
                                        >
                                            All In (${currentPlayer?.chips || 0})
                                        </button>
                                    </>
                                );
                            })()}
                        </div>

                        <div className="bet-amount">
                            <label>Raise Amount:</label>
                            <input 
                                type="number" 
                                min={(() => {
                                    const currentPlayer = gameState.players?.find(p => p.name === playerName);
                                    const playerCurrentBet = currentPlayer?.currentBet || 0;
                                    return gameState.currentBet - playerCurrentBet + 1; // Minimum raise
                                })()} 
                                value={betAmount || (() => {
                                    const currentPlayer = gameState.players?.find(p => p.name === playerName);
                                    const playerCurrentBet = currentPlayer?.currentBet || 0;
                                    const minRaise = gameState.currentBet - playerCurrentBet + 1;
                                    return Math.max(minRaise, gameState.currentBet * 2);
                                })()} 
                                onChange={(e) => setBetAmount(parseInt(e.target.value) || 0)}
                            />
                        </div>
                    </div>
                )}

                {/* Show auto-advance message */}
                {isAutoAdvancing && (
                    <div className="auto-advance-notification">
                        <div className="auto-advance-message">
                            🎰 {autoAdvanceMessage}
                        </div>
                        <div className="auto-advance-details">
                            Please wait while the remaining cards are revealed...
                        </div>
                    </div>
                )}

                {/* Show winner announcement during showdown */}
                {(isShowingShowdown || gameState.phase === 'SHOWDOWN') && (() => {
                    const displayState = isShowingShowdown && showdownState ? showdownState : gameState;
                    const winners = displayState.players?.filter(p => p.isWinner) || [];
                    
                    if (winners.length > 0) {
                        return (
                            <div className="winner-announcement">
                                <div className="winner-message">
                                    � {winners.length === 1 ? 'Winner' : 'Winners'}: {winners.map(w => w.name).join(', ')}
                                    {winners.length === 1 && winners[0].handRank && ` with ${winners[0].handRank}`}
                                </div>
                                <div className="winner-details">
                                    Pot: ${displayState.pot} • New hand starting soon...
                                </div>
                            </div>
                        );
                    }
                    return null;
                })()}

                {/* Show validation errors within the game */}
                {error && error.includes("Action failed") && (
                    <div className="alert alert-danger mt-3">
                        <strong>Invalid Action:</strong> {error.replace("Action failed: ", "")}
                    </div>
                )}

                {/* Show waiting message if it's not the player's turn and not in showdown and not auto-advancing */}
                {!isAutoAdvancing && gameState.phase !== 'SHOWDOWN' && !gameState.players?.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="waiting-message">
                        <p>Waiting for {gameState.players?.find(p => p.isCurrentPlayer)?.name || 'other player'} to act...</p>
                    </div>
                )}
            </div>
        </div>
    )
}

export default GameRoomPage
