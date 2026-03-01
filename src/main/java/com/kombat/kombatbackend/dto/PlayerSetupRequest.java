package com.kombat.kombatbackend.dto;

import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import java.util.List;

public class PlayerSetupRequest {

    private CharacterType character;
    private List<MinionSetup> minions;

    public CharacterType getCharacter() { return character; }
    public void setCharacter(CharacterType character) { this.character = character; }

    public List<MinionSetup> getMinions() { return minions; }
    public void setMinions(List<MinionSetup> minions) { this.minions = minions; }
}