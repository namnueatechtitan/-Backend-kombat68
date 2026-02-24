package com.kombat.kombatbackend.dto;

public class ConfigRequest {

    private long maxTurns;
    private long maxSpawns;
    private long maxBudget;

    public long getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(long maxTurns) {
        this.maxTurns = maxTurns;
    }

    public long getMaxSpawns() {
        return maxSpawns;
    }

    public void setMaxSpawns(long maxSpawns) {
        this.maxSpawns = maxSpawns;
    }

    public long getMaxBudget() {
        return maxBudget;
    }

    public void setMaxBudget(long maxBudget) {
        this.maxBudget = maxBudget;
    }
}
