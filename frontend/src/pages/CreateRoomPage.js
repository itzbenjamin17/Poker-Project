"use client"

import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"

function CreateRoomPage() {
    const navigate = useNavigate()
    const [formData, setFormData] = useState({
        roomName: "",
        maxPlayers: 6,
        smallBlind: 10,
        bigBlind: 20,
        buyIn: 1000,
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
            // For demo purposes, generate a random room ID
            const roomId = Math.random().toString(36).substring(2, 8).toUpperCase()

            // In a real app, you would make this API call:
            // const response = await axios.post('http://localhost:8080/api/game/create', formData);
            // const roomId = response.data.roomId;

            navigate(`/room/${roomId}`)
        } catch (error) {
            console.error("Error creating room:", error)
            setError("Failed to create room. Please try again.")
        } finally {
            setLoading(false)
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
