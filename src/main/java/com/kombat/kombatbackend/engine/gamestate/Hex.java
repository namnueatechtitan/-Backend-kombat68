package com.kombat.kombatbackend.engine.gamestate;

import java.util.Objects;

public class Hex {

    private final int x;
    private final int y;
    private Minion occupant;

    // constructor
    public Hex(int x, int y) {
        if (!isValidCoordinate(x) || !isValidCoordinate(y)) {
            throw new IllegalArgumentException(
                    "Hex coordinates must be in range [0, 7]: (" + x + ", " + y + ")"
            );
        }
        this.x = x;
        this.y = y;
        this.occupant = null;
        checkRep();
    }

    // interface: queries
    public int getX() {

        return x;
    }

    public int getY() {

        return y;
    }

    public Minion getOccupant() {

        return occupant;
    }

    public boolean isOccupied() {

        return occupant != null;
    }

    // interface: mutators
    public void placeMinion(Minion m) {
        Objects.requireNonNull(m, "Minion cannot be null");
        if (isOccupied()) {
            throw new IllegalStateException("Hex is already occupied");
        }
        this.occupant = m;
        checkRep();
    }

    public void removeMinion() {
        this.occupant = null;
        checkRep();
    }

    // internal
    private void checkRep() {
        if (!isValidCoordinate(x) || !isValidCoordinate(y)) {
            throw new IllegalStateException("Hex coordinates out of bounds");
        }
    }

    private static boolean isValidCoordinate (int v) {
        return v >= 0 && v <= 7;
    }
}
