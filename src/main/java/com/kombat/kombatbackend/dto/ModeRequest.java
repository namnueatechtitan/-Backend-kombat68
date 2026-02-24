package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.GameMode;

public class ModeRequest {

    private GameMode mode;

    public ModeRequest() {}

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }
}