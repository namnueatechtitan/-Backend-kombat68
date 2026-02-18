package com.kombat.kombatbackend.engine.strategy;

import java.util.Map;

// 2) INTERFACE

public class EvalContext {
    public final Map<String, Long> localVars;
    public final Map<String, Long> globalVars;
    public final SpecialVars special;
    public final InfoProvider info;

    // 1) CONSTRUCTOR
    public EvalContext(Map<String, Long> localVars,
                       Map<String, Long> globalVars,
                       SpecialVars special,
                       InfoProvider info) {
        this.localVars = localVars;
        this.globalVars = globalVars;
        this.special = special;
        this.info = info;
    }
}
