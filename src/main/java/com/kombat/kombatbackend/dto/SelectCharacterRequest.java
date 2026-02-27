package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.CharacterType;

public class SelectCharacterRequest {

    private long playerId;      // 🔥 เพิ่ม
    private CharacterType character;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public CharacterType getCharacter() {
        return character;
    }

    public void setCharacter(CharacterType character) {
        this.character = character;
    }
}