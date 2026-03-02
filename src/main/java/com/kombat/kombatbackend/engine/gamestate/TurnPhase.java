package com.kombat.kombatbackend.engine.gamestate;

public enum TurnPhase {

    // ใช้ตอนตั้งค่าก่อนเริ่มเกม
    SETUP,

    // 2 เทิร์นแรกของเกม
    FREE_SPAWN,

    // ช่วงซื้อ hex
    BUY_HEX,

    // ช่วงทำ action / execute strategy
    ACTION,

    // เกมจบ
    END
}