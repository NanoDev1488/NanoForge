package ru.nanodev.nanoforge.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Высокопроизводительный лексер NanoScript.
 * Пробегает по символам без лишних строковых аллокаций.
 */
public final class NanoLexer {

    private final char[] src;
    private final int len;
    private int pos = 0;

    public NanoLexer(String source) {
        if (source == null) {
            this.src = new char[0];
            this.len = 0;
        } else {
            this.src = source.toCharArray();
            this.len = src.length;
        }
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < len) {
            char c = src[pos];
            if (isWhitespace(c)) {
                pos++;
                continue;
            }

            int start = pos;

            // Комментарии в одну строку (# или //)
            if (c == '#' || (c == '/' && pos + 1 < len && src[pos + 1] == '/')) {
                skipLine();
                continue;
            }

            // Числа (и проценты вида 25%)
            if (isDigit(c) || (c == '.' && pos + 1 < len && isDigit(src[pos + 1]))) {
                tokens.add(readNumber());
                continue;
            }

            // Строки "..." или '...'
            if (c == '"' || c == '\'') {
                tokens.add(readString(c));
                continue;
            }

            // Идентификаторы и ключевые слова (true, false)
            if (isIdentStart(c)) {
                tokens.add(readIdentifier());
                continue;
            }

            // Операторы и разделители
            pos++;
            switch (c) {
                case '+': tokens.add(Token.of(TokenType.PLUS, start, 1)); break;
                case '-': tokens.add(Token.of(TokenType.MINUS, start, 1)); break;
                case '*': tokens.add(Token.of(TokenType.STAR, start, 1)); break;
                case '/': tokens.add(Token.of(TokenType.SLASH, start, 1)); break;
                case '%': tokens.add(Token.of(TokenType.PERCENT, start, 1)); break;
                case '(': tokens.add(Token.of(TokenType.LPAREN, start, 1)); break;
                case ')': tokens.add(Token.of(TokenType.RPAREN, start, 1)); break;
                case ',': tokens.add(Token.of(TokenType.COMMA, start, 1)); break;
                case '.': tokens.add(Token.of(TokenType.DOT, start, 1)); break;
                case '=':
                    if (match('=')) {
                        tokens.add(Token.of(TokenType.EQ_EQ, start, 2));
                    } else {
                        // одиночный = для равенства или присваивания
                        tokens.add(Token.of(TokenType.EQ_EQ, start, 1));
                    }
                    break;
                case '!':
                    if (match('=')) {
                        tokens.add(Token.of(TokenType.BANG_EQ, start, 2));
                    } else {
                        tokens.add(Token.of(TokenType.BANG, start, 1));
                    }
                    break;
                case '<':
                    if (match('=')) {
                        tokens.add(Token.of(TokenType.LT_EQ, start, 2));
                    } else {
                        tokens.add(Token.of(TokenType.LT, start, 1));
                    }
                    break;
                case '>':
                    if (match('=')) {
                        tokens.add(Token.of(TokenType.GT_EQ, start, 2));
                    } else {
                        tokens.add(Token.of(TokenType.GT, start, 1));
                    }
                    break;
                case '&':
                    if (match('&')) {
                        tokens.add(Token.of(TokenType.AMP_AMP, start, 2));
                    } else {
                        throw new IllegalArgumentException("Unexpected character '&' at index " + start + ". Did you mean '&&'?");
                    }
                    break;
                case '|':
                    if (match('|')) {
                        tokens.add(Token.of(TokenType.PIPE_PIPE, start, 2));
                    } else {
                        throw new IllegalArgumentException("Unexpected character '|' at index " + start + ". Did you mean '||'?");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at index " + start);
            }
        }

        tokens.add(Token.of(TokenType.EOF, pos, 0));
        return tokens;
    }

    private Token readNumber() {
        int start = pos;
        boolean hasDot = false;
        while (pos < len) {
            char c = src[pos];
            if (isDigit(c)) {
                pos++;
            } else if (c == '.' && !hasDot && pos + 1 < len && isDigit(src[pos + 1])) {
                hasDot = true;
                pos++;
            } else {
                break;
            }
        }

        double val = parseDouble(start, pos);

        // Проверка процента 25% -> 0.25
        if (pos < len && src[pos] == '%') {
            pos++;
            val = val / 100.0;
        }

        return Token.number(start, pos - start, val);
    }

    private double parseDouble(int start, int end) {
        // Парсинг без создания промежуточных объектов String для типичных чисел
        double result = 0;
        int i = start;
        while (i < end && src[i] != '.') {
            result = result * 10 + (src[i] - '0');
            i++;
        }
        if (i < end && src[i] == '.') {
            i++;
            double factor = 0.1;
            while (i < end) {
                result += (src[i] - '0') * factor;
                factor *= 0.1;
                i++;
            }
        }
        return result;
    }

    private Token readString(char quote) {
        int start = pos;
        pos++; // пропускаем открывающую кавычку
        StringBuilder sb = new StringBuilder();
        while (pos < len) {
            char c = src[pos++];
            if (c == quote) {
                return Token.string(start, pos - start, sb.toString());
            }
            if (c == '\\' && pos < len) {
                char esc = src[pos++];
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\'': sb.append('\''); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(esc); break;
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string starting at index " + start);
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < len && isIdentPart(src[pos])) {
            pos++;
        }
        int l = pos - start;
        if (l == 4 && src[start] == 't' && src[start+1] == 'r' && src[start+2] == 'u' && src[start+3] == 'e') {
            return new Token(TokenType.BOOLEAN, start, l, 1.0, "true");
        }
        if (l == 5 && src[start] == 'f' && src[start+1] == 'a' && src[start+2] == 'l' && src[start+3] == 's' && src[start+4] == 'e') {
            return new Token(TokenType.BOOLEAN, start, l, 0.0, "false");
        }
        String id = new String(src, start, l);
        return Token.identifier(start, l, id);
    }

    private boolean match(char expected) {
        if (pos < len && src[pos] == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void skipLine() {
        while (pos < len && src[pos] != '\n' && src[pos] != '\r') {
            pos++;
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }
}
