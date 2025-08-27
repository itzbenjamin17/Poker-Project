"use client"

import { useState } from "react"
import { useParams, useNavigate } from "react-router-dom"

function GameRoomPage() {
    const { id } = useParams()
    const navigate = useNavigate()
    const [gameState, setGameState] = useState({
        roomName: `Room ${id}`,
        pot: 450,
        currentBet: 50,
        maxPlayers: 6,
        players: [
            { id: 1, name: "You", chips: 2000, status: "active", cards: ["A♠", "K♥"], isCurrentPlayer: true },
            { id: 2, name: "Alice", chips: 1800, status: "active", cards: ["?", "?"] },
            { id: 3, name: "Bob", chips: 2200, status: "folded", cards: ["?", "?"] },
            { id: 4, name: "Charlie", chips: 1500, status: "active", cards: ["?", "?"] },
        ],
        communityCards: ["Q♠", "10♦", "9♣"],
        phase: "flop",
    })
    const [playerName, setPlayerName] = useState("Player")
    const [isJoined, setIsJoined] = useState(true) // Set to true for demo
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")

    const handleAction = async (action, amount = 0) => {
        console.log(`[v0] Player action: ${action}`, amount)
        // In a real app, you would make an API call here
        // try {
        //   await axios.post(`http://localhost:8080/api/game/${id}/action`, {
        //     action,
        //     amount
        //   });
        //   fetchGameState();
        // } catch (error) {
        //   console.error('Error performing action:', error);
        // }
    }

    const leaveRoom = () => {
        navigate("/")
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
