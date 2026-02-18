package com.kombat.kombatbackend.engine.strategy;

public class NumberExpr implements Expr {

    // 3) INTERNAL
    private final long value;

    // 1) CONSTRUCTOR
    public NumberExpr(long value) {
        this.value = value; }

    // 2) INTERFACE
    @Override
    public long eval(EvalContext ctx) {
        return value; }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append(value); }
}