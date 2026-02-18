package com.kombat.kombatbackend.engine.gamestate;

public class Board {

    private static final int SIZE = 8;

    private final Hex[][] grid;

    // constructor
    public Board() {
        this.grid = new Hex[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                grid[x][y] = new Hex(x, y);
            }
        }
        checkRep();
    }

    // interface: queries

    public Hex getHex(int x, int y) {
        if (!isInsideBoard(x, y)) {
            throw new IllegalArgumentException(
                    "Coordinates out of board: (" + x + ", " + y + ")"
            );
        }
        return grid[x][y];
    }

    public boolean isInsideBoard(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    // internal
    private void checkRep() {
        if (grid.length != SIZE) {
            throw new IllegalStateException("Board must have 8 rows");
        }
        for (int x = 0; x < SIZE; x++) {
            if (grid[x] == null || grid[x].length != SIZE) {
                throw new IllegalStateException("Board must have 8 columns");
            }
            for (int y = 0; y < SIZE; y++) {
                Hex h = grid[x][y];
                if (h == null) {
                    throw new IllegalStateException("Hex cannot be null");
                }
                if (h.getX() != x || h.getY() != y) {
                    throw new IllegalStateException(
                            "Hex coordinate mismatch at (" + x + ", " + y + ")"
                    );
                }
            }
        }
    }
}
