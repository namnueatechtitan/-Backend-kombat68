package com.kombat.kombatbackend.engine.gamestate;

public enum MinionType {
    FIGHTER,
    ASSASSIN,
    DPS,
    TANK,
    SUPPORT;

    public static MinionType fromUserText(String s) {
        if (s == null) {
            throw new IllegalArgumentException("minion type must not be null");
        }

        String t = s.trim().toUpperCase().replaceAll("\\s+", "");
        if (t.isEmpty()) {
            throw new IllegalArgumentException("minion type must not be blank");
        }

        // Classic switch: compatible with older Java (no "case A, B ->" syntax)
        switch (t) {
            case "FIGHTER":
            case "FIGTHER":
                return FIGHTER;

            case "ASSASSIN":
            case "ASSASIN":
                return ASSASSIN;

            case "DPS":
            case "CARRY":
            case "CARY":
                return DPS;

            case "TANK":
                return TANK;

            case "SUPPORT":
            case "SUP":
                return SUPPORT;

            default:
                throw new IllegalArgumentException(
                        "Unknown minion type: '" + s + "'. Allowed: Fighter, Assasin, DPS, Tank, Support"
                );
        }
    }
}