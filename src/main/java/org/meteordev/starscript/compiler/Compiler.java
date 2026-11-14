package org.meteordev.starscript.compiler;

import org.meteordev.starscript.Instruction;
import org.meteordev.starscript.Script;
import org.meteordev.starscript.Starscript;
import org.meteordev.starscript.value.Value;

/** Compiler that produces compiled starscript code from {@link Parser.Result}. */
public class Compiler implements Expr.Visitor {
    private final Script script = new Script();

    private int blockDepth;

    /** The direct child expression of the block being compiled, which may use a fused {@code *Append} instruction. */
    private Expr appendTarget;

    private Compiler() {}

    /** Produces compiled {@link Script} from {@link Parser.Result} that can be run inside {@link Starscript}. */
    public static Script compile(Parser.Result result) {
        Compiler compiler = new Compiler();

        for (Expr expr : result.exprs) compiler.compile(expr);
        compiler.script.write(Instruction.End);

        return compiler.script;
    }

    // Expressions

    @Override
    public void visitNull(Expr.Null expr) {
        script.write(Instruction.Null);
    }

    @Override
    public void visitString(Expr.String expr) {
        script.write((blockDepth == 0 || expr == appendTarget) ? Instruction.ConstantAppend : Instruction.Constant, Value.string(expr.string));
    }

    @Override
    public void visitNumber(Expr.Number expr) {
        script.write(Instruction.Constant, Value.number(expr.number));
    }

    @Override
    public void visitBool(Expr.Bool expr) {
        script.write(expr.bool ? Instruction.True : Instruction.False);
    }

    @Override
    public void visitBlock(Expr.Block expr) {
        Expr child = expr.getExpr();
        boolean fused = child instanceof Expr.String || child instanceof Expr.Variable || child instanceof Expr.Get || child instanceof Expr.Call;

        Expr prevAppendTarget = appendTarget;
        blockDepth++;
        appendTarget = fused ? child : null;

        compile(child);
        if (!fused) script.write(Instruction.Append);

        blockDepth--;
        appendTarget = prevAppendTarget;
    }

    @Override
    public void visitGroup(Expr.Group expr) {
        compile(expr.getExpr());
    }

    @Override
    public void visitBinary(Expr.Binary expr) {
        compile(expr.getLeft());

        if (expr.op == Token.Plus) {
            Value constant = switch (expr.getRight()) {
                case Expr.String s -> Value.string(s.string);
                case Expr.Number n -> Value.number(n.number);
                default -> null;
            };

            if (constant != null) {
                script.write(Instruction.AddConstant, constant);
                return;
            }
        }

        compile(expr.getRight());

        script.write(switch (expr.op) {
            case Plus -> Instruction.Add;
            case Minus -> Instruction.Subtract;
            case Star -> Instruction.Multiply;
            case Slash -> Instruction.Divide;
            case Percentage -> Instruction.Modulo;
            case UpArrow -> Instruction.Power;

            case EqualEqual -> Instruction.Equals;
            case BangEqual -> Instruction.NotEquals;
            case Greater -> Instruction.Greater;
            case GreaterEqual -> Instruction.GreaterEqual;
            case Less -> Instruction.Less;
            case LessEqual -> Instruction.LessEqual;

            case Ampersand -> Instruction.BitwiseAnd;
            case VBar -> Instruction.BitwiseOr;
            case DoubleUpArrow -> Instruction.BitwiseXor;
            case DoubleLess -> Instruction.LeftShift;
            case DoubleGreater -> Instruction.RightShift;
            case TripleGreater -> Instruction.UnsignedRightShift;

            default -> throw new IllegalStateException("Unexpected binary operator " + expr.op);
        });
    }

    @Override
    public void visitUnary(Expr.Unary expr) {
        compile(expr.getRight());

        script.write(switch (expr.op) {
            case Bang -> Instruction.Not;
            case Minus -> Instruction.Negate;
            case Tilde -> Instruction.BitwiseNot;
            default -> throw new IllegalStateException("Unexpected unary operator " + expr.op);
        });
    }

    @Override
    public void visitVariable(Expr.Variable expr) {
        script.write(expr == appendTarget ? Instruction.VariableAppend : Instruction.Variable, Value.string(expr.name));
    }

    @Override
    public void visitGet(Expr.Get expr) {
        boolean append = expr == appendTarget;

        if (expr.getObject() instanceof Expr.Variable variable) {
            script.write(append ? Instruction.VariableGetAppend : Instruction.VariableGet, Value.string(variable.name));
            script.writeConstant(Value.string(expr.name));
        }
        else {
            compile(expr.getObject());
            script.write(append ? Instruction.GetAppend : Instruction.Get, Value.string(expr.name));
        }
    }

    @Override
    public void visitCall(Expr.Call expr) {
        compile(expr.getCallee());
        for (int i = 0; i < expr.getArgCount(); i++) compile(expr.getArg(i));

        script.write(expr == appendTarget ? Instruction.CallAppend : Instruction.Call, expr.getArgCount());
    }

    @Override
    public void visitLogical(Expr.Logical expr) {
        compile(expr.getLeft());
        int endJump = script.writeJump(expr.op == Token.And ? Instruction.JumpIfFalse : Instruction.JumpIfTrue);

        script.write(Instruction.Pop);
        compile(expr.getRight());

        script.patchJump(endJump);
    }

    @Override
    public void visitConditional(Expr.Conditional expr) {
        compile(expr.getCondition());
        int falseJump = script.writeJump(Instruction.JumpIfFalse);

        script.write(Instruction.Pop);
        compile(expr.getTrueExpr());
        int endJump = script.writeJump(Instruction.Jump);

        script.patchJump(falseJump);
        script.write(Instruction.Pop);
        compile(expr.getFalseExpr());

        script.patchJump(endJump);
    }

    @Override
    public void visitSection(Expr.Section expr) {
        script.write(Instruction.Section, expr.index);
        compile(expr.getExpr());
    }

    // Helpers

    private void compile(Expr expr) {
        if (expr != null) expr.accept(this);
    }
}
