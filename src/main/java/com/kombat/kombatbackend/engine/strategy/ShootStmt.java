package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.evaluator.StopEvaluation;
import com.kombat.kombatbackend.engine.gamestate.Direction;
import com.kombat.kombatbackend.engine.gamestate.MockGameState;

public class ShootStmt implements Stmt {

    // 3) INTERNAL
    private final Direction dir;
    private final Expr expenditure;
    private final MockGameState game;

    // 1) CONSTRUCTOR
    public ShootStmt(Direction dir, Expr expenditure, MockGameState game) {
        this.dir = dir;
        this.expenditure = expenditure;
        this.game = game;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        long x = expenditure.eval(ctx.eval);
        if (x < 0) x = 0;

        long cost = x + 1;
        MockGameState g = ctx.getGameOr(game);
        long pid = g.currentPlayerId();
        long before = g.getBudget(pid);

        // spec: ถ้างบไม่พอ -> หยุด evaluation ของ strategy ทันที
        if (before < cost) {
            ctx.log("P" + pid + " SHOOT " + dir + " x=" + x + " STOP(no-budget) Budget:" + before);
            throw new StopEvaluation("no budget for shoot");
        }
        MockGameState.AttackResult r = g.shoot(dir, x);

        ctx.log("P" + pid + " SHOOT " + dir + " x=" + x + " " + r
                + " Budget:" + before + "->" + g.getBudget(pid));
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("shoot ").append(dir.name().toLowerCase()).append(" ");
        expenditure.prettyPrint(sb);
    }
}
