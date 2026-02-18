package com.kombat.kombatbackend.engine.strategy;

public interface SpecialVars {
    long row();
    long col();
    long budget();
    long interestRate(); // Int ในสเปก
    long maxBudget();
    long spawnsLeft();
    long random0to999();
}