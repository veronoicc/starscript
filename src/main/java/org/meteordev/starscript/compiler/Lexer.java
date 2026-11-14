package org.meteordev.starscript.compiler;

/** Takes starscript source code and produces a stream or tokens that are used for parsing. */
public class Lexer {
    /** The type of the token. */
    public Token token;
    /** The string representation of the token. */
    public String lexeme;

    /** 1-based line of the last consumed character. */
    public int line = 1;
    /** 0-based column, within its line, of the last consumed character. */
    public int character = -1;
    /** The last consumed character. */
    public char ch;

    private final String source;
    private final StringBuilder stringBuilder = new StringBuilder();
    public int start, current;
    private int expressionDepth;
    private boolean afterNewline;

    public Lexer(String source) {
        this.source = source;
    }

    /** Scans for next token storing it in {@link Lexer#token} and {@link Lexer#lexeme}. Produces {@link Token#EOF} if the end of source code has been reached and {@link Token#Error} if there has been an error. */
    public void next() {
        start = current;

        if (isAtEnd()) {
            createToken(Token.EOF);
            return;
        }

        if (expressionDepth > 0) {
            // Scan expression
            skipWhitespace();
            if (isAtEnd()) {
                createToken(Token.EOF);
                return;
            }

            char c = advance();

            if (isDigit(c)) number();
            else if (isAlpha(c)) identifier();
            else {
                switch (c) {
                    case '\'', '"' -> string(c);

                    case '=' -> {
                        if (match('=')) createToken(Token.EqualEqual);
                        else unexpected();
                    }
                    case '!' -> createToken(match('=') ? Token.BangEqual : Token.Bang);
                    case '>' -> {
                        if (match('=')) createToken(Token.GreaterEqual);
                        else if (match('>')) createToken(match('>') ? Token.TripleGreater : Token.DoubleGreater);
                        else createToken(Token.Greater);
                    }
                    case '<' -> {
                        if (match('=')) createToken(Token.LessEqual);
                        else if (match('<')) createToken(Token.DoubleLess);
                        else createToken(Token.Less);
                    }

                    case '+' -> createToken(Token.Plus);
                    case '-' -> createToken(Token.Minus);
                    case '*' -> createToken(Token.Star);
                    case '/' -> createToken(Token.Slash);
                    case '%' -> createToken(Token.Percentage);
                    case '^' -> createToken(match('^') ? Token.DoubleUpArrow : Token.UpArrow);

                    case '.' -> createToken(Token.Dot);
                    case ',' -> createToken(Token.Comma);
                    case '?' -> createToken(Token.QuestionMark);
                    case ':' -> createToken(Token.Colon);
                    case '(' -> createToken(Token.LeftParen);
                    case ')' -> createToken(Token.RightParen);
                    case '{' -> {
                        expressionDepth++;
                        createToken(Token.LeftBrace);
                    }
                    case '}' -> {
                        expressionDepth--;
                        createToken(Token.RightBrace);
                    }

                    case '#' -> {
                        while (isDigit(peek())) advance();
                        createToken(Token.Section, source.substring(start + 1, current));
                    }

                    case '&' -> createToken(Token.Ampersand);
                    case '|' -> createToken(Token.VBar);
                    case '~' -> createToken(Token.Tilde);

                    default -> unexpected();
                }
            }
        }
        else {
            // Scan string, start an expression or section
            char c = advance();

            if (canStartExpression(c, peek())) {
                expressionDepth++;
                createToken(Token.LeftBrace);
            }
            else if (canStartSection(c, peek())) {
                while (isDigit(peek())) advance();
                createToken(Token.Section, source.substring(start + 1, current));
            }
            else text(c);
        }
    }

    /** Scans plain text, collapsing the double-character escapes for the brace and the hash sign. */
    private void text(char first) {
        stringBuilder.setLength(0);

        char c = first;
        while (true) {
            stringBuilder.append(c);
            if ((c == '{' && peek() == '{') || (c == '#' && peek() == '#')) advance();

            if (isAtEnd() || canStartExpression(peek(), peekNext()) || canStartSection(peek(), peekNext())) break;
            c = advance();
        }

        createToken(Token.String, stringBuilder.toString());
    }

    private void string(char delimiter) {
        stringBuilder.setLength(0);

        while (!isAtEnd()) {
            char c = advance();

            if (c == delimiter) {
                createToken(Token.String, stringBuilder.toString());
                return;
            }

            if (c == '\\') {
                if (isAtEnd()) break;
                c = advance();
            }

            stringBuilder.append(c);
        }

        createToken(Token.Error, "Unterminated expression.");
    }

    private void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            advance();

            while (isDigit(peek())) advance();
        }

        createToken(Token.Number);
    }

    private void identifier() {
        while (!isAtEnd() && isAlphaNumeric(peek())) advance();

        createToken(Token.Identifier);

        switch (lexeme) {
            case "null" -> token = Token.Null;
            case "true" -> token = Token.True;
            case "false" -> token = Token.False;
            case "and" -> token = Token.And;
            case "or" -> token = Token.Or;
        }
    }

    private boolean canStartExpression(char c1, char c2) {
        return c1 == '{' && c2 != '{';
    }

    private boolean canStartSection(char c1, char c2) {
        return c1 == '#' && isDigit(c2);
    }

    private void skipWhitespace() {
        while (!isAtEnd()) {
            switch (peek()) {
                case ' ', '\r', '\t', '\n' -> advance();
                default -> {
                    start = current;
                    return;
                }
            }
        }
    }

    // Helpers

    public boolean isInExpression() {
        return expressionDepth > 0;
    }

    private void unexpected() {
        createToken(Token.Error, "Unexpected character.");
    }

    private void createToken(Token token, String lexeme) {
        this.token = token;
        this.lexeme = lexeme;
    }

    private void createToken(Token token) {
        createToken(token, source.substring(start, current));
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;

        advance();
        return true;
    }

    private char advance() {
        if (afterNewline) {
            line++;
            character = -1;
        }

        character++;
        ch = source.charAt(current++);
        afterNewline = ch == '\n';
        return ch;
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
