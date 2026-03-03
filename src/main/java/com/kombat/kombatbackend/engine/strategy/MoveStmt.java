package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.evaluator.StopEvaluation;
import com.kombat.kombatbackend.engine.gamestate.Direction;
import com.kombat.kombatbackend.engine.gamestate.MockGameState;

public class MoveStmt implements Stmt {

    // 3) INTERNAL
    private final Direction dir;
    private final MockGameState game;

    // 1) CONSTRUCTOR
    public MoveStmt(Direction dir, MockGameState game) {
        this.dir = dir;
        this.game = game;
    }

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        MockGameState g = ctx.getGameOr(game);
        long pid = g.currentPlayerId();

        // spec: ถ้าไม่มีงบพอจ่าย 1 -> stop evaluation ทันที
        if (g.getBudget(pid) < 1) {
            ctx.log("MOVE " + dir + " STOP(no-budget)");
            throw new StopEvaluation("no budget for move");
        }

        long before = g.getBudget(pid);
        boolean moved = g.moveCurrentMinion(dir);

        ctx.log("P" + pid + " MOVE " + dir + " " + (moved ? "OK" : "NO-OP")
                + " Budget:" + before + "->" + g.getBudget(pid));
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("move ").append(dir.name().toLowerCase());
    }
}
