package com.kombat.kombatbackend.engine.tokenizer;

public class Token {

    // 2) INTERFACE
    public final TokenType type;
    public final String lexeme;
    public final long number; // only valid for NUMBER
    public final int pos;

    // 1) CONSTRUCTOR
    public Token(TokenType type, String lexeme, long number,int pos) {
        this.type = type;
        this.lexeme = lexeme;
        this.number = number;
        this.pos = pos;
    }

    // 2) INTERFACE
    public static Token simple(TokenType type, String lexeme, int pos) {

        return new Token(type,lexeme,0L,pos);
    }

    public static Token number(long v, int pos) {
        return new Token(TokenType.NUMBER,Long.toString(v),v,pos);
    }

    @Override
    public String toString() {

        return type + "(" + lexeme + ")";
    }
}