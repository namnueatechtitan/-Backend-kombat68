package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameService {

    private GameConfig config;
    private GameMode mode;
    private CharacterType selectedCharacter;
    private int minionTypeCount;

    // ✅ เพิ่มใหม่
    private final List<MinionType> selectedMinions = new ArrayList<>();

    private GameState gameState;
    private MockGameState mockGameState;

    // =========================
    // CONFIG
    // =========================

    public GameConfig getConfig() {
        if (this.config == null) {
            this.config = GameConfig.sampleDefaults();
        }
        return this.config;
    }

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    // =========================
    // MODE
    // =========================

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public GameMode getMode() {
        return mode;
    }

    // =========================
    // CHARACTER
    // =========================

    public void setCharacter(CharacterType character) {
        this.selectedCharacter = character;
    }

    public CharacterType getSelectedCharacter() {
        return selectedCharacter;
    }

    // =========================
    // MINION TYPE COUNT
    // =========================

    public void setMinionTypeCount(int count) {
        this.minionTypeCount = count;
    }

    public int getMinionTypeCount() {
        return minionTypeCount;
    }

    // =========================
    // MINION SELECTION (เพิ่มใหม่)
    // =========================

    public void addMinion(MinionType type) {

        if (selectedMinions.size() >= minionTypeCount) {
            throw new IllegalStateException("Minion limit reached");
        }

        if (selectedMinions.contains(type)) {
            throw new IllegalStateException("Minion type already selected");
        }

        selectedMinions.add(type);
    }

    public List<MinionType> getSelectedMinions() {
        return selectedMinions;
    }

    // =========================
    // START GAME
    // =========================

    public void startGame() {

        if (mode == null) {
            throw new IllegalStateException("Mode not selected");
        }

        if (config == null) {
            throw new IllegalStateException("Config not selected");
        }

        if (selectedCharacter == null) {
            throw new IllegalStateException("Character not selected");
        }

        SetupResult result;

        switch (mode) {

            case AUTO:
                result = AutoModeSetup.createGame(config, null);
                break;

            case DUEL:
                result = createDuelState();
                break;

            case SOLITAIRE:
                result = createSolitaireState();
                break;

            default:
                throw new IllegalStateException("Unsupported mode");
        }

        this.gameState = result.getGameState();
        this.mockGameState = result.getMock();
    }

    private SetupResult createDuelState() {
        return AutoModeSetup.createGame(config, null);
    }

    private SetupResult createSolitaireState() {
        return AutoModeSetup.createGame(config, null);
    }

    public GameState getGameState() {
        return gameState;
    }

    public MockGameState getMockGameState() {
        return mockGameState;
    }
}