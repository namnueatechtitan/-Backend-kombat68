package com.kombat.kombatbackend.engine.strategy;

public interface Expr {
    long eval(EvalContext ctx); // ใช้ ctx เพื่อเข้าถึง env + game state + special vars
    void prettyPrint(StringBuilder sb);
}