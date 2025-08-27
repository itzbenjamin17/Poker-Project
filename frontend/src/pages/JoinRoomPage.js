"use client"

import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"

function JoinRoomPage() {
    const navigate = useNavigate()
    const [formData, setFormData] = useState({
        roomName: "",
        playerName: "",
        password: ""
    })
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")

    const handleInputChange = (e) => {
        const { name, value } = e.target
        setFormData(prev => ({
            ...prev,
            [name]: value
        }))
    }

    const handleSubmit = async (e) => {
        e.preventDefault()
        setLoading(true)
        setError("")

        try {
            const response = await fetch(`http://localhost:8080/api/game/room/join-by-name`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    roomName: formData.roomName,
                    playerName: formData.playerName,
                    password: formData.password
                })
            })

            if (!response.ok) {
                const errorData = await response.text()
                throw new Error(errorData || 'Failed to join room')
            }

            const result = await response.json()
            const roomId = result.roomId

            // Navigate to lobby
            navigate(`/lobby/${roomId}`, {
                state: {
                    isHost: false,
                    playerName: formData.playerName
                }
            })
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="join-room-page">
            <div className="page-header">
                <Link to="/" className="back-button">
                    ← Back to Home
                </Link>
                <h1>Join Game Room</h1>
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

                    <div className="form-group">
                        <label htmlFor="password">Room Password (if required)</label>
                        <input
                            type="password"
                            id="password"
                            name="password"
                            value={formData.password}
                            onChange={handleInputChange}
                            placeholder="Enter password if room is private"
                        />
                    </div>

                    {error && <p className="error-message">{error}</p>}

                    <div className="form-actions">
                        <button type="button" onClick={() => navigate("/")} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" disabled={loading} className="btn btn-primary">
                            {loading ? "Joining..." : "Join Room"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default JoinRoomPage
