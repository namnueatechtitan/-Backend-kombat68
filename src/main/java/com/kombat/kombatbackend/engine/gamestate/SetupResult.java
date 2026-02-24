package com.kombat.kombatbackend.engine.gamestate;

public final class SetupResult {

    private final GameState gameState;
    private final MockGameState mock;

    public SetupResult(GameState gameState, MockGameState mock) {
        this.gameState = gameState;
        this.mock = mock;
    }

    public GameState getGameState() {
        return gameState;
    }

    public MockGameState getMock() {
        return mock;
    }
}