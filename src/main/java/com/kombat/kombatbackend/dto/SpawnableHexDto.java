package com.kombat.kombatbackend.dto;

public class SpawnableHexDto {

    private int row;
    private int col;
    private long ownerId;

    public SpawnableHexDto(int row, int col, long ownerId) {
        this.row = row;
        this.col = col;
        this.ownerId = ownerId;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public long getOwnerId() { return ownerId; }
}