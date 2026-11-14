package org.meteordev.starscript;

import org.meteordev.starscript.compiler.Expr;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.utils.Error;
import org.meteordev.starscript.utils.*;
import org.meteordev.starscript.value.Value;
import org.meteordev.starscript.value.ValueMap;

import lombok.Getter;

import java.util.function.Supplier;

/** A VM (virtual machine) that can run compiled starscript code, {@link Script}. */
public class Starscript {
    @Getter
    private final ValueMap globals;

    private final Stack<Value> stack = new Stack<>();

    public Starscript() {
        globals = new ValueMap();
    }

    /** Creates a new Starscript instance that shares the globals ({@code getGlobals()}) of the parent instance. */
    public Starscript(Starscript parent) {
        globals = parent.globals;
    }

    /** Runs the script and fills the provided {@link StringBuilder}. Throws {@link StarscriptError} if a runtime error happens. */
    public Section run(Script script, StringBuilder sb) {
        int base = stack.size();

        try {
            return execute(script, sb);
        }
        finally {
            stack.truncate(base);
        }
    }

    private Section execute(Script script, StringBuilder sb) {
        byte[] code = script.code;

        sb.setLength(0);
        int ip = 0;

        Section firstSection = null;
        Section section = null;
        int index = 0;

        loop:
        while (true) {
            Instruction insn = Instruction.valueOf(code[ip++]);

            switch (insn) {
                case Constant -> { push(script.constants.get(readU16(code, ip))); ip += 2; }
                case Null -> push(Value.null_());
                case True -> push(Value.bool(true));
                case False -> push(Value.bool(false));

                case Add -> { Value b = pop(); Value a = pop(); push(add(a, b)); }
                case Subtract -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number(a.getNumber() - b.getNumber())); else error("Can only subtract 2 numbers."); }
                case Multiply -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number(a.getNumber() * b.getNumber())); else error("Can only multiply 2 numbers."); }
                case Divide -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number(a.getNumber() / b.getNumber())); else error("Can only divide 2 numbers."); }
                case Modulo -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number(a.getNumber() % b.getNumber())); else error("Can only modulo 2 numbers."); }
                case Power -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number(Math.pow(a.getNumber(), b.getNumber()))); else error("Can only power 2 numbers."); }

                case BitwiseAnd -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() & (long) b.getNumber())); else error("This operation requires 2 numbers."); }
                case BitwiseOr -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() | (long) b.getNumber())); else error("This operation requires 2 numbers."); }
                case BitwiseXor -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() ^ (long) b.getNumber())); else error("This operation requires 2 numbers."); }
                case LeftShift -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() << (long) b.getNumber())); else error("This operation requires 2 numbers."); }
                case RightShift -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() >> (long) b.getNumber())); else error("This operation requires 2 numbers."); }
                case UnsignedRightShift -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.number((long) a.getNumber() >>> (long) b.getNumber())); else error("This operation requires 2 numbers."); }

                case AddConstant -> { Value b = script.constants.get(readU16(code, ip)); ip += 2; Value a = pop(); push(add(a, b)); }

                case Pop -> pop();
                case Not -> push(Value.bool(!pop().isTruthy()));
                case Negate -> { Value a = pop(); if (a.isNumber()) push(Value.number(-a.getNumber())); else error("This operation requires a number."); }
                case BitwiseNot -> { Value a = pop(); if (a.isNumber()) push(Value.number(~((long) a.getNumber()))); else error("This operation requires a number."); }

                case Equals -> push(Value.bool(pop().equals(pop())));
                case NotEquals -> push(Value.bool(!pop().equals(pop())));
                case Greater -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.bool(a.getNumber() > b.getNumber())); else error("This operation requires 2 numbers."); }
                case GreaterEqual -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.bool(a.getNumber() >= b.getNumber())); else error("This operation requires 2 numbers."); }
                case Less -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.bool(a.getNumber() < b.getNumber())); else error("This operation requires 2 numbers."); }
                case LessEqual -> { Value b = pop(); Value a = pop(); if (a.isNumber() && b.isNumber()) push(Value.bool(a.getNumber() <= b.getNumber())); else error("This operation requires 2 numbers."); }

                case Variable -> { push(variable(script, readU16(code, ip))); ip += 2; }
                case Get -> { push(field(pop(), script, readU16(code, ip))); ip += 2; }
                case Call -> { call(code[ip++] & 0xFF, sb, false); }

                case Jump -> ip += 2 + readU16(code, ip);
                case JumpIfTrue -> ip += peek().isTruthy() ? 2 + readU16(code, ip) : 2;
                case JumpIfFalse -> ip += !peek().isTruthy() ? 2 + readU16(code, ip) : 2;

                case Section -> {
                    Section next = new Section(index, sb.toString());
                    if (firstSection == null) firstSection = next;
                    else section.next = next;
                    section = next;

                    sb.setLength(0);
                    index = code[ip++] & 0xFF;
                }

                case Append -> sb.append(pop().toString());
                case ConstantAppend -> { sb.append(script.constants.get(readU16(code, ip)).toString()); ip += 2; }
                case VariableAppend -> { sb.append(variable(script, readU16(code, ip)).toString()); ip += 2; }
                case GetAppend -> { sb.append(field(pop(), script, readU16(code, ip)).toString()); ip += 2; }
                case CallAppend -> call(code[ip++] & 0xFF, sb, true);

                case VariableGet -> {
                    push(field(variable(script, readU16(code, ip)), script, readU16(code, ip + 2)));
                    ip += 4;
                }
                case VariableGetAppend -> {
                    sb.append(field(variable(script, readU16(code, ip)), script, readU16(code, ip + 2)).toString());
                    ip += 4;
                }

                case End -> { break loop; }
                default -> throw new UnsupportedOperationException("Unknown instruction '" + insn + "'");
            }
        }

        if (section != null) {
            section.next = new Section(index, sb.toString());
            return firstSection;
        }

        return new Section(index, sb.toString());
    }

    private static int readU16(byte[] code, int ip) {
        return ((code[ip] & 0xFF) << 8) | (code[ip + 1] & 0xFF);
    }

    private Value add(Value a, Value b) {
        if (a.isNumber() && b.isNumber()) return Value.number(a.getNumber() + b.getNumber());
        if (a.isString()) return Value.string(a.getString() + b);

        error("Can only add 2 numbers or 1 string and other value.");
        return null;
    }

    private Value variable(Script script, int constant) {
        Supplier<Value> s = globals.getRaw(script.constants.get(constant).getString());
        return s != null ? s.get() : Value.null_();
    }

    private Value field(Value v, Script script, int constant) {
        if (!v.isMap()) return Value.null_();

        Supplier<Value> s = v.getMap().getRaw(script.constants.get(constant).getString());
        return s != null ? s.get() : Value.null_();
    }

    /** Calls the function below {@code argCount} arguments, then drops the arguments and callee and pushes or appends the result. */
    private void call(int argCount, StringBuilder sb, boolean append) {
        Value callee = peek(argCount);
        if (!callee.isFunction()) error("Tried to call a %s, can only call functions.", callee.type());

        int base = stack.size() - argCount - 1;
        Value result = callee.getFunction().run(this, argCount);

        if (stack.size() <= base) error("Function popped too many values.");
        stack.truncate(base);

        if (append) sb.append(result.toString());
        else push(result);
    }

    /** Runs the script. Throws {@link StarscriptError} if a runtime error happens. */
    public Section run(Script script) {
        return run(script, new StringBuilder());
    }

    // Stack manipulation

    /** Pushes a new value on the stack. */
    public void push(Value value) {
        stack.push(value);
    }

    /** Pops a new value from the stack. */
    public Value pop() {
        return stack.pop();
    }

    /** Returns a value from the stack without removing it. */
    public Value peek() {
        if (stack.size() == 0) return Value.null_();
        return stack.peek();
    }

    /** Returns a value from the stack with an offset without removing it. */
    public Value peek(int offset) {
        if (stack.size() <= offset) return Value.null_();
        return stack.peek(offset);
    }

    /** Pops a value from the stack and returns it as boolean. Calls {@link Starscript#error(String, Object...)} with the provided message if the value is not boolean. */
    public boolean popBool(String errorMsg) {
        Value a = pop();
        if (!a.isBool()) error(errorMsg);
        return a.getBool();
    }

    /** Pops a value from the stack and returns it as double. Calls {@link Starscript#error(String, Object...)} with the provided message if the value is not double. */
    public double popNumber(String errorMsg) {
        Value a = pop();
        if (!a.isNumber()) error(errorMsg);
        return a.getNumber();
    }

    /** Pops a value from the stack and returns it as String. Calls {@link Starscript#error(String, Object...)} with the provided message if the value is not String. */
    public String popString(String errorMsg) {
        Value a = pop();
        if (!a.isString()) error(errorMsg);
        return a.getString();
    }

    /** Pops a value from the stack and returns it as Object. Calls {@link Starscript#error(String, Object...)} with the provided message if the value is not Object. */
    public Object popObject(String errorMsg) {
        Value a = pop();
        if (!a.isObject()) error(errorMsg);
        return a.getObject();
    }

    // Helpers

    /** Throws a {@link StarscriptError}. Without arguments the message is used verbatim, otherwise it is a {@link String#format} pattern. */
    public void error(String format, Object... args) {
        throw new StarscriptError(args.length == 0 ? format : String.format(format, args));
    }

    // Globals

    /** Sets a variable supplier for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, Supplier<Value> supplier) {
        return globals.set(name, supplier);
    }

    /** Sets a variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, Value value) {
        return globals.set(name, value);
    }

    /** Sets a boolean variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, boolean bool) {
        return globals.set(name, bool);
    }

    /** Sets a number variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, double number) {
        return globals.set(name, number);
    }

    /** Sets a string variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, String string) {
        return globals.set(name, string);
    }

    /** Sets a function variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, SFunction function) {
        return globals.set(name, function);
    }

    /** Sets a map variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, ValueMap map) {
        return globals.set(name, map);
    }

    /** Sets an object variable supplier that always returns the same value for the provided name. <br><br> See {@link ValueMap#set(String, Supplier)} for dot notation. */
    public ValueMap set(String name, Object object) {
        return globals.set(name, object);
    }

    /** Removes all values from the globals. */
    public void clear() {
        globals.clear();
    }

    /** Removes a single value with the specified name from the globals and returns the removed value. <br><br> See {@link ValueMap#remove(String)} for dot notation. */
    public Supplier<Value> remove(String name) {
        return globals.remove(name);
    }

    // Completions

    /** Calls the provided callback for every completion that is able to be resolved from global variables. */
    public void getCompletions(String source, int position, CompletionCallback callback) {
        Parser.Result result = Parser.parse(source);

        for (Expr expr : result.exprs) {
            completionsExpr(source, position, expr, callback);
        }

        for (Error error : result.errors) {
            if (error.expr != null) completionsExpr(source, position, error.expr, callback);
        }
    }

    private void completionsExpr(String source, int position, Expr expr, CompletionCallback callback) {
        if (position < expr.start || (position > expr.end && position != source.length())) return;

        if (expr instanceof Expr.Variable var) {
            String start = source.substring(var.start, position);

            for (String key : globals.keys()) {
                if (!key.startsWith("_") && key.startsWith(start)) callback.onCompletion(key, globals.getRaw(key).get().isFunction());
            }
        }
        else if (expr instanceof Expr.Get get) {
            if (position >= get.end - get.name.length()) {
                Value value = resolveExpr(get.getObject());

                if (value != null && value.isMap()) {
                    String start = source.substring(get.getObject().end + 1, position);

                    for (String key : value.getMap().keys()) {
                        if (!key.startsWith("_") && key.startsWith(start)) callback.onCompletion(key, value.getMap().getRaw(key).get().isFunction());
                    }
                }
            }
            else {
                for (Expr child : expr.children) completionsExpr(source, position, child, callback);
            }
        }
        else if (expr instanceof Expr.Block block && block.getExpr() == null) {
            for (String key : globals.keys()) {
                if (!key.startsWith("_")) callback.onCompletion(key, globals.getRaw(key).get().isFunction());
            }
        }
        else {
            for (Expr child : expr.children) completionsExpr(source, position, child, callback);
        }
    }

    private Value resolveExpr(Expr expr) {
        return switch (expr) {
            case Expr.Variable var -> {
                Supplier<Value> supplier = globals.getRaw(var.name);
                yield supplier != null ? supplier.get() : null;
            }
            case Expr.Get get -> {
                Value value = resolveExpr(get.getObject());
                if (value == null || !value.isMap()) yield null;

                Supplier<Value> supplier = value.getMap().getRaw(get.name);
                yield supplier != null ? supplier.get() : null;
            }
            default -> null;
        };
    }
}
