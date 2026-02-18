package com.kombat.kombatbackend.engine.strategy;

public class PowExpr implements Expr {

    // 3) INTERNAL
    private final Expr base;
    private final Expr exp;

    // 1) CONSTRUCTOR
    public PowExpr(Expr base, Expr exp) {
        this.base = base;
        this.exp = exp;
    }

    // 2) INTERFACE
    @Override
    public long eval(EvalContext ctx) {
        long a = base.eval(ctx);
        long b = exp.eval(ctx);
        if (b < 0) return 0; // ป้องกันเคสแปลก ๆ
        long r = 1;
        for (long i = 0; i < b; i++) {
            r *= a;
        }
        return r;
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("(");
        base.prettyPrint(sb);
        sb.append(" ^ ");
        exp.prettyPrint(sb);
        sb.append(")");
    }
}