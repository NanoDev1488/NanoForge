package ru.nanodev.nanoforge.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Pratt Parser (Top-Down Operator Precedence) для NanoScript с Constant Folding.
 * За один проход строит оптимизированное выражение и компилирует в NanoProgram.
 */
public final class NanoParser {

    private final List<Token> tokens;
    private int current = 0;

    public NanoParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static NanoProgram parseAndCompile(String source) {
        NanoLexer lexer = new NanoLexer(source);
        List<Token> tokens = lexer.tokenize();
        NanoParser parser = new NanoParser(tokens);
        ExprNode expr = parser.parseExpression();
        BytecodeEmitter emitter = new BytecodeEmitter();
        expr.emit(emitter);
        return emitter.build(source);
    }

    public ExprNode parseExpression() {
        return parsePrecedence(Precedence.LOWEST);
    }

    private ExprNode parsePrecedence(Precedence precedence) {
        Token token = advance();
        ExprNode left = parsePrefix(token);

        while (precedence.ordinal() < getPrecedence(peek().type).ordinal()) {
            Token next = advance();
            left = parseInfix(left, next);
        }

        return left;
    }

    private ExprNode parsePrefix(Token token) {
        switch (token.type) {
            case NUMBER:
                return new NumNode(token.numberVal);
            case BOOLEAN:
                return new BoolNode(token.numberVal != 0.0);
            case STRING:
                return new StrNode(token.stringVal);
            case IDENTIFIER: {
                String id = token.stringVal;
                // Проверяем вызов функции верхнего уровня: id(...)
                if (match(TokenType.LPAREN)) {
                    List<ExprNode> args = parseArgList();
                    return new CallNode(null, id, args);
                }
                return new VarNode(id);
            }
            case LPAREN: {
                ExprNode expr = parseExpression();
                consume(TokenType.RPAREN, "Expected ')' after expression");
                return expr;
            }
            case MINUS: {
                ExprNode right = parsePrecedence(Precedence.UNARY);
                if (right.isConstNum()) {
                    return new NumNode(-right.getConstNum());
                }
                return new UnaryNode(TokenType.MINUS, right);
            }
            case BANG: {
                ExprNode right = parsePrecedence(Precedence.UNARY);
                if (right.isConstBool()) {
                    return new BoolNode(!right.getConstBool());
                }
                return new UnaryNode(TokenType.BANG, right);
            }
            default:
                throw new IllegalArgumentException("Unexpected token at prefix: " + token);
        }
    }

    private ExprNode parseInfix(ExprNode left, Token opToken) {
        switch (opToken.type) {
            case DOT: {
                Token propToken = consume(TokenType.IDENTIFIER, "Expected property or method name after '.'");
                String name = propToken.stringVal;
                // Проверяем вызов метода target.name(...)
                if (match(TokenType.LPAREN)) {
                    List<ExprNode> args = parseArgList();
                    return new CallNode(left, name, args);
                }
                return new PropertyNode(left, name);
            }
            case LPAREN: {
                // Вызов метода объекта: target(...)
                List<ExprNode> args = parseArgList();
                return new CallNode(left, "", args);
            }
            case PLUS:
            case MINUS:
            case STAR:
            case SLASH:
            case PERCENT:
            case EQ_EQ:
            case BANG_EQ:
            case LT:
            case LT_EQ:
            case GT:
            case GT_EQ:
            case AMP_AMP:
            case PIPE_PIPE: {
                Precedence prec = getPrecedence(opToken.type);
                ExprNode right = parsePrecedence(prec);
                return foldBinary(left, opToken.type, right);
            }
            default:
                throw new IllegalArgumentException("Unexpected token at infix: " + opToken);
        }
    }

    private ExprNode foldBinary(ExprNode left, TokenType op, ExprNode right) {
        // Constant Folding для чисел
        if (left.isConstNum() && right.isConstNum()) {
            double a = left.getConstNum();
            double b = right.getConstNum();
            switch (op) {
                case PLUS: return new NumNode(a + b);
                case MINUS: return new NumNode(a - b);
                case STAR: return new NumNode(a * b);
                case SLASH: return new NumNode(b == 0.0 ? 0.0 : a / b);
                case PERCENT: return new NumNode(b == 0.0 ? 0.0 : a % b);
                case LT: return new BoolNode(a < b);
                case LT_EQ: return new BoolNode(a <= b);
                case GT: return new BoolNode(a > b);
                case GT_EQ: return new BoolNode(a >= b);
                case EQ_EQ: return new BoolNode(a == b);
                case BANG_EQ: return new BoolNode(a != b);
                default: break;
            }
        }

        // Constant Folding для конкатенации строк
        if (op == TokenType.PLUS && (left.isConstStr() || right.isConstStr())) {
            String a = left.isConstStr() ? left.getConstStr() : (left.isConstNum() ? stripTrailingZeros(left.getConstNum()) : null);
            String b = right.isConstStr() ? right.getConstStr() : (right.isConstNum() ? stripTrailingZeros(right.getConstNum()) : null);
            if (a != null && b != null) {
                return new StrNode(a + b);
            }
        }

        // Constant Folding для булевой логики
        if (left.isConstBool() && right.isConstBool()) {
            boolean a = left.getConstBool();
            boolean b = right.getConstBool();
            switch (op) {
                case AMP_AMP: return new BoolNode(a && b);
                case PIPE_PIPE: return new BoolNode(a || b);
                case EQ_EQ: return new BoolNode(a == b);
                case BANG_EQ: return new BoolNode(a != b);
                default: break;
            }
        }

        // Dead code elimination при коротком замыкании
        if (op == TokenType.AMP_AMP && left.isConstBool()) {
            return left.getConstBool() ? right : new BoolNode(false);
        }
        if (op == TokenType.PIPE_PIPE && left.isConstBool()) {
            return left.getConstBool() ? new BoolNode(true) : right;
        }

        return new BinaryNode(left, op, right);
    }

    private List<ExprNode> parseArgList() {
        List<ExprNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after arguments");
        return args;
    }

    private static String stripTrailingZeros(double val) {
        if (val == (long) val) return String.valueOf((long) val);
        return String.valueOf(val);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token consume(TokenType type, String errorMsg) {
        if (check(type)) return advance();
        throw new IllegalArgumentException(errorMsg + " (found " + peek() + " at pos " + peek().start + ")");
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private enum Precedence {
        LOWEST,
        OR,          // ||
        AND,         // &&
        EQUALITY,    // == !=
        COMPARISON,  // < <= > >=
        TERM,        // + -
        FACTOR,      // * / %
        UNARY,       // ! -
        CALL,        // . ()
        PRIMARY
    }

    private static Precedence getPrecedence(TokenType type) {
        switch (type) {
            case PIPE_PIPE: return Precedence.OR;
            case AMP_AMP: return Precedence.AND;
            case EQ_EQ:
            case BANG_EQ: return Precedence.EQUALITY;
            case LT:
            case LT_EQ:
            case GT:
            case GT_EQ: return Precedence.COMPARISON;
            case PLUS:
            case MINUS: return Precedence.TERM;
            case STAR:
            case SLASH:
            case PERCENT: return Precedence.FACTOR;
            case DOT:
            case LPAREN: return Precedence.CALL;
            default: return Precedence.LOWEST;
        }
    }

    // ================================= AST NODES =================================

    public interface ExprNode {
        default boolean isConstNum() { return false; }
        default double getConstNum() { return 0.0; }
        default boolean isConstBool() { return false; }
        default boolean getConstBool() { return false; }
        default boolean isConstStr() { return false; }
        default String getConstStr() { return null; }
        void emit(BytecodeEmitter emitter);
    }

    public static final class NumNode implements ExprNode {
        private final double val;
        public NumNode(double val) { this.val = val; }
        @Override public boolean isConstNum() { return true; }
        @Override public double getConstNum() { return val; }
        @Override public void emit(BytecodeEmitter emitter) {
            int idx = emitter.addNum(val);
            emitter.emit(NanoOpcode.OP_CONST_NUM, idx);
        }
    }

    public static final class BoolNode implements ExprNode {
        private final boolean val;
        public BoolNode(boolean val) { this.val = val; }
        @Override public boolean isConstBool() { return true; }
        @Override public boolean getConstBool() { return val; }
        @Override public void emit(BytecodeEmitter emitter) {
            emitter.emit(val ? NanoOpcode.OP_CONST_TRUE : NanoOpcode.OP_CONST_FALSE);
        }
    }

    public static final class StrNode implements ExprNode {
        private final String val;
        public StrNode(String val) { this.val = val; }
        @Override public boolean isConstStr() { return true; }
        @Override public String getConstStr() { return val; }
        @Override public void emit(BytecodeEmitter emitter) {
            int idx = emitter.addStr(val);
            emitter.emit(NanoOpcode.OP_CONST_STR, idx);
        }
    }

    public static final class VarNode implements ExprNode {
        private final String name;
        public VarNode(String name) { this.name = name; }
        @Override public void emit(BytecodeEmitter emitter) {
            int idx = emitter.addStr(name);
            emitter.emit(NanoOpcode.OP_LOAD_VAR, idx);
        }
    }

    public static final class PropertyNode implements ExprNode {
        private final ExprNode target;
        private final String prop;
        public PropertyNode(ExprNode target, String prop) {
            this.target = target;
            this.prop = prop;
        }
        @Override public void emit(BytecodeEmitter emitter) {
            target.emit(emitter);
            int idx = emitter.addStr(prop);
            emitter.emit(NanoOpcode.OP_LOAD_PROP, idx);
        }
    }

    public static final class CallNode implements ExprNode {
        private final ExprNode target;
        private final String funcName;
        private final List<ExprNode> args;

        public CallNode(ExprNode target, String funcName, List<ExprNode> args) {
            this.target = target;
            this.funcName = funcName;
            this.args = args;
        }

        @Override public void emit(BytecodeEmitter emitter) {
            if (target != null) {
                target.emit(emitter);
                for (ExprNode arg : args) {
                    arg.emit(emitter);
                }
                int idx = emitter.addStr(funcName);
                emitter.emit(NanoOpcode.OP_INVOKE_METHOD, idx, args.size());
            } else {
                for (ExprNode arg : args) {
                    arg.emit(emitter);
                }
                int idx = emitter.addStr(funcName);
                emitter.emit(NanoOpcode.OP_INVOKE, idx, args.size());
            }
        }
    }

    public static final class UnaryNode implements ExprNode {
        private final TokenType op;
        private final ExprNode right;

        public UnaryNode(TokenType op, ExprNode right) {
            this.op = op;
            this.right = right;
        }

        @Override public void emit(BytecodeEmitter emitter) {
            right.emit(emitter);
            if (op == TokenType.MINUS) {
                emitter.emit(NanoOpcode.OP_NEG);
            } else if (op == TokenType.BANG) {
                emitter.emit(NanoOpcode.OP_NOT);
            }
        }
    }

    public static final class BinaryNode implements ExprNode {
        private final ExprNode left;
        private final TokenType op;
        private final ExprNode right;

        public BinaryNode(ExprNode left, TokenType op, ExprNode right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }

        @Override public void emit(BytecodeEmitter emitter) {
            if (op == TokenType.AMP_AMP) {
                // Short-circuit: если левое false -> выходим сразу
                left.emit(emitter);
                int jump = emitter.emitJump(NanoOpcode.OP_JUMP_IF_FALSE);
                emitter.emit(NanoOpcode.OP_POP); // снимаем true со стека
                right.emit(emitter);
                emitter.patchJump(jump);
                return;
            }
            if (op == TokenType.PIPE_PIPE) {
                // Short-circuit: если левое true -> выходим сразу
                left.emit(emitter);
                int jump = emitter.emitJump(NanoOpcode.OP_JUMP_IF_TRUE);
                emitter.emit(NanoOpcode.OP_POP); // снимаем false со стека
                right.emit(emitter);
                emitter.patchJump(jump);
                return;
            }

            left.emit(emitter);
            right.emit(emitter);

            switch (op) {
                case PLUS: emitter.emit(NanoOpcode.OP_ADD); break;
                case MINUS: emitter.emit(NanoOpcode.OP_SUB); break;
                case STAR: emitter.emit(NanoOpcode.OP_MUL); break;
                case SLASH: emitter.emit(NanoOpcode.OP_DIV); break;
                case PERCENT: emitter.emit(NanoOpcode.OP_MOD); break;
                case EQ_EQ: emitter.emit(NanoOpcode.OP_EQ); break;
                case BANG_EQ: emitter.emit(NanoOpcode.OP_NE); break;
                case LT: emitter.emit(NanoOpcode.OP_LT); break;
                case LT_EQ: emitter.emit(NanoOpcode.OP_LE); break;
                case GT: emitter.emit(NanoOpcode.OP_GT); break;
                case GT_EQ: emitter.emit(NanoOpcode.OP_GE); break;
                default: break;
            }
        }
    }
}
