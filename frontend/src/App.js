import React, { useState } from 'react';
import axios from 'axios';
import './App.css';

function App() {
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleTestBackend = () => {
    // Reset previous messages
    setMessage('');
    setError('');

    axios.get("http://localhost:8080/api/game/test")
        .then(response => {
          // Set the message from the backend response
          setMessage(response.data);
        })
        .catch(error => {
          // Handle any errors
          console.error("There was an error connecting to the backend!", error);
          setError("Failed to connect to the backend. Is it running?");
        });
  };

  return (
      <div className="App">
        <header className="App-header">
          <h1>Poker Game Frontend</h1>
          <button onClick={handleTestBackend}>
            Test Backend Connection
          </button>
          {message && <p style={{ color: 'lightgreen' }}>{message}</p>}
          {error && <p style={{ color: 'red' }}>{error}</p>}
        </header>
      </div>
  );
}

export default App;