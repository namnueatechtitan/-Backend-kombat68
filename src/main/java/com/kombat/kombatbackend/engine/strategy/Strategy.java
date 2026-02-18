package com.kombat.kombatbackend.engine.strategy;

import java.util.List;

public class Strategy {
    // 3) INTERNAL
    private final List<Stmt> statements;

    // 1) CONSTRUCTOR
    public Strategy(List<Stmt> statements) {
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("Strategy must have at least one statement");
        }
        this.statements = statements;
    }

    // 2) INTERFACE
    public void exec(ExecContext ctx) {
        for (Stmt s : statements) {
            s.exec(ctx);
        }
    }

    public void prettyPrint(StringBuilder sb) {
        for (Stmt s : statements) {
            s.prettyPrint(sb);
            sb.append("\n");
        }
    }
}