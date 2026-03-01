package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameMode;

public class GameInitRequest {

    private GameConfig config;
    private GameMode mode;
    private PlayerSetupRequest player1;
    private PlayerSetupRequest player2;

    public GameConfig getConfig() { return config; }
    public void setConfig(GameConfig config) { this.config = config; }

    public GameMode getMode() { return mode; }
    public void setMode(GameMode mode) { this.mode = mode; }

    public PlayerSetupRequest getPlayer1() { return player1; }
    public void setPlayer1(PlayerSetupRequest player1) { this.player1 = player1; }

    public PlayerSetupRequest getPlayer2() { return player2; }
    public void setPlayer2(PlayerSetupRequest player2) { this.player2 = player2; }
}