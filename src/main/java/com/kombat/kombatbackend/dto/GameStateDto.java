package com.kombat.kombatbackend.dto;

import java.util.List;

public class GameStateDto {

    private int turnNumber;
    private String phase;
    private List<MinionDto> minions;
    private long budget;

    public GameStateDto(int turnNumber,
                        String phase,
                        List<MinionDto> minions,
                        long budget) {
        this.turnNumber = turnNumber;
        this.phase = phase;
        this.minions = minions;
        this.budget = budget;
    }

    public int getTurnNumber() { return turnNumber; }
    public String getPhase() { return phase; }
    public List<MinionDto> getMinions() { return minions; }
    public long getBudget() { return budget; }
}