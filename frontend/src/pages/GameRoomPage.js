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
    
    // Use refs to avoid useCallback dependency issues
    const showdownStateRef = useRef(null)
    const showdownStartTimeRef = useRef(null)
    const isShowingShowdownRef = useRef(false)

    // Handle WebSocket game state updates
    const handleGameStateUpdate = useCallback((newGameState) => {
        console.log('Received game state update:', newGameState);
        
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

                {/* Only show betting controls if it's the current player's turn AND not in showdown */}
                {gameState.phase !== 'SHOWDOWN' && gameState.players.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="betting-controls">
                        <div className="action-buttons">
                            <button onClick={() => handleAction("FOLD")} className="btn btn-danger action-btn">
                                Fold
                            </button>
                            
                            {/* Show CHECK only if player's current bet matches highest bet */}
                            {(() => {
                                const currentPlayer = gameState.players.find(p => p.name === playerName);
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
                                    </>
                                );
                            })()}
                        </div>

                        <div className="bet-amount">
                            <label>Raise Amount:</label>
                            <input 
                                type="number" 
                                min={(() => {
                                    const currentPlayer = gameState.players.find(p => p.name === playerName);
                                    const playerCurrentBet = currentPlayer?.currentBet || 0;
                                    return gameState.currentBet - playerCurrentBet + 1; // Minimum raise
                                })()} 
                                value={betAmount || (() => {
                                    const currentPlayer = gameState.players.find(p => p.name === playerName);
                                    const playerCurrentBet = currentPlayer?.currentBet || 0;
                                    const minRaise = gameState.currentBet - playerCurrentBet + 1;
                                    return Math.max(minRaise, gameState.currentBet * 2);
                                })()} 
                                onChange={(e) => setBetAmount(parseInt(e.target.value) || 0)}
                            />
                        </div>
                    </div>
                )}

                {/* Show all players' cards during showdown */}
                {(isShowingShowdown || gameState.phase === 'SHOWDOWN') && (
                    <div className="showdown-section">
                        <h3>Showdown - Best Hands</h3>
                        
                        {/* Use showdown state if available, otherwise current game state */}
                        {(() => {
                            const displayState = isShowingShowdown && showdownState ? showdownState : gameState;
                            return (
                                <>
                                    {/* Show debug info */}
                                    <div style={{fontSize: '12px', color: '#ccc', marginBottom: '10px'}}>
                                        Debug: Winners array: {JSON.stringify(displayState.winners || [])} | 
                                        Players with isWinner: {displayState.players?.filter(p => p.isWinner).map(p => p.name).join(', ') || 'none'} |
                                        Winnings per player: {displayState.winningsPerPlayer || 'not available'} |
                                        {isShowingShowdown && `Showing cached showdown (${Math.max(0, 12000 - (Date.now() - (showdownStartTime || 0)))}ms remaining)`}
                                    </div>
                                    
                                    {/* Show winners if available - check both winners array and isWinner flags */}
                                    {((displayState.winners && displayState.winners.length > 0) || 
                                      (displayState.players && displayState.players.some(p => p.isWinner))) && (
                                        <div className="winners-announcement">
                                            <h4 className="winners-title">
                                                🏆 Winner{(displayState.winners?.length > 1 || displayState.players?.filter(p => p.isWinner).length > 1) ? 's' : ''}: 
                                                {displayState.winners?.join(', ') || displayState.players?.filter(p => p.isWinner).map(p => p.name).join(', ')}
                                            </h4>
                                            <p>
                                                {displayState.players?.filter(p => p.isWinner).map(winner => 
                                                    `${winner.name}: +$${winner.chipsWon || displayState.winningsPerPlayer || 0}`
                                                ).join(', ') || `Pot won: $${displayState.winningsPerPlayer || Math.floor(displayState.pot / (displayState.winners?.length || displayState.players?.filter(p => p.isWinner).length || 1))} each`}
                                            </p>
                                        </div>
                                    )}
                                    
                                    <div className="showdown-players">
                                        {displayState.players?.filter(p => !p.hasFolded).map((player, index) => (
                                            <div key={index} className={`showdown-player ${player.isWinner ? 'winner' : ''}`}>
                                                <h4>
                                                    {player.name} - ${player.chips} chips
                                                    {player.isWinner && ' 🏆'}
                                                    {player.isWinner && player.chipsWon && ` (+$${player.chipsWon})`}
                                                </h4>
                                                <div className="player-hand">
                                                    <p className="hand-label">Best Hand:</p>
                                                    {player.cards && player.cards.length > 0 ? 
                                                        player.cards.map((card, cardIndex) => (
                                                            <div key={cardIndex} className="card showdown-card">
                                                                {formatCard(card)}
                                                            </div>
                                                        )) : 
                                                        <div style={{color: '#ccc', fontSize: '14px'}}>No cards available</div>
                                                    }
                                                </div>
                                                {player.handRank && (
                                                    <p className="hand-rank">Hand: {player.handRank}</p>
                                                )}
                                                <div style={{fontSize: '12px', color: '#999'}}>
                                                    Debug: isWinner={String(player.isWinner)}, cards count={player.cards?.length || 0}, chipsWon={player.chipsWon || 0}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                    
                                    <div className="showdown-message">
                                        {((displayState.winners && displayState.winners.length > 0) || 
                                          (displayState.players && displayState.players.some(p => p.isWinner))) ? (
                                            <p>🎉 Hand complete! New hand will start shortly...</p>
                                        ) : (
                                            <p>Determining winner... New hand will start shortly.</p>
                                        )}
                                    </div>
                                </>
                            );
                        })()}
                    </div>
                )}

                {/* Show validation errors within the game */}
                {error && error.includes("Action failed") && (
                    <div className="alert alert-danger mt-3">
                        <strong>Invalid Action:</strong> {error.replace("Action failed: ", "")}
                    </div>
                )}

                {/* Show waiting message if it's not the player's turn and not in showdown */}
                {gameState.phase !== 'SHOWDOWN' && !gameState.players.find(p => p.isCurrentPlayer && p.name === playerName) && (
                    <div className="waiting-message">
                        <p>Waiting for {gameState.players.find(p => p.isCurrentPlayer)?.name || 'other player'} to act...</p>
                    </div>
                )}
            </div>
        </div>
    )
}

export default GameRoomPage
