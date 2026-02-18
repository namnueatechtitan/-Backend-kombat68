package com.kombat.kombatbackend.engine.tokenizer;

import com.kombat.kombatbackend.engine.parser.SyntaxException;

import java.util.HashMap;
import java.util.Map;

public class Tokenizer {

    // 3) INTERNAL
    private final String s;
    private int i = 0;

    // 3) INTERNAL (KEYWORD TABLE)
    private static final Map<String, TokenType> KW = new HashMap<>();
    static {
        KW.put("if", TokenType.IF);
        KW.put("then", TokenType.THEN);
        KW.put("else", TokenType.ELSE);
        KW.put("while", TokenType.WHILE);

        KW.put("done", TokenType.DONE);
        KW.put("move", TokenType.MOVE);
        KW.put("shoot", TokenType.SHOOT);

        KW.put("ally", TokenType.ALLY);
        KW.put("opponent", TokenType.OPPONENT);
        KW.put("nearby", TokenType.NEARBY);

        KW.put("up", TokenType.UP);
        KW.put("down", TokenType.DOWN);
        KW.put("upleft", TokenType.UPLEFT);
        KW.put("upright", TokenType.UPRIGHT);
        KW.put("downleft", TokenType.DOWNLEFT);
        KW.put("downright", TokenType.DOWNRIGHT);
    }

    // 1) CONSTRUCTOR
    public Tokenizer(String input) {
        this.s = input;
    }

    // 2) INTERFACE
    // เมธอดที่คนอื่นเรียกใช้: ขอ token ถัดไปทีละตัว
    public Token next() {
        skipWsAndComments();
        if (i >= s.length()) return Token.simple(TokenType.EOF, "", i);

        int start = i;
        char c = s.charAt(i);

        // number
        if (Character.isDigit(c)) {
            long v = 0;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                v = v * 10 + (s.charAt(i) - '0');
                i++;
            }
            return Token.number(v, start);
        }

        // identifier / keyword
        if (Character.isLetter(c)) {
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '_') {
                    sb.append(ch); i++;
                } else break;
            }
            String w = sb.toString();
            TokenType t = KW.get(w);
            if (t != null) return Token.simple(t, w, start);
            return Token.simple(TokenType.IDENT, w, start);
        }

        // single-char tokens
        i++;
        return switch (c) {
            case '{' -> Token.simple(TokenType.LBRACE, "{", start);
            case '}' -> Token.simple(TokenType.RBRACE, "}", start);
            case '(' -> Token.simple(TokenType.LPAREN, "(", start);
            case ')' -> Token.simple(TokenType.RPAREN, ")", start);
            case ';' -> Token.simple(TokenType.SEMI, ";", start);

            case '+' -> Token.simple(TokenType.PLUS, "+", start);
            case '-' -> Token.simple(TokenType.MINUS, "-", start);
            case '*' -> Token.simple(TokenType.TIMES, "*", start);
            case '/' -> Token.simple(TokenType.DIVIDE, "/", start);
            case '%' -> Token.simple(TokenType.MOD, "%", start);
            case '^' -> Token.simple(TokenType.POW, "^", start);

            case '=' -> Token.simple(TokenType.ASSIGN, "=", start);
            default -> throw new SyntaxException("Invalid character '" + c + "' at " + start);
        };
    }

    // 3) INTERNAL (HELPERS)
    private void skipWsAndComments() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                i += 2;
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            break;
        }
    }
}
