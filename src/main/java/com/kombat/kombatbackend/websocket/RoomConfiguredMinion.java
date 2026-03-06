package com.kombat.kombatbackend.websocket;

public class RoomConfiguredMinion {
    private String type;
    private String name;
    private int defenseFactor;
    private String strategy;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDefenseFactor() {
        return defenseFactor;
    }

    public void setDefenseFactor(int defenseFactor) {
        this.defenseFactor = defenseFactor;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
