package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.GameEngine;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;

final class GameTurnService {

    boolean spawn(GameEngine engine, String type, int row, int col) {
        return engine.spawn(type, row, col);
    }

    boolean buyHex(GameEngine engine, int row, int col) {
        return engine.buyHex(row, col);
    }

    void executeTurn(GameEngine engine) {
        engine.executeTurn();
    }

    boolean markFinishedIfGameOver(GameEngine engine, GameState gameState) {
        if (engine != null && engine.isGameOver()) {
            if (gameState != null) {
                gameState.setPhase(TurnPhase.END);
            }
            return true;
        }
        return false;
    }
}

