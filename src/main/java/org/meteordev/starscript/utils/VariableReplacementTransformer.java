package org.meteordev.starscript.utils;

import org.meteordev.starscript.compiler.Expr;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Replaces usages of variables or get expressions with a different expression. <strong>Doesn't support functions.</strong><br><br>
 *
 * <strong>Examples:</strong><br>
 * addReplacer("player", () -> "foo");
 * addReplacer("player.name", () -> "foo.bar");
 */
public class VariableReplacementTransformer extends AbstractExprVisitor {
    private final Map<String, Supplier<String>> replacers = new HashMap<>();

    public void addReplacer(String name, Supplier<String> supplier) {
        replacers.put(name, supplier);
    }

    @Override
    public void visitVariable(Expr.Variable expr) {
        tryReplace(expr, expr.name);
    }

    @Override
    public void visitGet(Expr.Get expr) {
        String name = getFullName(expr);
        if (name != null) tryReplace(expr, name);
    }

    private void tryReplace(Expr expr, String name) {
        Supplier<String> replacer = replacers.get(name);
        if (replacer == null) return;

        Expr replacement = createReplacement(replacer.get());
        expr.replace(replacement);
    }

    private Expr createReplacement(String replacement) {
        String[] parts = replacement.split("\\.");
        if (parts.length == 0) throw new IllegalStateException("Cannot replace with an empty replacement");

        Expr expr = new Expr.Variable(0, 0, parts[0]);
        for (int i = 1; i < parts.length; i++) expr = new Expr.Get(0, 0, expr, parts[i]);

        return expr;
    }

    /** Returns the dotted name of a chain of gets on a variable, or null if the chain starts with anything else. */
    private String getFullName(Expr.Get expr) {
        return switch (expr.getObject()) {
            case Expr.Variable variable -> variable.name + "." + expr.name;
            case Expr.Get get -> {
                String parent = getFullName(get);
                yield parent == null ? null : parent + "." + expr.name;
            }
            default -> null;
        };
    }
}
