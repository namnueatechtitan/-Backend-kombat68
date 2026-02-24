package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.MinionType;

public class MinionTypeRequest {

    private MinionType type;

    public MinionType getType() {
        return type;
    }

    public void setType(MinionType type) {
        this.type = type;
    }
}