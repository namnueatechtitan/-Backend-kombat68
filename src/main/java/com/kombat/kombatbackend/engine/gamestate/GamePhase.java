package com.kombat.kombatbackend.engine.gamestate;

public enum GamePhase {
    NOT_CONFIGURED,
    CONFIGURED,
    MODE_SET,
    SETUP_IN_PROGRESS,
    READY_TO_START,
    PLAYING,
    FINISHED
}