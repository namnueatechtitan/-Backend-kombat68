package com.kombat.kombatbackend.dto;

public class MinionSetup {

    private String type;
    private int defenseFactor;
    private String strategy;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getDefenseFactor() { return defenseFactor; }
    public void setDefenseFactor(int defenseFactor) { this.defenseFactor = defenseFactor; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
}