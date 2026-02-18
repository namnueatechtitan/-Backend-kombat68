package com.kombat.kombatbackend.engine.strategy;

public class IfStmt implements Stmt {

    // 3) INTERNAL
    private final Expr cond;
    private final Stmt thenS;
    private final Stmt elseS;

    // 1) CONSTRUCTOR
    public IfStmt(Expr cond, Stmt thenS, Stmt elseS) {
        this.cond = cond;
        this.thenS = thenS;
        this.elseS = elseS;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        long v = cond.eval(ctx.eval);
        if (v > 0) thenS.exec(ctx);
        else elseS.exec(ctx);
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("if (");
        cond.prettyPrint(sb);
        sb.append(") then ");
        thenS.prettyPrint(sb);
        sb.append(" else ");
        elseS.prettyPrint(sb);
    }
}
