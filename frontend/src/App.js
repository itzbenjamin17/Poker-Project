import { Routes, Route } from "react-router-dom"
import HomePage from "./pages/HomePage"
import CreateRoomPage from "./pages/CreateRoomPage"
import JoinRoomPage from "./pages/JoinRoomPage"
import LobbyPage from "./pages/LobbyPage"
import GameRoomPage from "./pages/GameRoomPage"
import "./App.css"

function App() {
    return (
        <div className="App">
            <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/create-room" element={<CreateRoomPage />} />
                <Route path="/join-room" element={<JoinRoomPage />} />
                <Route path="/lobby/:id" element={<LobbyPage />} />
                <Route path="/game/:id" element={<GameRoomPage />} />
            </Routes>
        </div>
    )
}

export default App