package org.meteordev.starscript.utils;

import lombok.RequiredArgsConstructor;
import org.meteordev.starscript.compiler.Expr;
import org.meteordev.starscript.compiler.Lexer;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.compiler.Token;

import java.util.Comparator;
import java.util.List;

/** Provides a list of {@link SemanticToken}s for a given Starscript source input. The main use case for semantic tokens is syntax highlighting. */
public class SemanticTokenProvider {
    /** See {@link SemanticTokenProvider}. The tokens are added to the list (which is automatically cleared) passed to this function. */
    public static void get(String source, List<SemanticToken> tokens) {
        tokens.clear();

        // Lexer
        Lexer lexer = new Lexer(source);

        lexer.next();
        while (lexer.token != Token.EOF) {
            SemanticTokenType type = switch (lexer.token) {
                case Dot -> SemanticTokenType.Dot;
                case Comma -> SemanticTokenType.Comma;
                case EqualEqual, BangEqual, Greater, GreaterEqual, Less, LessEqual, Plus, Minus, Star, Slash, Percentage,
                     UpArrow, Bang, QuestionMark, Colon, Ampersand, VBar, DoubleUpArrow, DoubleLess, DoubleGreater,
                     TripleGreater, Tilde -> SemanticTokenType.Operator;
                case String -> lexer.isInExpression() ? SemanticTokenType.String : null;
                case Number -> SemanticTokenType.Number;
                case Null, True, False, And, Or -> SemanticTokenType.Keyword;
                case LeftParen, RightParen -> SemanticTokenType.Paren;
                case LeftBrace, RightBrace -> SemanticTokenType.Brace;
                case Section -> SemanticTokenType.Section;
                default -> null;
            };

            if (type != null) tokens.add(new SemanticToken(type, lexer.start, lexer.current));

            lexer.next();
        }

        // Parser
        Parser.Result result = Parser.parse(source);

        if (result.hasErrors()) {
            int errorOffset = offsetOf(source, result.errors.getFirst());

            // Remove tokens at the same position or after the error
            tokens.removeIf(token -> token.end() > errorOffset);

            // Add the error token starting at the error position going to the end of the source
            tokens.add(new SemanticToken(SemanticTokenType.Error, errorOffset, source.length()));
        }
        else {
            result.accept(new Visitor(tokens));
        }

        // Sort tokens
        tokens.sort(Comparator.comparingInt(SemanticToken::start));
    }

    /** Converts the line and column of an error to an offset into the source. */
    private static int offsetOf(String source, Error error) {
        int lineStart = 0;

        for (int line = 1; line < error.line; line++) {
            int newline = source.indexOf('\n', lineStart);
            if (newline < 0) break;
            lineStart = newline + 1;
        }

        return Math.min(lineStart + error.character, source.length());
    }

    @RequiredArgsConstructor
    private static class Visitor extends AbstractExprVisitor {
        private final List<SemanticToken> tokens;

        @Override
        public void visitVariable(Expr.Variable expr) {
            if (!(expr.parent instanceof Expr.Get)) {
                tokens.add(new SemanticToken(SemanticTokenType.Identifier, expr.end - expr.name.length(), expr.end));
            }

            super.visitVariable(expr);
        }

        @Override
        public void visitGet(Expr.Get expr) {
            if (expr.getObject() instanceof Expr.Variable varExpr) {
                tokens.add(new SemanticToken(SemanticTokenType.Map, varExpr.start, varExpr.end));
            }
            else if (expr.getObject() instanceof Expr.Get getExpr) {
                tokens.add(new SemanticToken(SemanticTokenType.Map, getExpr.end - getExpr.name.length(), getExpr.end));
            }

            if (!(expr.parent instanceof Expr.Get)) {
                tokens.add(new SemanticToken(SemanticTokenType.Identifier, expr.end - expr.name.length(), expr.end));
            }

            super.visitGet(expr);
        }
    }
}
