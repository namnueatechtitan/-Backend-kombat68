package com.kombat.kombatbackend.dto;

import java.util.List;

public class GameStatusResponse {

    private long currentPlayer;
    private boolean gameOver;
    private String winner;

    // ✅ เปลี่ยนจาก GameState → GameStateDto
    private GameStateDto gameState;

    private List<SpawnableHexDto> spawnableHexes;

    public GameStatusResponse() {
    }

    public GameStatusResponse(long currentPlayer,
                              boolean gameOver,
                              String winner,
                              GameStateDto gameState) {
        this.currentPlayer = currentPlayer;
        this.gameOver = gameOver;
        this.winner = winner;
        this.gameState = gameState;
    }

    // ==============================
    // GETTERS
    // ==============================

    public long getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinner() {
        return winner;
    }

    public GameStateDto getGameState() {
        return gameState;
    }

    public List<SpawnableHexDto> getSpawnableHexes() {
        return spawnableHexes;
    }

    // ==============================
    // SETTERS
    // ==============================

    public void setCurrentPlayer(long currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public void setGameState(GameStateDto gameState) {
        this.gameState = gameState;
    }

    public void setSpawnableHexes(List<SpawnableHexDto> spawnableHexes) {
        this.spawnableHexes = spawnableHexes;
    }
}