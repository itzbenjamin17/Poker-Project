"use client"

import { useState, useEffect } from "react"
import { Link, useLocation } from "react-router-dom"
import axios from "axios"

function HomePage() {
    const [message, setMessage] = useState("")
    const [error, setError] = useState("")
    const location = useLocation()

    // Check for messages from navigation state (like when redirected from closed room)
    useEffect(() => {
        if (location.state?.message) {
            setMessage(location.state.message)
            // Clear the message after a few seconds
            setTimeout(() => setMessage(""), 5000)
        }
    }, [location.state])

    const handleTestBackend = () => {
        setMessage("")
        setError("")

        axios
            .get("http://localhost:8080/api/game/test")
            .then((response) => {
                setMessage(response.data)
            })
            .catch((error) => {
                console.error("There was an error connecting to the backend!", error)
                setError("Failed to connect to the backend. Is it running?")
            })
    }

    return (
        <div className="home-page">
            <header className="hero-section">
                <div className="hero-content">
                    <div className="card-symbols">
                        <span className="spade">♠</span>
                        <span className="heart">♥</span>
                        <span className="diamond">♦</span>
                        <span className="club">♣</span>
                    </div>
                    <h1 className="game-title">Poker Pro</h1>
                    <p className="game-subtitle">Experience the thrill of Texas Hold'em with friends</p>

                     <div className="action-buttons">
                        <Link to="/create-room" className="btn btn-primary">
                            Create Game Room
                        </Link>
                        <Link to="/join-room" className="btn btn-secondary">
                            Join Room
                        </Link>
                    </div>

                    <div className="backend-test">
                        <button onClick={handleTestBackend} className="btn btn-outline">
                            Test Backend Connection
                        </button>
                        {message && <p className="success-message">{message}</p>}
                        {error && <p className="error-message">{error}</p>}
                    </div>
                </div>
            </header>

            <section className="features-section">
                <div className="features-grid">
                    <div className="feature-card">
                        <div className="feature-icon">🎯</div>
                        <h3>Easy Setup</h3>
                        <p>Create a room and invite friends with a simple room code</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">🃏</div>
                        <h3>Real-time Play</h3>
                        <p>Experience smooth, real-time poker gameplay</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">💰</div>
                        <h3>Customizable</h3>
                        <p>Set your own blinds, buy-ins, and game rules</p>
                    </div>
                </div>
            </section>
        </div>
    )
}

export default HomePage
