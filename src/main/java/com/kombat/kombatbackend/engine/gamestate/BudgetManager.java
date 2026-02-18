package com.kombat.kombatbackend.engine.gamestate;

import java.util.HashMap;
import java.util.Map;

/**
 * Budget manager.
 *
 * Phase-2 compatibility:
 * - Existing tests/code use a single budget via getBudget()/addBudget()/spendBudget().
 *   Those methods operate on the implicit playerId = 0.
 * - Duel mode can use per-player budgets via getBudget(pid)/addBudget(pid)/spendBudget(pid).
 */
public class BudgetManager {

    private final Map<Long, Long> budgets = new HashMap<Long, Long>();

    public BudgetManager(long initialBudget) {
        if (initialBudget < 0) throw new IllegalArgumentException("Budget cannot be negative");
        budgets.put(0L, initialBudget);
    }

    // Single-budget (phase-2) API

    public long getBudget() {
        return getBudget(0L);
    }

    public void addBudget(long amount) {
        addBudget(0L, amount);
    }

    public void spendBudget(long amount) {
        spendBudget(0L, amount);
    }

    // Multi-budget (duel) API

    /** Initialize/overwrite a player's budget. */
    public void setBudget(long playerId, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Budget cannot be negative");
        budgets.put(playerId, amount);
    }

    /** Alias for setBudget (kept for readability from setup code). */
    public void initPlayer(long playerId, long initialBudget) {
        setBudget(playerId, initialBudget);
    }

    public long getBudget(long playerId) {
        Long v = budgets.get(playerId);
        return (v == null) ? 0L : v;
    }

    public void addBudget(long playerId, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot add negative budget");
        long b = getBudget(playerId);
        budgets.put(playerId, b + amount);
    }

    public void spendBudget(long playerId, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot spend negative budget");
        long b = getBudget(playerId);
        if (b < amount) throw new IllegalStateException("Not enough budget");
        budgets.put(playerId, b - amount);
    }
}
