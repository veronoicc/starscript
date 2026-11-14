package org.meteordev.starscript.utils;

/** A single token containing its {@link SemanticTokenType type} and position range where it is located in the source string. */
public record SemanticToken(SemanticTokenType type, int start, int end) {}
