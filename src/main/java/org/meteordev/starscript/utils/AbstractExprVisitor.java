package org.meteordev.starscript.utils;

import org.meteordev.starscript.compiler.Expr;

/** Visitor that walks the whole tree; override the methods you care about and call {@code super} to keep descending. */
public abstract class AbstractExprVisitor implements Expr.Visitor {
    private void visitChildren(Expr expr) {
        for (Expr child : expr.children) child.accept(this);
    }

    @Override
    public void visitNull(Expr.Null expr) {
        visitChildren(expr);
    }

    @Override
    public void visitString(Expr.String expr) {
        visitChildren(expr);
    }

    @Override
    public void visitNumber(Expr.Number expr) {
        visitChildren(expr);
    }

    @Override
    public void visitBool(Expr.Bool expr) {
        visitChildren(expr);
    }

    @Override
    public void visitBlock(Expr.Block expr) {
        visitChildren(expr);
    }

    @Override
    public void visitGroup(Expr.Group expr) {
        visitChildren(expr);
    }

    @Override
    public void visitBinary(Expr.Binary expr) {
        visitChildren(expr);
    }

    @Override
    public void visitUnary(Expr.Unary expr) {
        visitChildren(expr);
    }

    @Override
    public void visitVariable(Expr.Variable expr) {
        visitChildren(expr);
    }

    @Override
    public void visitGet(Expr.Get expr) {
        visitChildren(expr);
    }

    @Override
    public void visitCall(Expr.Call expr) {
        visitChildren(expr);
    }

    @Override
    public void visitLogical(Expr.Logical expr) {
        visitChildren(expr);
    }

    @Override
    public void visitConditional(Expr.Conditional expr) {
        visitChildren(expr);
    }

    @Override
    public void visitSection(Expr.Section expr) {
        visitChildren(expr);
    }
}
