package com.pokergame.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Room {
    private String roomId;
    private String roomName;
    private String hostName;
    private List<String> players;
    private int maxPlayers;
    private int smallBlind;
    private int bigBlind;
    private int buyIn;
    private String password;
    private LocalDateTime createdAt;
    private boolean gameStarted = false;

    public Room(String roomId, String roomName, String hostName, int maxPlayers,
            int smallBlind, int bigBlind, int buyIn, String password) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostName = hostName;
        this.maxPlayers = maxPlayers;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buyIn = buyIn;
        this.password = password;
        this.players = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }

    public void addPlayer(String playerName) {
        if (!players.contains(playerName)) {
            players.add(playerName);
        }
    }

    public void setGameStarted() {
        gameStarted = true;
    }

    public void removePlayer(String playerName) {
        players.remove(playerName);
    }

    public boolean hasPlayer(String playerName) {
        return players.contains(playerName);
    }

    public boolean hasPassword() {
        return password != null && !password.trim().isEmpty();
    }

    public boolean checkPassword(String inputPassword) {
        if (!hasPassword())
            return true;
        return password.equals(inputPassword);
    }
    @Override
    public String toString(){
        return roomName + " (" + roomId + ")" + " by " + hostName + " with " + players.size() + " players";
    }

    // Getters and setters
    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getHostName() {
        return hostName;
    }

    public List<String> getPlayers() {
        return players;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public int getBuyIn() {
        return buyIn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}