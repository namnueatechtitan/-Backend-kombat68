package com.kombat.kombatbackend.engine.strategy;

import java.util.List;

public class BlockStmt implements Stmt {

    // 3) INTERNAL
    private final List<Stmt> statements;

    // 1) CONSTRUCTOR
    public BlockStmt(List<Stmt> statements) {

        this.statements = statements;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        for (Stmt s : statements) s.exec(ctx);
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("{");
        for (Stmt s : statements) {
            sb.append("\n  ");
            s.prettyPrint(sb);
        }
        sb.append("\n}");
    }
}