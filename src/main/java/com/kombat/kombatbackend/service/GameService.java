package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.parser.Parser;
import com.kombat.kombatbackend.engine.strategy.Strategy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {

    public static final long P1 = 1L;
    public static final long P2 = 2L;

    private GameConfig config;
    private GameMode mode;

    // 🔥 แยก character ต่อ player
    private final Map<Long, CharacterType> selectedCharacters = new HashMap<>();

    // 🔥 แยก minions ต่อ player
    private final Map<Long, List<MinionKindDef>> selectedMinionsByPlayer = new HashMap<>();

    private GameState gameState;
    private MockGameState mockGameState;

    // 🔥 current turn player
    private long currentPlayer = P1;

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
    // CHARACTER (per player)
    // =====================================================

    public void setCharacter(long playerId, CharacterType character) {
        selectedCharacters.put(playerId, character);
    }

    public CharacterType getCharacter(long playerId) {
        return selectedCharacters.get(playerId);
    }

    // =====================================================
    // RESET MINIONS (per player)
    // =====================================================

    public void resetMinions(long playerId) {
        selectedMinionsByPlayer.put(playerId, new ArrayList<>());
    }

    // =====================================================
    // ADD MINION (per player)
    // =====================================================

    public void addMinion(long playerId,
                          String typeText,
                          int defenseFactor,
                          String strategyCode) {

        if (strategyCode == null || strategyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy must not be empty");
        }

        if (config == null) {
            throw new IllegalStateException("Config must be set before adding minions");
        }

        MinionType type = MinionType.fromUserText(typeText);

        List<MinionKindDef> list =
                selectedMinionsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>());

        if (list.stream().anyMatch(m -> m.getType() == type)) {
            throw new IllegalStateException("Minion type already selected for player " + playerId);
        }

        // dummy state สำหรับ parse strategy
        Board board = new Board();
        List<Minion> minions = new ArrayList<>();
        BudgetManager budgetManager = new BudgetManager(config.initBudget());

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

        list.add(def);
    }

    public List<MinionKindDef> getSelectedMinions(long playerId) {
        return selectedMinionsByPlayer.getOrDefault(playerId, List.of());
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

        if (!selectedCharacters.containsKey(P1) ||
                !selectedCharacters.containsKey(P2)) {
            throw new IllegalStateException("Both players must select characters");
        }

        if (getSelectedMinions(P1).isEmpty() ||
                getSelectedMinions(P2).isEmpty()) {
            throw new IllegalStateException("Both players must configure minions");
        }

        Board board = new Board();
        BudgetManager budget = new BudgetManager(config.initBudget());
        List<Minion> minions = new ArrayList<>();

        GameState gs = new GameState(
                board,
                minions,
                budget,
                TurnPhase.PLAY,
                config
        );

        MockGameState mg = new MockGameState(gs);

        // 🔥 register kinds for P1
        for (MinionKindDef def : getSelectedMinions(P1)) {
            gs.registerKind(P1, def);
        }

        // 🔥 register kinds for P2
        for (MinionKindDef def : getSelectedMinions(P2)) {
            gs.registerKind(P2, def);
        }

        gs.lockSetup();

        this.gameState = gs;
        this.mockGameState = mg;
        this.currentPlayer = P1;
    }

    // =====================================================
    // TURN CONTROL
    // =====================================================

    public long getCurrentPlayer() {
        return currentPlayer;
    }

    public void endTurn() {
        currentPlayer = (currentPlayer == P1) ? P2 : P1;
        gameState.advanceTurn();
    }

    // =====================================================
    // GETTERS
    // =====================================================

    public GameState getGameState() {
        return gameState;
    }

    public MockGameState getMockGameState() {
        return mockGameState;
    }
}