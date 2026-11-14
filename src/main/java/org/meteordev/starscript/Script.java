package org.meteordev.starscript;

import org.meteordev.starscript.utils.StarscriptError;
import org.meteordev.starscript.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compiled representation of starscript code that can be run inside {@link Starscript}. */
public class Script {
    public byte[] code = new byte[8];
    private int size;

    public final List<Value> constants = new ArrayList<>();
    private final Map<Value, Integer> constantIndices = new HashMap<>();

    private void write(int b) {
        if (size >= code.length) {
            byte[] newCode = new byte[code.length * 2];
            System.arraycopy(code, 0, newCode, 0, code.length);
            code = newCode;
        }

        code[size++] = (byte) b;
    }

    /** Writes instruction to this script. */
    public void write(Instruction insn) {
        write(insn.ordinal());
    }

    /** Writes instruction with an additional byte to this script. */
    public void write(Instruction insn, int b) {
        write(insn.ordinal());
        write(b);
    }

    /** Writes instruction with an additional constant value to this script. */
    public void write(Instruction insn, Value constant) {
        write(insn.ordinal());
        writeConstant(constant);
    }

    /** Writes constant value to this script as an unsigned 16-bit index. Throws {@link StarscriptError} past 65536 distinct constants. */
    public void writeConstant(Value constant) {
        Integer existing = constantIndices.get(constant);
        int index;

        if (existing != null) index = existing;
        else {
            index = constants.size();
            if (index > 0xFFFF) throw new StarscriptError("Script has too many constants, the limit is 65536.");

            constants.add(constant);
            constantIndices.put(constant, index);
        }

        write(index >> 8);
        write(index);
    }

    /** Begins a jump instruction. */
    public int writeJump(Instruction insn) {
        write(insn);
        write(0);
        write(0);

        return size - 2;
    }

    /** Ends a jump instruction. Throws {@link StarscriptError} if the jump is longer than 65535 bytes. */
    public void patchJump(int offset) {
        int jump = size - offset - 2;
        if (jump > 0xFFFF) throw new StarscriptError("Jump is too long, script is too large.");

        code[offset] = (byte) (jump >> 8);
        code[offset + 1] = (byte) jump;
    }

    /** Returns the number of bytes inside {@link #code}. */
    public int getSize() {
        return size;
    }

    // Decompilation

    /** Decompiles this script and writes it to the {@link Appendable} argument. */
    public void decompile(Appendable out) {
        try {
            for (int i = 0; i < size; i++) {
                Instruction insn = Instruction.valueOf(code[i]);
                out.append(String.format("%3d %-18s", i, insn));

                switch (insn) {
                    case AddConstant, Variable, VariableAppend, Get, GetAppend, Constant, ConstantAppend -> {
                        int c = readU16(i + 1);
                        i += 2;
                        out.append(String.format("%3d '%s'", c, constants.get(c)));
                    }
                    case Call, CallAppend -> {
                        int argCount = code[++i] & 0xFF;
                        out.append(String.format("%3d %s", argCount, argCount == 1 ? "argument" : "arguments"));
                    }
                    case Jump, JumpIfTrue, JumpIfFalse -> {
                        int jump = readU16(i + 1);
                        i += 2;
                        out.append(String.format("%3d -> %d", jump, i + 1 + jump));
                    }
                    case Section -> out.append(String.format("%3d", code[++i] & 0xFF));
                    case VariableGet, VariableGetAppend -> {
                        int a = readU16(i + 1);
                        int b = readU16(i + 3);
                        i += 4;
                        out.append(String.format("%3d.%-3d '%s.%s'", a, b, constants.get(a), constants.get(b)));
                    }
                    default -> { }
                }

                out.append('\n');
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int readU16(int i) {
        return ((code[i] & 0xFF) << 8) | (code[i + 1] & 0xFF);
    }

    /** Decompiles this script and writes it to {@link System#out}. */
    public void decompile() {
        decompile(System.out);
    }
}
