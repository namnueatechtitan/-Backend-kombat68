package com.kombat.kombatbackend.engine.strategy;

public interface Stmt {
    void exec(ExecContext ctx);
    void prettyPrint(StringBuilder sb);
}
