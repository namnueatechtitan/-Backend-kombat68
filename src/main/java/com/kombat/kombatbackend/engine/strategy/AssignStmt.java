package com.kombat.kombatbackend.engine.strategy;

public class AssignStmt implements Stmt {

    // FIELDS
    private final String name;
    private final Expr expr;

    // 1) CONSTRUCTOR
    public AssignStmt(String name, Expr expr) {
        this.name = name;
        this.expr = expr;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        if (ctx.isSpecialVar(name)) {
            ctx.log("ASSIGN " + name + " = <no-op special var>");
            return; // no-op ตามสเปก
        }
        long v = expr.eval(ctx.eval);
        if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
            ctx.globalVars.put(name, v);
        } else {
            ctx.localVars.put(name, v);
        }
        ctx.log("ASSIGN " + name + " = " + v);
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append(name).append(" = ");
        expr.prettyPrint(sb);
    }
}