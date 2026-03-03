package com.kombat.kombatbackend.dto;

public class PlayerEconomyDto {

    private long playerId;
    private long budget;
    private long spawnsLeft;
    private long lastInterest;

    public PlayerEconomyDto() {
    }

    public PlayerEconomyDto(long playerId, long budget, long spawnsLeft, long lastInterest) {
        this.playerId = playerId;
        this.budget = budget;
        this.spawnsLeft = spawnsLeft;
        this.lastInterest = lastInterest;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getBudget() {
        return budget;
    }

    public void setBudget(long budget) {
        this.budget = budget;
    }

    public long getSpawnsLeft() {
        return spawnsLeft;
    }

    public void setSpawnsLeft(long spawnsLeft) {
        this.spawnsLeft = spawnsLeft;
    }

    public long getLastInterest() {
        return lastInterest;
    }

    public void setLastInterest(long lastInterest) {
        this.lastInterest = lastInterest;
    }
}
