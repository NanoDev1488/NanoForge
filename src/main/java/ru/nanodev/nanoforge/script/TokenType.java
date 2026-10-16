package ru.nanodev.nanoforge.script;

public enum TokenType {
    // Литералы
    NUMBER,
    BOOLEAN,
    STRING,
    IDENTIFIER,

    // Арифметика
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %

    // Сравнение
    EQ_EQ,          // ==
    BANG_EQ,        // !=
    LT,             // <
    LT_EQ,          // <=
    GT,             // >
    GT_EQ,          // >=

    // Логика
    AMP_AMP,        // &&
    PIPE_PIPE,      // ||
    BANG,           // !

    // Разделители
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    DOT,            // .

    EOF
}
