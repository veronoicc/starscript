package org.meteordev.starscript.value;

import org.meteordev.starscript.utils.SFunction;

import java.util.Objects;
import java.util.function.Supplier;

/** Any starscript value. */
public sealed interface Value {
    static Value null_() {
        return VNull.INSTANCE;
    }
    static Value bool(boolean bool) {
        return bool ? VBool.TRUE : VBool.FALSE;
    }
    static Value number(double number) {
        return new VNumber(number);
    }
    static Value string(String string) {
        return new VString(string);
    }
    static Value function(SFunction function) {
        return new VFunction(function);
    }
    static Value map(ValueMap fields) {
        return new VMap(fields);
    }
    /** Wraps an arbitrary object, a {@code null} object becomes {@link #null_()}. */
    static Value object(Object object) {
        return object == null ? VNull.INSTANCE : new VObject(object);
    }

    ValueType type();

    default boolean isNull() {
        return type() == ValueType.Null;
    }
    default boolean isBool() {
        return type() == ValueType.Boolean;
    }
    default boolean isNumber() {
        return type() == ValueType.Number;
    }
    default boolean isString() {
        return type() == ValueType.String;
    }
    default boolean isFunction() {
        return type() == ValueType.Function;
    }
    default boolean isMap() {
        return type() == ValueType.Map;
    }
    default boolean isObject() {
        return type() == ValueType.Object;
    }

    default boolean getBool() {
        return ((VBool) this).value;
    }
    default double getNumber() {
        return ((VNumber) this).value;
    }
    default String getString() {
        return ((VString) this).value;
    }
    default SFunction getFunction() {
        return ((VFunction) this).value;
    }
    default ValueMap getMap() {
        return ((VMap) this).value;
    }
    default Object getObject() {
        return ((VObject) this).value;
    }

    default boolean isTruthy() {
        return !(this instanceof VNull) && (!(this instanceof VBool b) || b.value);
    }

    /** Whole numbers within long range print without a decimal point. */
    private static String formatNumber(double n) {
        if (n % 1 == 0 && n >= -0x1p63 && n < 0x1p63) return Long.toString((long) n);
        return Double.toString(n);
    }

    final class VNull implements Value {
        private static final VNull INSTANCE = new VNull();

        private VNull() {}

        @Override public ValueType type() { return ValueType.Null; }
        @Override public boolean equals(Object o) { return o instanceof VNull; }
        @Override public int hashCode() { return 0; }
        @Override public String toString() { return "null"; }
    }

    record VBool(boolean value) implements Value {
        private static final VBool TRUE = new VBool(true);
        private static final VBool FALSE = new VBool(false);

        @Override public ValueType type() { return ValueType.Boolean; }
        @Override public String toString() { return value ? "true" : "false"; }
    }

    record VNumber(double value) implements Value {
        @Override public ValueType type() { return ValueType.Number; }

        @Override
        public boolean equals(Object o) {
            return o instanceof VNumber n && value == n.value;
        }

        @Override
        public int hashCode() {
            return value == 0 ? 0 : Double.hashCode(value);
        }

        @Override public String toString() { return formatNumber(value); }
    }

    record VString(String value) implements Value {
        @Override public ValueType type() { return ValueType.String; }
        @Override public String toString() { return value; }
    }

    record VFunction(SFunction value) implements Value {
        @Override public ValueType type() { return ValueType.Function; }

        @Override
        public boolean equals(Object o) {
            return o instanceof VFunction f && value == f.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

        @Override public String toString() { return "<function>"; }
    }

    record VMap(ValueMap value) implements Value {
        @Override public ValueType type() { return ValueType.Map; }

        @Override
        public boolean equals(Object o) {
            return o instanceof VMap m && value == m.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

        @Override
        public String toString() {
            Supplier<Value> s = value.getRaw("_toString");
            return s == null ? "<map>" : s.get().toString();
        }
    }

    record VObject(Object value) implements Value {
        @Override public ValueType type() { return ValueType.Object; }

        @Override
        public boolean equals(Object o) {
            return o instanceof VObject v && Objects.equals(value, v.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override public String toString() { return String.valueOf(value); }
    }
}
