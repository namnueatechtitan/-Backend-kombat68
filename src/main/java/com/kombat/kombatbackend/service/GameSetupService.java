package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.engine.gamestate.Board;
import com.kombat.kombatbackend.engine.gamestate.BudgetManager;
import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameEngine;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import com.kombat.kombatbackend.engine.gamestate.GamePhase;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.engine.gamestate.Minion;
import com.kombat.kombatbackend.engine.gamestate.MinionKindDef;
import com.kombat.kombatbackend.engine.gamestate.MinionType;
import com.kombat.kombatbackend.engine.gamestate.MockGameState;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;
import com.kombat.kombatbackend.engine.parser.Parser;
import com.kombat.kombatbackend.engine.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GameSetupService {

    static final class GameStartBundle {
        private final GameState gameState;
        private final MockGameState mockGameState;
        private final GameEngine engine;

        GameStartBundle(GameState gameState, MockGameState mockGameState, GameEngine engine) {
            this.gameState = gameState;
            this.mockGameState = mockGameState;
            this.engine = engine;
        }

        GameState gameState() {
            return gameState;
        }

        MockGameState mockGameState() {
            return mockGameState;
        }

        GameEngine engine() {
            return engine;
        }
    }

    void ensureEditable(GamePhase phase, String reason) {
        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException(reason);
        }
    }

    MinionKindDef buildMinionKindDef(
            GameConfig config,
            String typeText,
            String kindName,
            int defenseFactor,
            String strategyCode
    ) {
        if (strategyCode == null || strategyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy must not be empty");
        }

        if (config == null) {
            throw new IllegalStateException("Config must be set first");
        }

        MinionType type = MinionType.fromUserText(typeText);

        Board board = new Board();
        List<Minion> minions = new ArrayList<>();
        BudgetManager budgetManager = new BudgetManager(config.initBudget());
        GameState dummyState = new GameState(board, minions, budgetManager, TurnPhase.PLAYER_ACTION, config);
        MockGameState mock = new MockGameState(dummyState);

        Parser parser = new Parser(strategyCode, mock);
        Strategy strategy = parser.parseStrategy();

        return new MinionKindDef(type, kindName, defenseFactor, strategyCode, strategy);
    }

    GameStartBundle createGameStartBundle(
            GameConfig config,
            Map<Long, List<MinionKindDef>> selectedMinionsByPlayer,
            long p1,
            long p2
    ) {
        if (config == null) {
            throw new IllegalStateException("Config not set");
        }

        Board board = new Board();
        BudgetManager budget = new BudgetManager(config.initBudget());
        budget.initPlayer(p1, config.initBudget());
        budget.initPlayer(p2, config.initBudget());
        List<Minion> minions = new ArrayList<>();

        GameState gs = new GameState(board, minions, budget, TurnPhase.FREE_SPAWN, config);
        MockGameState mg = new MockGameState(gs);

        for (MinionKindDef def : selectedMinionsByPlayer.getOrDefault(p1, List.of())) {
            gs.registerKind(p1, def);
        }
        for (MinionKindDef def : selectedMinionsByPlayer.getOrDefault(p2, List.of())) {
            gs.registerKind(p2, def);
        }
        gs.lockSetup();

        return new GameStartBundle(gs, mg, new GameEngine(config, gs, mg));
    }

    GameMode resolveMode(GameMode requestedMode) {
        return requestedMode == null ? GameMode.DUEL : requestedMode;
    }
}

