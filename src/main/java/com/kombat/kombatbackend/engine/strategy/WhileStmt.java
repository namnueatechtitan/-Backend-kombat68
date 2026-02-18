package com.kombat.kombatbackend.engine.strategy;

public class WhileStmt implements Stmt {

    // 3) INTERNAL
    private final Expr cond;
    private final Stmt body;

    // 1) CONSTRUCTOR
    public WhileStmt(Expr cond, Stmt body) {
        this.cond = cond;
        this.body = body;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        for (int counter = 0; counter < 10000; counter++) {
            long v = cond.eval(ctx.eval);
            if (v <= 0) return;
            body.exec(ctx);
        }
        ctx.log("STOP(while-limit-10000)");
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("while (");
        cond.prettyPrint(sb);
        sb.append(") ");
        body.prettyPrint(sb);
    }
}