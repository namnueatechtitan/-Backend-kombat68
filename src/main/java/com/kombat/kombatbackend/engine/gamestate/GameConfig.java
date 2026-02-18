package com.kombat.kombatbackend.engine.gamestate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class GameConfig {

    public enum Key {
        SPAWN_COST("spawn_cost"),
        HEX_PURCHASE_COST("hex_purchase_cost"),
        INIT_BUDGET("init_budget"),
        INIT_HP("init_hp"),
        TURN_BUDGET("turn_budget"),
        MAX_BUDGET("max_budget"),
        INTEREST_PCT("interest_pct"),
        MAX_TURNS("max_turns"),
        MAX_SPAWNS("max_spawns");

        final String raw;
        Key(String raw) { this.raw = raw; }

        static Key fromRaw(String s) {
            for (Key k : values()) {
                if (k.raw.equals(s)) return k;
            }
            return null;
        }
    }

    private final long spawnCost;
    private final long hexPurchaseCost;
    private final long initBudget;
    private final long initHp;
    private final long turnBudget;
    private final long maxBudget;
    private final long interestPct;
    private final long maxTurns;
    private final long maxSpawns;

    public GameConfig(long spawnCost,
                      long hexPurchaseCost,
                      long initBudget,
                      long initHp,
                      long turnBudget,
                      long maxBudget,
                      long interestPct,
                      long maxTurns,
                      long maxSpawns) {
        this.spawnCost = nonNegative(spawnCost, "spawn_cost");
        this.hexPurchaseCost = nonNegative(hexPurchaseCost, "hex_purchase_cost");
        this.initBudget = nonNegative(initBudget, "init_budget");
        this.initHp = nonNegative(initHp, "init_hp");
        this.turnBudget = nonNegative(turnBudget, "turn_budget");
        this.maxBudget = nonNegative(maxBudget, "max_budget");
        this.interestPct = nonNegative(interestPct, "interest_pct");
        this.maxTurns = nonNegative(maxTurns, "max_turns");
        this.maxSpawns = nonNegative(maxSpawns, "max_spawns");

        if (this.maxBudget < this.initBudget) {
            throw new IllegalArgumentException("max_budget must be >= init_budget");
        }
    }

    private static long nonNegative(long v, String name) {
        if (v < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return v;
    }

    public static GameConfig sampleDefaults() {
        return new GameConfig(
                100,
                1000,
                10000,
                100,
                90,
                23456,
                5,
                69,
                47
        );
    }

    public long spawnCost() { return spawnCost; }
    public long hexPurchaseCost() { return hexPurchaseCost; }
    public long initBudget() { return initBudget; }
    public long initHp() { return initHp; }
    public long turnBudget() { return turnBudget; }
    public long maxBudget() { return maxBudget; }
    public long interestPct() { return interestPct; }
    public long maxTurns() { return maxTurns; }
    public long maxSpawns() { return maxSpawns; }


    public static GameConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (BufferedReader br = Files.newBufferedReader(path)) {
            return parse(br);
        }
    }


    public static GameConfig parse(Reader r) throws IOException {
        Objects.requireNonNull(r, "reader must not be null");

        Map<Key, Long> vals = new EnumMap<Key, Long>(Key.class);
        BufferedReader br = new BufferedReader(r);

        String line;
        int ln = 0;
        while ((line = br.readLine()) != null) {
            ln++;
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) continue;

            int eq = s.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Invalid config line " + ln + ": expected name=value");
            }

            String name = s.substring(0, eq).trim();
            String value = s.substring(eq + 1).trim();

            Key k = Key.fromRaw(name);
            if (k == null) {
                throw new IllegalArgumentException("Unknown config key at line " + ln + ": " + name);
            }
            if (vals.containsKey(k)) {
                throw new IllegalArgumentException("Duplicate config key at line " + ln + ": " + name);
            }

            long v;
            try {
                v = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid long at line " + ln + ": " + value);
            }
            vals.put(k, v);
        }

        // require all keys
        for (Key k : Key.values()) {
            if (!vals.containsKey(k)) {
                throw new IllegalArgumentException("Missing config key: " + k.raw);
            }
        }

        return new GameConfig(
                vals.get(Key.SPAWN_COST),
                vals.get(Key.HEX_PURCHASE_COST),
                vals.get(Key.INIT_BUDGET),
                vals.get(Key.INIT_HP),
                vals.get(Key.TURN_BUDGET),
                vals.get(Key.MAX_BUDGET),
                vals.get(Key.INTEREST_PCT),
                vals.get(Key.MAX_TURNS),
                vals.get(Key.MAX_SPAWNS)
        );
    }
}