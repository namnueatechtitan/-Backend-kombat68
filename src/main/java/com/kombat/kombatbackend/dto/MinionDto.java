package com.kombat.kombatbackend.dto;

public class MinionDto {

    private long ownerId;
    private String type;
    private int hp;
    private int x;
    private int y;

    public MinionDto(long ownerId, String type, int hp, int x, int y) {
        this.ownerId = ownerId;
        this.type = type;
        this.hp = hp;
        this.x = x;
        this.y = y;
    }

    public long getOwnerId() { return ownerId; }
    public String getType() { return type; }
    public int getHp() { return hp; }
    public int getX() { return x; }
    public int getY() { return y; }
}
