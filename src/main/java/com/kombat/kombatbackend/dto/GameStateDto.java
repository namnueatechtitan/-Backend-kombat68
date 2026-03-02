package com.kombat.kombatbackend.dto;

import java.util.List;

public class GameStateDto {

    private int turnNumber;
    private String phase;
    private List<MinionDto> minions;
    private long budget;
    private long spawnsLeft;

    public GameStateDto(int turnNumber,
                        String phase,
                        List<MinionDto> minions,
                        long budget,
                        long spawnsLeft) {
        this.turnNumber = turnNumber;
        this.phase = phase;
        this.minions = minions;
        this.budget = budget;
        this.spawnsLeft = spawnsLeft;
    }

    public int getTurnNumber() { return turnNumber; }
    public String getPhase() { return phase; }
    public List<MinionDto> getMinions() { return minions; }
    public long getBudget() { return budget; }
    public long getSpawnsLeft() { return spawnsLeft; }
}
