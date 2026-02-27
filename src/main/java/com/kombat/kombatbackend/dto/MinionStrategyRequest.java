package com.kombat.kombatbackend.dto;

public class MinionStrategyRequest {

    private long playerId;   // 🔥 เพิ่ม

    private String type;
    private String strategy;
    private int defenseFactor;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public String getType() {
        return type;
    }

    public String getStrategy() {
        return strategy;
    }

    public int getDefenseFactor() {
        return defenseFactor;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setDefenseFactor(int defenseFactor) {
        this.defenseFactor = defenseFactor;
    }
}