package org.meteordev.starscript.utils;

import lombok.AllArgsConstructor;
import org.meteordev.starscript.compiler.Expr;

/** Class for storing errors produced while parsing. */
@AllArgsConstructor
public class Error {
    /** 1-based line. */
    public final int line;
    /** 0-based column within the line. */
    public final int character;
    public final char ch;
    public final String message;
    public Expr expr;

    @Override
    public String toString() {
        return String.format("[line %d, character %d] at '%s': %s", line, character, ch, message);
    }
}
