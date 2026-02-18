package com.kombat.kombatbackend.engine.tokenizer;

public enum TokenType {

    // 2) INTERFACE
    EOF,

    // literals & identifiers
    NUMBER,
    IDENT,

    // keywords
    IF, THEN, ELSE, WHILE,
    DONE, MOVE, SHOOT,
    ALLY, OPPONENT, NEARBY,

    // directions
    UP, DOWN, UPLEFT, UPRIGHT, DOWNLEFT, DOWNRIGHT,

    // symbols
    LBRACE, RBRACE, LPAREN, RPAREN, SEMI,
    ASSIGN, // =
    PLUS, MINUS, TIMES, DIVIDE, MOD, POW // ^
}

