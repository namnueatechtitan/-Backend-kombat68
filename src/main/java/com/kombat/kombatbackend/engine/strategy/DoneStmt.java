package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.evaluator.StopEvaluation;

public class DoneStmt implements Stmt {

    // 2) INTERFACE
    @Override
    public void exec(ExecContext ctx) {
        ctx.log("DONE");
        throw new StopEvaluation("done");
    }

    @Override
    public void prettyPrint(StringBuilder sb) {

        sb.append("done");
    }
}
