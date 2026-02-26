package com.kombat.kombatbackend.engine.gamestate;

import com.kombat.kombatbackend.engine.strategy.Strategy;

import java.util.Objects;

/**
 * Immutable setup definition for one minion "kind" (per MinionType).
 * Humans choose:
 *  - kindName (display name)
 *  - defenseFactor
 *  - rawStrategy (original text)
 *  - strategy (parsed AST)
 */
public final class MinionKindDef {

    private final MinionType type;
    private final String kindName;
    private final int defenseFactor;

    // 🔥 เพิ่มอันนี้ไว้เก็บ code ที่ user พิมพ์
    private final String rawStrategy;

    // parsed strategy (AST)
    private final Strategy strategy;

    /**
     * Backward-compatible constructor:
     * display name defaults to enum name.
     */
    public MinionKindDef(
            MinionType type,
            int defenseFactor,
            String rawStrategy,
            Strategy strategy
    ) {
        this(type, type.name(), defenseFactor, rawStrategy, strategy);
    }

    public MinionKindDef(
            MinionType type,
            String kindName,
            int defenseFactor,
            String rawStrategy,
            Strategy strategy
    ) {
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

        if (rawStrategy == null || rawStrategy.trim().isEmpty()) {
            throw new IllegalArgumentException("rawStrategy must not be empty");
        }
        this.rawStrategy = rawStrategy;

        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public MinionType getType() {
        return type;
    }

    public String getKindName() {
        return kindName;
    }

    public int getDefenseFactor() {
        return defenseFactor;
    }

    // 👇 เอาไว้แสดงใน UI
    public String getRawStrategy() {
        return rawStrategy;
    }

    // 👇 ใช้ตอน game execute
    public Strategy getStrategy() {
        return strategy;
    }
}