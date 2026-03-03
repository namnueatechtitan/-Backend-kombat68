package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.gamestate.MockGameState;

import java.util.List;
import java.util.Map;


public class ExecContext {

    // 2) INTERFACE
    public final EvalContext eval;
    public final Map<String, Long> localVars;
    public final Map<String, Long> globalVars;
    public final List<String> actionLog;
    private final MockGameState runtimeGame;

    // 1) CONSTRUCTOR
    public ExecContext(EvalContext eval,
                       Map<String, Long> localVars,
                       Map<String, Long> globalVars,
                       List<String> actionLog) {
        this(eval, localVars, globalVars, actionLog, null);
    }

    public ExecContext(EvalContext eval,
                       Map<String, Long> localVars,
                       Map<String, Long> globalVars,
                       List<String> actionLog,
                       MockGameState runtimeGame) {
        this.eval = eval;
        this.localVars = localVars;
        this.globalVars = globalVars;
        this.actionLog = actionLog;
        this.runtimeGame = runtimeGame;
    }

    // 2) INTERFACE
    public void log(String s) {
        actionLog.add(s);
    }


    public MockGameState getGameOr(MockGameState fallback) {
        return runtimeGame != null ? runtimeGame : fallback;
    }

    public boolean isSpecialVar(String name) {
        return name.equals("row") || name.equals("col") || name.equals("Budget") ||
                name.equals("Int") || name.equals("MaxBudget") || name.equals("SpawnsLeft") ||
                name.equals("random");
    }
}