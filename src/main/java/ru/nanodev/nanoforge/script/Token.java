package ru.nanodev.nanoforge.script;

public final class Token {
    public final TokenType type;
    public final int start;
    public final int length;
    public final double numberVal;
    public final String stringVal;

    public Token(TokenType type, int start, int length, double numberVal, String stringVal) {
        this.type = type;
        this.start = start;
        this.length = length;
        this.numberVal = numberVal;
        this.stringVal = stringVal;
    }

    public static Token of(TokenType type, int start, int length) {
        return new Token(type, start, length, 0.0, null);
    }

    public static Token number(int start, int length, double val) {
        return new Token(TokenType.NUMBER, start, length, val, null);
    }

    public static Token string(int start, int length, String val) {
        return new Token(TokenType.STRING, start, length, 0.0, val);
    }

    public static Token identifier(int start, int length, String val) {
        return new Token(TokenType.IDENTIFIER, start, length, 0.0, val);
    }

    @Override
    public String toString() {
        return "Token{" + type + ", str='" + stringVal + "', num=" + numberVal + ", pos=" + start + "}";
    }
}
