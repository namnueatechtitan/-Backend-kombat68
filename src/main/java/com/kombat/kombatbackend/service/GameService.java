package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.parser.Parser;
import com.kombat.kombatbackend.engine.strategy.Strategy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameService {

    private GameConfig config;
    private GameMode mode;
    private CharacterType selectedCharacter;

    private final List<MinionKindDef> selectedMinions = new ArrayList<>();

    private GameState gameState;
    private MockGameState mockGameState;

    // =====================================================
    // CONFIG
    // =====================================================

    public GameConfig getConfig() {
        if (this.config == null) {
            this.config = GameConfig.sampleDefaults();
        }
        return this.config;
    }

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    // =====================================================
    // MODE
    // =====================================================

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public GameMode getMode() {
        return mode;
    }

    // =====================================================
    // CHARACTER
    // =====================================================

    public void setCharacter(CharacterType character) {
        this.selectedCharacter = character;
    }

    public CharacterType getSelectedCharacter() {
        return selectedCharacter;
    }

    // =====================================================
    // RESET MINIONS
    // =====================================================

    public void resetMinions() {
        selectedMinions.clear();
    }

    // =====================================================
    // ADD MINION (called from /setup/full)
    // =====================================================

    public void addMinion(String typeText, int defenseFactor, String strategyCode) {

        if (strategyCode == null || strategyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy must not be empty");
        }

        if (config == null) {
            throw new IllegalStateException("Config must be set before adding minions");
        }

        MinionType type = MinionType.fromUserText(typeText);

        if (selectedMinions.stream().anyMatch(m -> m.getType() == type)) {
            throw new IllegalStateException("Minion type already selected");
        }

        // 🔥 dummy state สำหรับ parse strategy
        Board board = new Board();
        List<Minion> minions = new ArrayList<>();

        BudgetManager budgetManager =
                new BudgetManager(config.initBudget());

        GameState dummyState = new GameState(
                board,
                minions,
                budgetManager,
                TurnPhase.SETUP,
                config
        );

        MockGameState mock = new MockGameState(dummyState);

        Parser parser = new Parser(strategyCode, mock);
        Strategy strategy = parser.parseStrategy();

        MinionKindDef def =
                new MinionKindDef(
                        type,
                        defenseFactor,
                        strategyCode,
                        strategy
                );

        selectedMinions.add(def);
    }

    public List<MinionKindDef> getSelectedMinions() {
        return selectedMinions;
    }

    // =====================================================
    // START GAME
    // =====================================================

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

        if (selectedMinions.isEmpty()) {
            throw new IllegalStateException("No minions configured");
        }

        Board board = new Board();
        BudgetManager budget =
                new BudgetManager(config.initBudget());

        List<Minion> minions = new ArrayList<>();

        GameState gs = new GameState(
                board,
                minions,
                budget,
                TurnPhase.PLAY,
                config
        );

        MockGameState mg = new MockGameState(gs);

        // register kind definitions
        for (MinionKindDef def : selectedMinions) {
            gs.registerKind(def);
        }

        gs.lockSetup();

        this.gameState = gs;
        this.mockGameState = mg;
    }

    public GameState getGameState() {
        return gameState;
    }

    public MockGameState getMockGameState() {
        return mockGameState;
    }
}