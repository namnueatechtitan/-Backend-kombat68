package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.GameState;

public class GameStatusResponse {

    private long currentPlayer;
    private boolean gameOver;
    private String winner;
    private GameState gameState;

    public GameStatusResponse() {}

    public GameStatusResponse(long currentPlayer,
                              boolean gameOver,
                              String winner,
                              GameState gameState) {
        this.currentPlayer = currentPlayer;
        this.gameOver = gameOver;
        this.winner = winner;
        this.gameState = gameState;
    }

    public long getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinner() {
        return winner;
    }

    public GameState getGameState() {
        return gameState;
    }
}