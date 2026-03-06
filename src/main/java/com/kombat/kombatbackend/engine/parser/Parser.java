package com.kombat.kombatbackend.engine.parser;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.strategy.*;
import com.kombat.kombatbackend.engine.tokenizer.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {


    // FIELDS
    private final Tokenizer tz;
    private Token la;                 // lookahead token
    private final MockGameState game; // context สำหรับ Move/Shoot

    // 1) CONSTRUCTOR
    public Parser(String input, MockGameState game) {
        this.tz = new Tokenizer(input);
        this.la = tz.next();
        this.game = game;
    }


    // 2) INTERFACE
    // เมธอดที่ "คนภายนอก" ควรเรียกใช้มีแค่นี้ (entry point)
    /// แก้ไขให้ test ผ่าน
    public Strategy parseStrategy() {
        List<Stmt> stmts = new ArrayList<>();

        while (la.type != TokenType.EOF) {
            while (la.type == TokenType.SEMI) consume(TokenType.SEMI); // skip extras
            if (la.type == TokenType.EOF) break;

            stmts.add(parseStatement());

            if (la.type == TokenType.SEMI) consume(TokenType.SEMI);    // optional after if/while/block
        }

        if (stmts.isEmpty()) throw new SyntaxException("Strategy must have at least one statement");
        return new Strategy(stmts);
    }


    // 3) INTERNAL

    // statements
    private Stmt parseStatement() {
        return switch (la.type) {
            case LBRACE -> parseBlock();
            case IF -> parseIf();
            case WHILE -> parseWhile();
            default -> parseCommand();
        };
    }

    /// แก้ไขให้ test ผ่าน
    private Stmt parseBlock() {
        consume(TokenType.LBRACE);
        List<Stmt> ss = new ArrayList<>();

        while (la.type != TokenType.RBRACE) {
            if (la.type == TokenType.EOF) throw new SyntaxException("Unclosed block");

            while (la.type == TokenType.SEMI) consume(TokenType.SEMI); // skip extras
            if (la.type == TokenType.RBRACE) break;

            ss.add(parseStatement());

            if (la.type == TokenType.SEMI) consume(TokenType.SEMI);    // optional after if/while/block
        }

        consume(TokenType.RBRACE);
        return new BlockStmt(ss);
    }

    private Stmt parseIf() {
        consume(TokenType.IF);
        consume(TokenType.LPAREN);
        Expr cond = parseExpression();
        consume(TokenType.RPAREN);
        consume(TokenType.THEN);
        Stmt thenS = parseStatement();
        consume(TokenType.ELSE);
        Stmt elseS = parseStatement();
        return new IfStmt(cond, thenS, elseS);
    }

    private Stmt parseWhile() {
        consume(TokenType.WHILE);
        consume(TokenType.LPAREN);
        Expr cond = parseExpression();
        consume(TokenType.RPAREN);
        Stmt body = parseStatement();
        return new WhileStmt(cond, body);
    }

    /// แก้ไขให้ test ผ่าน
    private Stmt parseCommand() {
        // Assignment: IDENT '=' Expression ';'
        if (la.type == TokenType.IDENT) {
            String name = la.lexeme;
            consume(TokenType.IDENT);
            consume(TokenType.ASSIGN);
            Expr e = parseExpression();
            consume(TokenType.SEMI);              // required
            return new AssignStmt(name, e);
        }

        if (la.type == TokenType.DONE) {
            consume(TokenType.DONE);
            consume(TokenType.SEMI);              // required
            return new DoneStmt();
        }

        if (la.type == TokenType.MOVE) {
            consume(TokenType.MOVE);
            Direction d = parseDirection();
            consume(TokenType.SEMI);              // required
            return new MoveStmt(d, game);
        }

        if (la.type == TokenType.SHOOT) {
            consume(TokenType.SHOOT);
            Direction d = parseDirection();
            Expr x = parseExpression();
            consume(TokenType.SEMI);              // required
            return new ShootStmt(d, x, game);
        }

        throw new SyntaxException("Expected statement/command, found: " + la);
    }

    private Direction parseDirection() {
        return switch (la.type) {
            case UP -> { consume(TokenType.UP); yield Direction.UP; }
            case DOWN -> { consume(TokenType.DOWN); yield Direction.DOWN; }
            case UPLEFT -> { consume(TokenType.UPLEFT); yield Direction.UPLEFT; }
            case UPRIGHT -> { consume(TokenType.UPRIGHT); yield Direction.UPRIGHT; }
            case DOWNLEFT -> { consume(TokenType.DOWNLEFT); yield Direction.DOWNLEFT; }
            case DOWNRIGHT -> { consume(TokenType.DOWNRIGHT); yield Direction.DOWNRIGHT; }
            default -> throw new SyntaxException("Expected direction, found: " + la);
        };
    }

    // expressions
    // Expression → Term ((+|-) Term)*
    private Expr parseExpression() {
        Expr left = parseTerm();
        while (la.type == TokenType.PLUS || la.type == TokenType.MINUS) {
            TokenType op = la.type;
            consume(op);
            Expr right = parseTerm();
            left = new BinaryExpr(left, right, op == TokenType.PLUS ? BinaryExpr.Op.ADD : BinaryExpr.Op.SUB);
        }
        return left;
    }

    // Term → Factor ((*|/|%) Factor)*
    private Expr parseTerm() {
        Expr left = parseFactor();
        while (la.type == TokenType.TIMES || la.type == TokenType.DIVIDE || la.type == TokenType.MOD) {
            TokenType op = la.type;
            consume(op);
            Expr right = parseFactor();
            left = switch (op) {
                case TIMES -> new BinaryExpr(left, right, BinaryExpr.Op.MUL);
                case DIVIDE -> new BinaryExpr(left, right, BinaryExpr.Op.DIV);
                case MOD -> new BinaryExpr(left, right, BinaryExpr.Op.MOD);
                default -> throw new IllegalStateException();
            };
        }
        return left;
    }

    // Factor → Power (^ Factor)?   (right-assoc)
    private Expr parseFactor() {
        Expr base = parsePower();
        if (la.type == TokenType.POW) {
            consume(TokenType.POW);
            Expr exp = parseFactor();
            return new PowExpr(base, exp);
        }
        return base;
    }

    // Power → number | identifier | ( Expression ) | InfoExpression
    private Expr parsePower() {
        if (la.type == TokenType.NUMBER) {
            long v = la.number;
            consume(TokenType.NUMBER);
            return new NumberExpr(v);
        }

        if (la.type == TokenType.IDENT) {
            String name = la.lexeme;
            consume(TokenType.IDENT);
            return new VarExpr(name);
        }

        if (la.type == TokenType.LPAREN) {
            consume(TokenType.LPAREN);
            Expr e = parseExpression();
            consume(TokenType.RPAREN);
            return e;
        }

        // InfoExpression → ally | opponent | nearby Direction
        if (la.type == TokenType.ALLY) {
            consume(TokenType.ALLY);
            return new InfoExpr(InfoExpr.Kind.ALLY, null);
        }
        if (la.type == TokenType.OPPONENT) {
            consume(TokenType.OPPONENT);
            return new InfoExpr(InfoExpr.Kind.OPPONENT, null);
        }
        if (la.type == TokenType.NEARBY) {
            consume(TokenType.NEARBY);
            Direction d = parseDirection();
            return new InfoExpr(InfoExpr.Kind.NEARBY, d);
        }

        throw new SyntaxException("Expected number/identifier/(expr)/infoExpr, found: " + la);
    }

    // token utilities (internal)
    private void consume(TokenType t) {
        if (la.type != t) throw new SyntaxException("Expected " + t + " but found " + la);
        la = tz.next();
    }

}
