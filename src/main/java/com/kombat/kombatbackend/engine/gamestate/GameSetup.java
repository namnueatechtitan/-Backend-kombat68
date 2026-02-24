package com.kombat.kombatbackend.engine.gamestate;

import java.util.ArrayList;
import java.util.List;

public class GameSetup {

    private GameMode mode;
    private GameConfig config;
    private CharacterType selectedCharacter;   // ✅ เพิ่มตรงนี้
    private List<KindInput> minionSelections = new ArrayList<>();

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public GameConfig getConfig() {
        return config;
    }

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    public CharacterType getSelectedCharacter() {   // ✅ getter
        return selectedCharacter;
    }

    public void setSelectedCharacter(CharacterType selectedCharacter) { // ✅ setter
        this.selectedCharacter = selectedCharacter;
    }

    public List<KindInput> getMinionSelections() {
        return minionSelections;
    }

    public void setMinionSelections(List<KindInput> minionSelections) {
        this.minionSelections = minionSelections;
    }
}