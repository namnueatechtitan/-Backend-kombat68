package com.kombat.kombatbackend.dto;

import java.util.List;

public class GameStatusResponse {

    private long currentPlayer;
    private boolean gameOver;
    private String winner;

    // ✅ เปลี่ยนจาก GameState → GameStateDto
    private GameStateDto gameState;

    private List<SpawnableHexDto> spawnableHexes;
    private List<SpawnableHexDto> buyableHexes;
    private List<String> actionLogs;
    private java.util.Map<Long, PlayerEconomyDto> playerEconomy;

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

    public List<SpawnableHexDto> getBuyableHexes() {
        return buyableHexes;
    }

    public List<String> getActionLogs() {
        return actionLogs;
    }

    public java.util.Map<Long, PlayerEconomyDto> getPlayerEconomy() {
        return playerEconomy;
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

    public void setBuyableHexes(List<SpawnableHexDto> buyableHexes) {
        this.buyableHexes = buyableHexes;
    }

    public void setActionLogs(List<String> actionLogs) {
        this.actionLogs = actionLogs;
    }

    public void setPlayerEconomy(java.util.Map<Long, PlayerEconomyDto> playerEconomy) {
        this.playerEconomy = playerEconomy;
    }

    private List<String> availableTypes;
    public List<String> getAvailableTypes() {
        return availableTypes;
    }

    public void setAvailableTypes(List<String> availableTypes) {
        this.availableTypes = availableTypes;
    }

}
