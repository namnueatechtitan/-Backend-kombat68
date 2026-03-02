package com.kombat.kombatbackend.engine.gamestate;

import com.kombat.kombatbackend.engine.strategy.Strategy;
import java.util.*;

public class GameState {

    private final Board board;
    private final List<Minion> minions;
    private final BudgetManager budgetManager;
    private final GameConfig config;

    private int turnNumber;
    private TurnPhase phase;

    // setup-time configuration (immutable after lock)
    // - phase-2 compatibility: ownerId=0 means single-player kinds
    // - duel mode: ownerId=1/2 (or any positive id)
    private final Map<Long, EnumMap<MinionType, MinionKindDef>> kindsByOwner = new HashMap<>();
    private boolean setupLocked = false;

    // =========================
    // Constructors
    // =========================

    public GameState(
            Board board,
            List<Minion> initialMinions,
            BudgetManager budgetManager,
            TurnPhase initialPhase
    ) {
        this(board, initialMinions, budgetManager, initialPhase, GameConfig.sampleDefaults());
    }

    public GameState(
            Board board,
            List<Minion> initialMinions,
            BudgetManager budgetManager,
            TurnPhase initialPhase,
            GameConfig config
    ) {
        this.board = Objects.requireNonNull(board, "Board cannot be null");
        this.budgetManager = Objects.requireNonNull(budgetManager, "BudgetManager cannot be null");
        this.phase = Objects.requireNonNull(initialPhase, "TurnPhase cannot be null");
        this.config = Objects.requireNonNull(config, "GameConfig cannot be null");

        if (initialMinions == null) {
            throw new NullPointerException("Minion list cannot be null");
        }

        this.minions = initialMinions;
        this.turnNumber = 0;

        // default (phase-2) owner
        kindsByOwner.put(0L, new EnumMap<>(MinionType.class));

        checkRep();
    }

    // =========================
    // Getters
    // =========================

    public Board getBoard() {
        return board;
    }

    public List<Minion> getMinions() {
        return Collections.unmodifiableList(minions);
    }

    public BudgetManager getBudgetManager() {
        return budgetManager;
    }

    public GameConfig getConfig() {
        return config;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public TurnPhase getPhase() {
        return phase;
    }

    // =========================
    // Setup-time API
    // =========================

    public void registerKind(MinionKindDef def) {
        registerKind(0L, def);
    }

    public void registerKind(long ownerId, MinionKindDef def) {
        Objects.requireNonNull(def, "def must not be null");

        if (setupLocked) {
            throw new IllegalStateException("Setup is locked; cannot register new kinds");
        }

        EnumMap<MinionType, MinionKindDef> kinds =
                kindsByOwner.computeIfAbsent(ownerId,
                        k -> new EnumMap<>(MinionType.class));

        MinionType key = def.getType();

        if (kinds.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate minion type: " + key);
        }

        kinds.put(key, def);
    }

    public Map<MinionType, MinionKindDef> getKinds() {
        return getKinds(0L);
    }

    public Map<MinionType, MinionKindDef> getKinds(long ownerId) {
        EnumMap<MinionType, MinionKindDef> kinds = kindsByOwner.get(ownerId);
        if (kinds == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(kinds);
    }

    public MinionKindDef getKind(MinionType type) {
        return getKind(0L, type);
    }

    public MinionKindDef getKind(long ownerId, MinionType type) {
        EnumMap<MinionType, MinionKindDef> kinds = kindsByOwner.get(ownerId);
        return (kinds == null) ? null : kinds.get(type);
    }

    /**
     * Convenience lookup:
     * - if name matches a registered kind display name -> return it
     * - else if name looks like a type (Fighter/Assassin/...) -> return that type's kind
     */
    public MinionKindDef getKind(String name) {
        return getKind(0L, name);
    }

    public MinionKindDef getKind(long ownerId, String name) {
        if (name == null) return null;

        String q = name.trim();
        if (q.isEmpty()) return null;

        EnumMap<MinionType, MinionKindDef> kinds = kindsByOwner.get(ownerId);
        if (kinds == null) return null;

        for (MinionKindDef def : kinds.values()) {
            if (def != null
                    && def.getKindName() != null
                    && def.getKindName().equalsIgnoreCase(q)) {
                return def;
            }
        }

        try {
            return kinds.get(MinionType.fromUserText(q));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void lockSetup() {
        this.setupLocked = true;
    }

    public boolean isSetupLocked() {
        return setupLocked;
    }

    // =========================
    // Spawn API
    // =========================

    public Minion spawnMinion(String kindOrType, int initialHp, int x, int y) {
        return spawnMinion(0L, kindOrType, initialHp, x, y);
    }

    public Minion spawnMinion(long ownerId, String kindOrType, int initialHp, int x, int y) {
        MinionKindDef def = getKind(ownerId, kindOrType);

        if (def == null) {
            throw new IllegalArgumentException("Unknown minion kind/type: " + kindOrType);
        }

        return spawnMinion(ownerId, def.getType(), initialHp, x, y);
    }

    public Minion spawnMinion(MinionType type, int x, int y) {
        return spawnMinion(0L, type, x, y);
    }

    public Minion spawnMinion(long ownerId, MinionType type, int x, int y) {
        long hp = config.initHp();

        if (hp > Integer.MAX_VALUE) {
            throw new IllegalStateException("init_hp too large for int: " + hp);
        }

        return spawnMinion(ownerId, type, (int) hp, x, y);
    }

    public Minion spawnMinion(MinionType type, int initialHp, int x, int y) {
        return spawnMinion(0L, type, initialHp, x, y);
    }

    public Minion spawnMinion(
            long ownerId,
            MinionType type,
            int initialHp,
            int x,
            int y
    ) {
        MinionKindDef def = getKind(ownerId, type);

        if (def == null) {
            throw new IllegalArgumentException(
                    "Unknown minion type for ownerId=" + ownerId + ": " + type);
        }

        if (!board.isInsideBoard(x, y)) {
            throw new IllegalArgumentException(
                    "Spawn position outside board: x=" + x + ",y=" + y);
        }

        Hex h = board.getHex(x, y);

        if (h.isOccupied()) {
            throw new IllegalStateException(
                    "Spawn position already occupied: x=" + x + ",y=" + y);
        }

        Minion m = new Minion(
                initialHp,
                def.getDefenseFactor(),
                h,
                def.getStrategy(),
                ownerId
        );

        m.setType(type);
        m.setKindName(def.getKindName());

        h.placeMinion(m);
        minions.add(m);

        checkRep();
        return m;
    }

    public Map<Minion, Strategy> buildStrategyBindings() {
        return buildStrategyBindings(0L);
    }

    public Map<Minion, Strategy> buildStrategyBindings(long ownerId) {
        IdentityHashMap<Minion, Strategy> map = new IdentityHashMap<>();

        for (Minion m : minions) {
            if (m == null) continue;
            if (m.getOwnerId() != ownerId) continue;

            Object ref = m.getStrategyRef();
            if (ref instanceof Strategy) {
                map.put(m, (Strategy) ref);
            }
        }

        return map;
    }

    // =========================
    // Removal API
    // =========================

    public boolean removeMinion(Minion m) {
        if (m == null) return false;

        Hex pos = m.getPosition();

        if (pos != null) {
            if (pos.getOccupant() == m) {
                pos.removeMinion();
            } else if (board.isInsideBoard(pos.getX(), pos.getY())) {
                Hex h = board.getHex(pos.getX(), pos.getY());
                if (h.getOccupant() == m) {
                    h.removeMinion();
                }
            }
        }

        boolean removed = minions.remove(m);
        checkRep();
        return removed;
    }

    // =========================
    // State Control
    // =========================

    public void advanceTurn() {
        if (turnNumber < 0) {
            throw new IllegalStateException("Turn number must be non-negative");
        }

        turnNumber++;
        checkRep();
    }

    public void setPhase(TurnPhase phase) {
        this.phase = Objects.requireNonNull(phase, "TurnPhase cannot be null");
        checkRep();
    }

    // =========================
    // Representation Invariant
    // =========================

    private void checkRep() {

        if (turnNumber < 0) {
            throw new IllegalStateException("Turn number must be non-negative");
        }

        if (phase == null) {
            throw new IllegalStateException("Phase must not be null");
        }

        for (Minion m : minions) {
            if (m == null) {
                throw new IllegalStateException("Minion list must not contain null");
            }

            Hex pos = m.getPosition();
            if (pos == null) {
                throw new IllegalStateException("Minion position must not be null");
            }

            if (!board.isInsideBoard(pos.getX(), pos.getY())) {
                throw new IllegalStateException("Minion is outside the board");
            }

            Hex boardHex = board.getHex(pos.getX(), pos.getY());
            if (boardHex.getOccupant() != m) {
                throw new IllegalStateException("Board and minion position mismatch");
            }
        }

        for (Map.Entry<Long, EnumMap<MinionType, MinionKindDef>> ownerEntry
                : kindsByOwner.entrySet()) {

            if (ownerEntry.getKey() == null || ownerEntry.getValue() == null) {
                throw new IllegalStateException(
                        "Kinds must not contain null ownerId/maps");
            }

            EnumMap<MinionType, MinionKindDef> kinds = ownerEntry.getValue();

            for (Map.Entry<MinionType, MinionKindDef> e : kinds.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    throw new IllegalStateException(
                            "Kinds must not contain null keys/values");
                }

                if (e.getKey() != e.getValue().getType()) {
                    throw new IllegalStateException(
                            "Kinds key/type mismatch: " + e.getKey());
                }
            }
        }
    }
}