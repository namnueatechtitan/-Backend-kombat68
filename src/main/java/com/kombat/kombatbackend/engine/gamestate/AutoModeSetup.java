package com.kombat.kombatbackend.engine.gamestate;

import com.kombat.kombatbackend.engine.parser.Parser;
import com.kombat.kombatbackend.engine.parser.SyntaxException;
import com.kombat.kombatbackend.engine.strategy.Strategy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Setup helper:
 * - humans choose (type, kindName, defenseFactor, strategyCode) for each kind
 * - enforce: choose at least 3 kinds (3..5), no duplicate types
 * - lock setup, return GameState + MockGameState
 */
public final class AutoModeSetup {

    private AutoModeSetup() {
    }

    public static SetupResult createGame(long initialBudget, List<KindInput> kindInputs) throws SyntaxException {
        GameConfig cfg = new GameConfig(
                0, 0, initialBudget, 0, 0, initialBudget, 0, 0, 0
        );
        return createGame(cfg, kindInputs);
    }

    public static SetupResult createGame(GameConfig cfg, List<KindInput> kindInputs) throws SyntaxException {
        if (cfg == null) cfg = GameConfig.sampleDefaults();
        if (kindInputs == null || kindInputs.isEmpty()) {
            throw new IllegalArgumentException("kindInputs must not be empty");
        }

        // validate kinds: 3..5 distinct types
        EnumSet<MinionType> chosen = EnumSet.noneOf(MinionType.class);
        for (KindInput in : kindInputs) {
            Objects.requireNonNull(in, "kind input must not be null");
            if (!chosen.add(in.type)) {
                throw new IllegalArgumentException("Duplicate minion type in setup: " + in.type);
            }
        }
        if (chosen.size() < 3) {
            throw new IllegalArgumentException("Must choose at least 3 minion types (kinds).");
        }
        if (chosen.size() > 5) {
            throw new IllegalArgumentException("Too many minion types (max 5).");
        }

        Board board = new Board();
        BudgetManager budget = new BudgetManager(cfg.initBudget());
        List<Minion> minions = new ArrayList<Minion>();

        GameState gs = new GameState(board, minions, budget, TurnPhase.PLAY, cfg);
        MockGameState mg = new MockGameState(gs);

        // parse strategy + register kind definitions
        for (KindInput in : kindInputs) {
            Strategy st = new Parser(in.strategyCode, mg).parseStrategy();
            gs.registerKind(new MinionKindDef(in.type, in.kindName, in.defenseFactor, st));
        }

        gs.lockSetup();
        return new SetupResult(gs, mg);
    }

    /**
     * Duel setup (2 players):
     * - both players must select the same set of 3..5 types
     * - per spec: both players must agree on (kindName, defenseFactor, strategyCode) per type
     */
    public static SetupResult createDuelGame(GameConfig cfg,
                                             List<KindInput> p1Kinds,
                                             List<KindInput> p2Kinds) throws SyntaxException {
        if (cfg == null) cfg = GameConfig.sampleDefaults();
        if (p1Kinds == null || p2Kinds == null || p1Kinds.isEmpty() || p2Kinds.isEmpty()) {
            throw new IllegalArgumentException("Both p1Kinds and p2Kinds must not be empty");
        }

        EnumSet<MinionType> p1 = EnumSet.noneOf(MinionType.class);
        for (KindInput in : p1Kinds) {
            Objects.requireNonNull(in, "kind input must not be null");
            if (!p1.add(in.type)) throw new IllegalArgumentException("Duplicate minion type in P1 setup: " + in.type);
        }

        EnumSet<MinionType> p2 = EnumSet.noneOf(MinionType.class);
        for (KindInput in : p2Kinds) {
            Objects.requireNonNull(in, "kind input must not be null");
            if (!p2.add(in.type)) throw new IllegalArgumentException("Duplicate minion type in P2 setup: " + in.type);
        }

        if (!p1.equals(p2)) {
            throw new IllegalArgumentException("Both players must choose the same set of types. P1=" + p1 + " P2=" + p2);
        }
        if (p1.size() < 3) throw new IllegalArgumentException("Must choose at least 3 minion types (kinds).");
        if (p1.size() > 5) throw new IllegalArgumentException("Too many minion types (max 5).");

        // ตามสเปค: ต้องเหมือนกันเฉพาะ defenseFactor/strategyCode ต่อ type
        for (MinionType t : p1) {
            KindInput a = findKind(p1Kinds, t);
            KindInput b = findKind(p2Kinds, t);
            if (a == null || b == null) throw new IllegalStateException("Missing KindInput for type: " + t);

            if (a.defenseFactor != b.defenseFactor ||
                    !normCode(a.strategyCode).equals(normCode(b.strategyCode))) {
                throw new IllegalArgumentException(
                        "Kind definition mismatch for " + t
                                + " (defenseFactor/strategyCode must be identical for both players)."
                );
            }
        }

        Board board = new Board();
        BudgetManager budget = new BudgetManager(cfg.initBudget()); // pid=0 (phase-2 compat)
        budget.initPlayer(1L, cfg.initBudget());
        budget.initPlayer(2L, cfg.initBudget());

        List<Minion> minions = new ArrayList<>();
        GameState gs = new GameState(board, minions, budget, TurnPhase.PLAY, cfg);
        MockGameState mg = new MockGameState(gs);

        // ป้องกันชื่อซ้ำในฝั่งเดียวกัน (เพราะ spawn รับ TYPE_OR_KIND)
        validateUniqueKindNames("P1", p1Kinds);
        validateUniqueKindNames("P2", p2Kinds);

        // register แยก per player (kindName ต่างกันได้ แต่ใช้ strategy เดียวกัน)
        for (MinionType t : p1) {
            KindInput a = findKind(p1Kinds, t);
            KindInput b = findKind(p2Kinds, t);

            Strategy st = new Parser(a.strategyCode, mg).parseStrategy();
            gs.registerKind(1L, new MinionKindDef(a.type, a.kindName, a.defenseFactor, st));
            gs.registerKind(2L, new MinionKindDef(b.type, b.kindName, b.defenseFactor, st));
        }

        gs.lockSetup();
        return new SetupResult(gs, mg);
    }

    private static KindInput findKind(List<KindInput> kinds, MinionType t) {
        for (KindInput in : kinds) {
            if (in != null && in.type == t) return in;
        }
        return null;
    }

    private static String normCode(String s) {
        return (s == null) ? "" : s.trim().replace("\r\n", "\n");
    }

    private static void validateUniqueKindNames(String who, List<KindInput> kinds) {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (KindInput k : kinds) {
            String key = (k.kindName == null ? "" : k.kindName.trim()).toLowerCase();
            if (key.isEmpty()) continue;
            if (!seen.add(key)) {
                throw new IllegalArgumentException(who + " has duplicate kindName: \"" + k.kindName + "\"");
            }
        }
    }
}