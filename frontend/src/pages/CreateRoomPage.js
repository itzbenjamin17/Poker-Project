"use client"

import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"

function CreateRoomPage() {
    const navigate = useNavigate()
    const [formData, setFormData] = useState({
        roomName: "",
        playerName: "",
        maxPlayers: 6,
        smallBlind: 1,
        bigBlind: 2,
        buyIn: 100,
        password: "",
    })
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")

    const handleInputChange = (e) => {
        const { name, value } = e.target
        setFormData((prev) => ({
            ...prev,
            [name]: value,
        }))
    }

   const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError("")

    try {
        console.log('Creating room with data:', formData);
        console.log('Player name being sent:', formData.playerName);
        
        // Create ROOM (not game)
        const response = await fetch('http://localhost:8080/api/game/create-room', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(formData)
        });

        console.log('Create room response status:', response.status);

        if (!response.ok) {
            const errorData = await response.text();
            console.error('Create room error:', errorData);
            throw new Error(errorData || 'Failed to create room');
        }

        const result = await response.json();
        console.log('Create room result:', result);
        const roomId = result.roomId;

        console.log('Navigating to lobby with:', {
            roomId,
            playerName: formData.playerName,
            formData
        });

        // Navigate to lobby with room data
        navigate(`/lobby/${roomId}`, {
            state: {
                isHost: true,
                playerName: formData.playerName,
                formData: formData
            }
        });
    } catch (err) {
        console.error('Error creating room:', err);
        setError(err.message);
    } finally {
        setLoading(false);
    }
}

    return (
        <div className="create-room-page">
            <div className="page-header">
                <Link to="/" className="back-button">
                    ← Back to Home
                </Link>
                <h1>Create Game Room</h1>
            </div>

            <div className="create-room-container">
                <form onSubmit={handleSubmit} className="create-room-form">
                    <div className="form-group">
                        <label htmlFor="playerName">Your Name</label>
                        <input
                            type="text"
                            id="playerName"
                            name="playerName"
                            value={formData.playerName}
                            onChange={handleInputChange}
                            required
                            placeholder="Enter your name"
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="roomName">Room Name</label>
                        <input
                            type="text"
                            id="roomName"
                            name="roomName"
                            value={formData.roomName}
                            onChange={handleInputChange}
                            required
                            placeholder="Enter room name"
                        />
                    </div>

                    <div className="form-row">
                        <div className="form-group">
                            <label htmlFor="maxPlayers">Max Players</label>
                            <select id="maxPlayers" name="maxPlayers" value={formData.maxPlayers} onChange={handleInputChange}>
                                <option value={2}>2 Players</option>
                                <option value={4}>4 Players</option>
                                <option value={6}>6 Players</option>
                                <option value={8}>8 Players</option>
                                <option value={10}>10 Players</option>
                            </select>
                        </div>

                        <div className="form-group">
                            <label htmlFor="buyIn">Buy-in Amount</label>
                            <input
                                type="number"
                                id="buyIn"
                                name="buyIn"
                                value={formData.buyIn}
                                onChange={handleInputChange}
                                min="100"
                                step="50"
                            />
                        </div>
                    </div>

                    <div className="form-row">
                        <div className="form-group">
                            <label htmlFor="smallBlind">Small Blind</label>
                            <input
                                type="number"
                                id="smallBlind"
                                name="smallBlind"
                                value={formData.smallBlind}
                                onChange={handleInputChange}
                                min="1"
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="bigBlind">Big Blind</label>
                            <input
                                type="number"
                                id="bigBlind"
                                name="bigBlind"
                                value={formData.bigBlind}
                                onChange={handleInputChange}
                                min="2"
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Room Password (Optional)</label>
                        <input
                            type="password"
                            id="password"
                            name="password"
                            value={formData.password}
                            onChange={handleInputChange}
                            placeholder="Leave empty for public room"
                        />
                    </div>

                    <div className="game-summary">
                        <h3>Game Settings Summary</h3>
                        <div className="summary-grid">
                            <div>Max Players: {formData.maxPlayers}</div>
                            <div>Buy-in: ${formData.buyIn}</div>
                            <div>
                                Blinds: ${formData.smallBlind}/${formData.bigBlind}
                            </div>
                            <div>Type: {formData.password ? "Private" : "Public"}</div>
                        </div>
                    </div>

                    {error && <p className="error-message">{error}</p>}

                    <div className="form-actions">
                        <button type="button" onClick={() => navigate("/")} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" disabled={loading} className="btn btn-primary">
                            {loading ? "Creating..." : "Create Room"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default CreateRoomPage
