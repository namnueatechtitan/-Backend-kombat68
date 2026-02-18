package com.kombat.kombatbackend.engine.gamestate;

import java.util.Objects;

/**
 * Setup-time input for a "kind" of minion.
 *
 * IMPORTANT:
 * - MinionType is the fixed enum (FIGHTER/ASSASSIN/DPS/TANK/SUPPORT).
 * - kindName is the human-chosen display name (what you want to show on the board/UI).
 * - defenseFactor + strategyCode are also chosen by humans during setup.
 */
public final class KindInput {

    public final MinionType type;
    public final String kindName;
    public final int defenseFactor;
    public final String strategyCode;

    public KindInput(String kindName, int defenseFactor, String strategyCode) {
        this(MinionType.fromUserText(kindName), normalizeDisplayName(MinionType.fromUserText(kindName)), defenseFactor, strategyCode);
    }

    public KindInput(String typeText, String kindName, int defenseFactor, String strategyCode) {
        this(MinionType.fromUserText(typeText), kindName, defenseFactor, strategyCode);
    }

    public KindInput(MinionType type, String kindName, int defenseFactor, String strategyCode) {
        this.type = Objects.requireNonNull(type, "type must not be null");

        if (kindName == null || kindName.trim().isEmpty()) {
            // fallback: use enum name if blank
            this.kindName = normalizeDisplayName(type);
        } else {
            this.kindName = kindName.trim();
        }

        if (defenseFactor < 0) {
            throw new IllegalArgumentException("defenseFactor must be non-negative");
        }
        this.defenseFactor = defenseFactor;

        this.strategyCode = Objects.requireNonNull(strategyCode, "strategyCode must not be null");
    }

    private static String normalizeDisplayName(MinionType type) {
        String n = type.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}