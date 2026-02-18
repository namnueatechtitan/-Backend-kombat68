package com.kombat.kombatbackend.engine.strategy;

public class VarExpr implements Expr {
    // 3) INTERNAL
    private final String name;

    // 1) CONSTRUCTOR
    public VarExpr(String name) {
        this.name = name; }

    // 2) INTERFACE
    public String name() {
        return name; }

    @Override
    public long eval(EvalContext ctx) {
        Long specialValue = evalSpecialVarOrNull(ctx);
        if (specialValue != null) return specialValue;
        return evalNormalVar(ctx);
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append(name);
    }

    // 3) INTERNAL ( ทำเพื่อให้อ่านได้ง่าย และ แก้ไขได้ง่าย ) (HELPERS)
    private Long evalSpecialVarOrNull(EvalContext ctx) {
        return switch (name) {
            case "row" -> ctx.special.row();
            case "col" -> ctx.special.col();
            case "Budget" -> ctx.special.budget();
            case "Int" -> ctx.special.interestRate();
            case "MaxBudget" -> ctx.special.maxBudget();
            case "SpawnsLeft" -> ctx.special.spawnsLeft();
            case "random" -> ctx.special.random0to999();
            default -> null;
        };
    }

    private long evalNormalVar(EvalContext ctx) {
        if (isGlobalName(name)) {
            return ctx.globalVars.getOrDefault(name, 0L);
        }
        return ctx.localVars.getOrDefault(name, 0L);
    }

    private static boolean isGlobalName(String name) {
        return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
    }
}