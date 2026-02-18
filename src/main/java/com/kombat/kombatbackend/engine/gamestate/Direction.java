package com.kombat.kombatbackend.engine.gamestate;

public enum Direction {
    UP, DOWN, UPLEFT, UPRIGHT, DOWNLEFT, DOWNRIGHT;

    // direction number 1..6 (ใช้ tie-break ตามสเปก: เลขน้อยสุด)
    public int dirNum() {
        return switch (this) {
            case UP -> 1;
            case UPRIGHT -> 2;
            case DOWNRIGHT -> 3;
            case DOWN -> 4;
            case DOWNLEFT -> 5;
            case UPLEFT -> 6;
        };
    }
}
