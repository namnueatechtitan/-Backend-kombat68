package com.kombat.kombatbackend.engine.gamestate;

import com.kombat.kombatbackend.engine.strategy.Strategy;

import java.util.Objects;

/**
 * Immutable setup definition for one minion "kind" (per MinionType).
 * Humans choose:
 *  - kindName (display name)
 *  - defenseFactor
 *  - strategy
 */
public final class MinionKindDef {

    private final MinionType type;
    private final String kindName;
    private final int defenseFactor;
    private final Strategy strategy;

    /**
     * Backward-compatible: display name defaults to enum name.
     */
    public MinionKindDef(MinionType type, int defenseFactor, Strategy strategy) {
        this(type, type.name(), defenseFactor, strategy);
    }

    public MinionKindDef(MinionType type, String kindName, int defenseFactor, Strategy strategy) {
        this.type = Objects.requireNonNull(type, "type must not be null");

        if (kindName == null || kindName.trim().isEmpty()) {
            this.kindName = type.name();
        } else {
            this.kindName = kindName.trim();
        }

        if (defenseFactor < 0) {
            throw new IllegalArgumentException("defenseFactor must be non-negative");
        }
        this.defenseFactor = defenseFactor;

        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public MinionType getType() { return type; }
    public String getKindName() { return kindName; }
    public int getDefenseFactor() { return defenseFactor; }
    public Strategy getStrategy() { return strategy; }
}