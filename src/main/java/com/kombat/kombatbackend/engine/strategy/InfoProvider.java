package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.gamestate.Direction;

public interface InfoProvider {
    long opponent();               // encoding distance+direction
    long ally();                   // encoding distance+direction
    long nearby(Direction dir);    // 100x+10y+z
}