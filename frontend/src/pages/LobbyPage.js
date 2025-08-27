"use client"

import { useState, useEffect } from "react"
import { useNavigate, useParams, useLocation, Link } from "react-router-dom"

function LobbyPage() {
    const navigate = useNavigate()
    const { id: roomId } = useParams()
    const location = useLocation()
    const { playerName, formData } = location.state || {}
    
    const [players, setPlayers] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")

    useEffect(() => {
        // Add the room creator as the first player
        if (playerName) {
            setPlayers([{ 
                id: 1, 
                name: playerName, 
                isHost: true,
                joinedAt: new Date().toLocaleTimeString()
            }])
        }

        // Simulate other players joining (for demo purposes)
        const interval = setInterval(() => {
            const demoPlayers = [
                "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"
            ]
            
            setPlayers(currentPlayers => {
                if (currentPlayers.length < (formData?.maxPlayers || 6) && Math.random() > 0.7) {
                    const availableNames = demoPlayers.filter(
                        name => !currentPlayers.some(p => p.name === name)
                    )
                    
                    if (availableNames.length > 0) {
                        const randomName = availableNames[Math.floor(Math.random() * availableNames.length)]
                        return [...currentPlayers, {
                            id: currentPlayers.length + 1,
                            name: randomName,
                            isHost: false,
                            joinedAt: new Date().toLocaleTimeString()
                        }]
                    }
                }
                return currentPlayers
            })
        }, 3000)

        return () => clearInterval(interval)
    }, [playerName, formData?.maxPlayers])

    const handleStartGame = async () => {
        if (players.length < 2) {
            setError("Need at least 2 players to start the game")
            return
        }

        setLoading(true)
        setError("")

        try {
            // In a real app, you would make an API call to start the game
            // await axios.post(`http://localhost:8080/api/game/${roomId}/start`);
            
            // Simulate a short delay
            await new Promise(resolve => setTimeout(resolve, 1000))
            
            navigate(`/room/${roomId}`, { state: { players, gameSettings: formData } })
        } catch (error) {
            console.error("Error starting game:", error)
            setError("Failed to start game. Please try again.")
        } finally {
            setLoading(false)
        }
    }

    const handleLeaveRoom = () => {
        navigate("/")
    }

    const isHost = players.find(p => p.name === playerName)?.isHost || false
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
                    <h2>{formData?.roomName || "Poker Room"}</h2>
                    <div className="room-details">
                        <div className="detail-item">
                            <span className="label">Room ID:</span>
                            <span className="value">{roomId}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Max Players:</span>
                            <span className="value">{formData?.maxPlayers || 6}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Buy-in:</span>
                            <span className="value">${formData?.buyIn || 100}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Blinds:</span>
                            <span className="value">${formData?.smallBlind || 1}/${formData?.bigBlind || 2}</span>
                        </div>
                        <div className="detail-item">
                            <span className="label">Type:</span>
                            <span className="value">{formData?.password ? "Private" : "Public"}</span>
                        </div>
                    </div>
                </div>

                <div className="players-section">
                    <div className="players-header">
                        <h3>Players ({players.length}/{formData?.maxPlayers || 6})</h3>
                        <div className="waiting-indicator">
                            {players.length < 2 ? "Waiting for more players..." : "Ready to start!"}
                        </div>
                    </div>

                    <div className="players-list">
                        {players.map((player) => (
                            <div key={player.id} className={`player-card ${player.isHost ? 'host' : ''}`}>
                                <div className="player-avatar">
                                    {player.name.charAt(0).toUpperCase()}
                                </div>
                                <div className="player-details">
                                    <div className="player-name">
                                        {player.name}
                                        {player.isHost && <span className="host-badge">HOST</span>}
                                    </div>
                                    <div className="join-time">Joined at {player.joinedAt}</div>
                                </div>
                                <div className="player-status">
                                    <span className="status-indicator ready"></span>
                                    Ready
                                </div>
                            </div>
                        ))}

                        {/* Empty slots */}
                        {Array.from({ length: (formData?.maxPlayers || 6) - players.length }).map((_, index) => (
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
