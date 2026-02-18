package com.kombat.kombatbackend.engine.gamestate;

import java.util.Objects;

/**
 * One minion on the board.
 * - type is the fixed enum (FIGHTER/ASSASSIN/DPS/TANK/SUPPORT)
 * - kindName is the human-chosen display name (from setup)
 * - strategyRef is an opaque reference (Strategy) attached at spawn time
 */
public class Minion {

    private MinionType type;
    private String kindName;

    private int hp;
    private final int defenseFactor;
    private Hex position;
    private final Object strategyRef;

    // Phase-2 uses default ownerId=0 (single player). Duel mode uses 1/2.
    private final long ownerId;

    public Minion(int initialHp, int defenseFactor, Hex position, Object strategyRef) {
        this(initialHp, defenseFactor, position, strategyRef, 0L);
    }

    public Minion(int initialHp, int defenseFactor, Hex position, Object strategyRef, long ownerId) {
        if (initialHp < 0) throw new IllegalArgumentException("HP cannot be negative");
        if (defenseFactor < 0) throw new IllegalArgumentException("Defense factor cannot be negative");
        this.hp = initialHp;
        this.defenseFactor = defenseFactor;
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.strategyRef = strategyRef;
        this.ownerId = ownerId;
        checkRep();
    }

    public MinionType getType() {
        return type; }
    public String getKindName() {
        return kindName; }
    public int getHp() {
        return hp; }
    public int getDefenseFactor() {
        return defenseFactor; }
    public Hex getPosition() {
        return position; }
    public Object getStrategyRef() {
        return strategyRef; }

    public long getOwnerId() { return ownerId; }

    public void setType(MinionType type) {
        this.type = type; }

    public void setKindName(String kindName) {
        if (kindName == null || kindName.trim().isEmpty()) this.kindName = null;
        else this.kindName = kindName.trim();
    }

    public void setHp(int newHp) {
        this.hp = Math.max(0, newHp);
        checkRep();
    }

    public void setPosition(Hex hex) {
        this.position = Objects.requireNonNull(hex, "Position cannot be null");
        checkRep();
    }

    private void checkRep() {
        if (hp < 0) throw new IllegalStateException("HP must be non-negative");
        if (defenseFactor < 0) throw new IllegalStateException("Defense factor must be non-negative");
        if (position == null) throw new IllegalStateException("Position must not be null");
        if (ownerId < 0) throw new IllegalStateException("Owner id must be non-negative");
    }
}