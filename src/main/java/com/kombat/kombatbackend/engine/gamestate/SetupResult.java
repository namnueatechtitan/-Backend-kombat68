package com.kombat.kombatbackend.engine.gamestate;

public final class SetupResult {

    public final GameState gameState;
    public final MockGameState mock;

    public SetupResult(GameState gameState, MockGameState mock) {
        this.gameState = gameState;
        this.mock = mock;
    }
}