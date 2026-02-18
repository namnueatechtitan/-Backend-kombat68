package com.kombat.kombatbackend.engine.strategy;

import com.kombat.kombatbackend.engine.evaluator.StopEvaluation;

public class BinaryExpr implements Expr {

    // 2) INTERFACE
    // (1) enum Op เป็นส่วนของ public contract ของ BinaryExpr
    public enum Op { ADD, SUB, MUL, DIV, MOD, LT, LE, GT, GE, EQ, NE }

    // 3) INTERNAL
    protected final Expr left;
    protected final Expr right;
    protected final Op op;

    // 1) CONSTRUCTOR
    public BinaryExpr(Expr left, Expr right, Op op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    // 2) INTERFACE
    @Override
    public long eval(EvalContext ctx) {
        long a = left.eval(ctx);
        long b = right.eval(ctx);

        return switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;

            case DIV -> {
                if (b == 0) throw new StopEvaluation("division by zero");
                yield a / b;
            }
            case MOD -> {
                if (b == 0) throw new StopEvaluation("mod by zero");
                yield a % b;
            }

            case LT -> (a < b) ? 1 : 0;
            case LE -> (a <= b) ? 1 : 0;
            case GT -> (a > b) ? 1 : 0;
            case GE -> (a >= b) ? 1 : 0;
            case EQ -> (a == b) ? 1 : 0;
            case NE -> (a != b) ? 1 : 0;
        };
    }

    @Override
    public void prettyPrint(StringBuilder sb) {
        sb.append("(");
        left.prettyPrint(sb);
        sb.append(opToString(op));
        right.prettyPrint(sb);
        sb.append(")");
    }

    // 3) INTERNAL  ( ทำเพื่อให้อ่านได้ง่าย และ แก้ไขได้ง่าย ) (HELPERS)
    private static String opToString(Op op) {
        return switch (op) {
            case ADD -> " + ";
            case SUB -> " - ";
            case MUL -> " * ";
            case DIV -> " / ";
            case MOD -> " % ";
            case LT  -> " < ";
            case LE  -> " <= ";
            case GT  -> " > ";
            case GE  -> " >= ";
            case EQ  -> " == ";
            case NE  -> " != ";
        };
    }

}
