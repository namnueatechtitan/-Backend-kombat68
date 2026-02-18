package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.gamestate.Direction;

public class InfoExpr implements Expr {
    public enum Kind { ALLY, OPPONENT, NEARBY }

    // 3) INTERNAL
    private final Kind kind;
    private final Direction dir; // only for nearby

    // 1) CONSTRUCTOR
    public InfoExpr(Kind kind, Direction dir) {
        this.kind = kind;
        this.dir = dir;
    }

    // 2) INTERFACE
    @Override
    public long eval(EvalContext ctx) {
        return switch (kind) {
            case ALLY -> ctx.info.ally();
            case OPPONENT -> ctx.info.opponent();
            case NEARBY -> ctx.info.nearby(dir);
        };
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        switch (kind) {
            case ALLY -> sb.append("ally");
            case OPPONENT -> sb.append("opponent");
            case NEARBY -> {
                sb.append("nearby ");
                sb.append(dir.name().toLowerCase());
            }
        }
    }
}