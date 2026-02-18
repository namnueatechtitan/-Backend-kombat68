package com.kombat.kombatbackend.engine.evaluator;

public class StopEvaluation extends RuntimeException {

    // 1) CONSTRUCTOR
    public StopEvaluation(String reason) {

        super(reason);
    }
}  // ไว้หยุด 'RUN TURN'