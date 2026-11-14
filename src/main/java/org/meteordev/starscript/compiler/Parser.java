package org.meteordev.starscript.compiler;

import org.meteordev.starscript.utils.Error;

import java.util.*;
import java.util.function.Supplier;

/** Parser that produces AST (abstract syntax tree) from starscript code and reports errors. */
public class Parser {
    public static final Set<String> KEYWORDS = Set.of("null", "true", "false", "and", "or");

    private final Lexer lexer;

    private final TokenData previous = new TokenData();
    private final TokenData current = new TokenData();

    private int expressionDepth;

    private Parser(String source) {
        lexer = new Lexer(source);
    }

    private Result parse_() {
        Result result = new Result();

        advance();

        while (!isAtEnd()) {
            try {
                result.exprs.add(statement());
            } catch (ParseException e) {
                result.errors.add(e.error);
                synchronize();
            }
        }

        return result;
    }

    /** Parses starscript code and returns {@link Result}. */
    public static Result parse(String source) {
        return new Parser(source).parse_();
    }

    // Statements

    private Expr statement() {
        if (match(Token.Section)) {
            if (previous.lexeme.isEmpty()) error("Expected section index.", null);

            int start = previous.start;

            String digits = previous.lexeme.replaceFirst("^0+(?=.)", "");
            boolean tooLarge = digits.length() > 3 || Integer.parseInt(digits) > 255;
            int index = tooLarge ? 0 : Integer.parseInt(digits);

            Expr expr = expression();
            expr = new Expr.Section(start, previous.end, index, expr);

            if (tooLarge) error("Section index cannot be larger than 255.", expr);
            return expr;
        }

        return expression();
    }

    // Expressions

    private Expr expression() {
        return conditional();
    }

    private Expr conditional() {
        int start = previous.start;
        Expr expr = or();

        if (match(Token.QuestionMark)) {
            Expr trueExpr = statement();
            consume(Token.Colon, "Expected ':' after first part of condition.", expr);
            Expr falseExpr = statement();
            expr = new Expr.Conditional(start, previous.end, expr, trueExpr, falseExpr);
        }

        return expr;
    }

    private Expr or() {
        return logical(this::and, Token.Or);
    }

    private Expr and() {
        return logical(this::equality, Token.And);
    }

    private Expr equality() {
        return binary(this::comparison, Token.EqualEqual, Token.BangEqual);
    }

    private Expr comparison() {
        return binary(this::bitOr, Token.Greater, Token.GreaterEqual, Token.Less, Token.LessEqual);
    }

    private Expr bitOr() {
        return binary(this::bitXor, Token.VBar);
    }

    private Expr bitXor() {
        return binary(this::bitAnd, Token.DoubleUpArrow);
    }

    private Expr bitAnd() {
        return binary(this::shift, Token.Ampersand);
    }

    private Expr shift() {
        return binary(this::term, Token.DoubleLess, Token.DoubleGreater, Token.TripleGreater);
    }

    private Expr term() {
        return binary(this::factor, Token.Plus, Token.Minus);
    }

    private Expr factor() {
        return binary(this::unary, Token.Star, Token.Slash, Token.Percentage);
    }

    private Expr unary() {
        if (match(Token.Bang, Token.Minus, Token.Tilde)) {
            int start = previous.start;

            Token op = previous.token;
            Expr right = unary();
            return new Expr.Unary(start, previous.end, op, right);
        }

        return power();
    }

    /** Right-associative; the right operand is a unary expression, so {@code 2^-1} works and {@code -2^2} is {@code -(2^2)}. */
    private Expr power() {
        int start = previous.start;
        Expr expr = call();

        if (match(Token.UpArrow)) {
            Expr right = unary();
            expr = new Expr.Binary(start, previous.end, expr, Token.UpArrow, right);
        }

        return expr;
    }

    private Expr binary(Supplier<Expr> operand, Token... ops) {
        int start = previous.start;
        Expr expr = operand.get();

        while (match(ops)) {
            Token op = previous.token;
            Expr right = operand.get();
            expr = new Expr.Binary(start, previous.end, expr, op, right);
        }

        return expr;
    }

    private Expr logical(Supplier<Expr> operand, Token opToken) {
        Expr expr = operand.get();

        while (match(opToken)) {
            int start = previous.start;

            Expr right = operand.get();
            expr = new Expr.Logical(start, previous.end, expr, opToken, right);
        }

        return expr;
    }

    private Expr call() {
        Expr expr = primary();
        int start = previous.start;

        while (true) {
            if (match(Token.LeftParen)) {
                expr = finishCall(expr);
            }
            else if (match(Token.Dot)) {
                if (!check(Token.Identifier)) {
                    expr = new Expr.Get(start, current.end, expr, "");
                }

                TokenData name = consume(Token.Identifier, "Expected field name after '.'.", expr);
                expr = new Expr.Get(start, previous.end, expr, name.lexeme);
            }
            else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>(2);

        if (!check(Token.RightParen)) {
            do {
                args.add(expression());
            } while (match(Token.Comma));
        }

        Expr expr = new Expr.Call(callee.start, previous.end, callee, args);
        if (args.size() > 255) error("Cannot have more than 255 arguments.", expr);
        consume(Token.RightParen, "Expected ')' after function arguments.", expr);
        return expr;
    }

    private Expr primary() {
        if (match(Token.Null)) return new Expr.Null(previous.start, previous.end);
        if (match(Token.String)) return new Expr.String(previous.start, previous.end, previous.lexeme);
        if (match(Token.True, Token.False)) return new Expr.Bool(previous.start, previous.end, previous.lexeme.equals("true"));
        if (match(Token.Number)) return new Expr.Number(previous.start, previous.end, Double.parseDouble(previous.lexeme));
        if (match(Token.Identifier)) return new Expr.Variable(previous.start, previous.end, previous.lexeme);

        if (match(Token.LeftParen)) {
            int start = previous.start;

            Expr expr = statement();
            expr = new Expr.Group(start, previous.end, expr);

            consume(Token.RightParen, "Expected ')' after expression.", expr);
            return expr;
        }

        if (match(Token.LeftBrace)) {
            int start = previous.start;
            int prevExpressionDepth = expressionDepth;

            expressionDepth++;
            Expr expr;

            try {
                expr = statement();
            }
            catch (ParseException e) {
                if (e.error.expr == null) e.error.expr = new Expr.Block(start, previous.end, null);
                throw e;
            }

            if (prevExpressionDepth == 0) {
                expr = new Expr.Block(start, previous.end, expr);
            }

            consume(Token.RightBrace, "Expected '}' after expression.", expr);
            expressionDepth--;
            return expr;
        }

        error("Expected expression.", null);
        return null;
    }

    // Helpers

    private void synchronize() {
        while (!isAtEnd()) {
            if (match(Token.LeftBrace)) expressionDepth++;
            else if (match(Token.RightBrace)) {
                expressionDepth--;
                if (expressionDepth == 0) return;
            }
            else advance();
        }
    }

    private void error(String message, Expr expr) {
        throw new ParseException(new Error(current.line, current.character, current.ch, message, expr));
    }

    private TokenData consume(Token token, String message, Expr expr) {
        if (check(token)) return advance();
        error(message, expr);
        return null;
    }

    private boolean match(Token... tokens) {
        for (Token token : tokens) {
            if (check(token)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(Token token) {
        if (isAtEnd()) return false;
        return current.token == token;
    }

    private TokenData advance() {
        previous.set(current);

        lexer.next();
        current.set(lexer.token, lexer.lexeme, lexer.start, lexer.current, lexer.line, lexer.character, lexer.ch);

        return previous;
    }

    private boolean isAtEnd() {
        return current.token == Token.EOF;
    }

    // Token data

    private static class TokenData {
        public Token token;
        public String lexeme;
        public int start, end, line, character;
        public char ch;

        public void set(Token token, String lexeme, int start, int end, int line, int character, char ch) {
            this.token = token;
            this.lexeme = lexeme;
            this.start = start;
            this.end = end;
            this.line = line;
            this.character = character;
            this.ch = ch;
        }

        public void set(TokenData data) {
            set(data.token, data.lexeme, data.start, data.end, data.line, data.character, data.ch);
        }

        @Override
        public String toString() {
            return String.format("%s '%s'", token, lexeme);
        }
    }

    // Parse Exception

    private static class ParseException extends RuntimeException {
        public final Error error;

        public ParseException(Error error) {
            this.error = error;
        }
    }

    // Result

    /** A class that holds the parsed AST (abstract syntax tree) and any errors that could have been found. */
    public static class Result {
        public final List<Expr> exprs = new ArrayList<>();
        public final List<Error> errors = new ArrayList<>();

        /** Helper method that returns true if there was 1 or more errors. */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public void accept(Expr.Visitor visitor) {
            for (Expr expr : exprs) expr.accept(visitor);
        }
    }
}
