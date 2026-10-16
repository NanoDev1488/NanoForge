package ru.nanodev.nanoforge.script;

/**
 * Байткод-инструкции для микро-виртуальной машины NanoVM.
 * Выражения компилируются в плоский int[] массив (opcode + опциональные аргументы).
 */
public final class NanoOpcode {

    private NanoOpcode() {}

    public static final int OP_NOP = 0;
    public static final int OP_CONST_NUM = 1;     // [OP, num_pool_idx]
    public static final int OP_CONST_STR = 2;     // [OP, str_pool_idx]
    public static final int OP_CONST_TRUE = 3;    // [OP]
    public static final int OP_CONST_FALSE = 4;   // [OP]
    public static final int OP_CONST_NULL = 5;    // [OP]

    public static final int OP_LOAD_VAR = 6;      // [OP, str_pool_idx]
    public static final int OP_LOAD_PROP = 7;     // [OP, str_pool_idx] (снимает объект со стека, кладёт свойство)
    public static final int OP_INVOKE = 8;        // [OP, str_pool_idx, arg_count] (глобальная функция)
    public static final int OP_INVOKE_METHOD = 9; // [OP, str_pool_idx, arg_count] (снимает аргументы и target)

    public static final int OP_ADD = 10;
    public static final int OP_SUB = 11;
    public static final int OP_MUL = 12;
    public static final int OP_DIV = 13;
    public static final int OP_MOD = 14;
    public static final int OP_NEG = 15;          // унарный минус

    public static final int OP_NOT = 16;          // унарное отрицание !

    public static final int OP_EQ = 20;           // ==
    public static final int OP_NE = 21;           // !=
    public static final int OP_LT = 22;           // <
    public static final int OP_LE = 23;           // <=
    public static final int OP_GT = 24;           // >
    public static final int OP_GE = 25;           // >=

    public static final int OP_JUMP_IF_FALSE = 30; // [OP, target_ip] (без pop для short-circuit &&)
    public static final int OP_JUMP_IF_TRUE = 31;  // [OP, target_ip] (без pop для short-circuit ||)
    public static final int OP_JUMP = 32;          // [OP, target_ip]

    public static final int OP_POP = 35;
    public static final int OP_RETURN = 40;

    public static String name(int opcode) {
        switch (opcode) {
            case OP_NOP: return "NOP";
            case OP_CONST_NUM: return "CONST_NUM";
            case OP_CONST_STR: return "CONST_STR";
            case OP_CONST_TRUE: return "CONST_TRUE";
            case OP_CONST_FALSE: return "CONST_FALSE";
            case OP_CONST_NULL: return "CONST_NULL";
            case OP_LOAD_VAR: return "LOAD_VAR";
            case OP_LOAD_PROP: return "LOAD_PROP";
            case OP_INVOKE: return "INVOKE";
            case OP_INVOKE_METHOD: return "INVOKE_METHOD";
            case OP_ADD: return "ADD";
            case OP_SUB: return "SUB";
            case OP_MUL: return "MUL";
            case OP_DIV: return "DIV";
            case OP_MOD: return "MOD";
            case OP_NEG: return "NEG";
            case OP_NOT: return "NOT";
            case OP_EQ: return "EQ";
            case OP_NE: return "NE";
            case OP_LT: return "LT";
            case OP_LE: return "LE";
            case OP_GT: return "GT";
            case OP_GE: return "GE";
            case OP_JUMP_IF_FALSE: return "JUMP_IF_FALSE";
            case OP_JUMP_IF_TRUE: return "JUMP_IF_TRUE";
            case OP_JUMP: return "JUMP";
            case OP_POP: return "POP";
            case OP_RETURN: return "RETURN";
            default: return "UNKNOWN_" + opcode;
        }
    }
}
