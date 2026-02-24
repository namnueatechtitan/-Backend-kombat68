package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.CharacterType;

public class SelectCharacterRequest {

    private CharacterType character;

    public CharacterType getCharacter() {
        return character;
    }

    public void setCharacter(CharacterType character) {
        this.character = character;
    }
}