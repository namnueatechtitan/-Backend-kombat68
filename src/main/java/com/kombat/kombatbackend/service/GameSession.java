package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.GameSetup;
import com.kombat.kombatbackend.engine.gamestate.GameState;

public class GameSession {

    // 🔵 ก่อนเริ่มเกม
    private GameSetup setup;

    // 🔴 ตอนกำลังเล่น
    private GameState gameState;

    public GameSetup getSetup() {
        return setup;
    }

    public void setSetup(GameSetup setup) {
        this.setup = setup;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void reset() {
        this.setup = null;
        this.gameState = null;
    }
}