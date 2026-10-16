package ru.nanodev.nanoforge.script;

import java.util.Objects;

/**
 * Высокопроизводительная виртуальная машина (NanoVM).
 * Цикл выполнения работает на пуле стековых фреймов через ThreadLocal (Zero Allocation в тике).
 */
public final class NanoVM {

    private static final ThreadLocal<NanoStackFrame> LOCAL_FRAME = ThreadLocal.withInitial(NanoStackFrame::new);
    private static final ThreadLocal<Object[]> LOCAL_ARGS_BUFFER = ThreadLocal.withInitial(() -> new Object[16]);

    private NanoVM() {}

    public static Object execute(NanoProgram program, NanoContext context) {
        if (program == null || program.code.length == 0) return null;
        NanoStackFrame frame = LOCAL_FRAME.get();
        frame.reset();

        Object[] argsBuf = LOCAL_ARGS_BUFFER.get();
        int[] code = program.code;
        double[] numPool = program.numPool;
        String[] strPool = program.strPool;
        int ip = 0;
        int len = code.length;

        while (ip < len) {
            int op = code[ip++];

            switch (op) {
                case NanoOpcode.OP_NOP:
                    break;

                case NanoOpcode.OP_CONST_NUM: {
                    int idx = code[ip++];
                    frame.pushNum(numPool[idx]);
                    break;
                }

                case NanoOpcode.OP_CONST_STR: {
                    int idx = code[ip++];
                    frame.pushObj(strPool[idx]);
                    break;
                }

                case NanoOpcode.OP_CONST_TRUE:
                    frame.pushBool(true);
                    break;

                case NanoOpcode.OP_CONST_FALSE:
                    frame.pushBool(false);
                    break;

                case NanoOpcode.OP_CONST_NULL:
                    frame.pushNull();
                    break;

                case NanoOpcode.OP_LOAD_VAR: {
                    int idx = code[ip++];
                    String name = strPool[idx];
                    Object val = (context != null) ? context.resolveVar(name) : null;
                    frame.pushObj(val);
                    break;
                }

                case NanoOpcode.OP_LOAD_PROP: {
                    int idx = code[ip++];
                    String prop = strPool[idx];
                    Object target = frame.popObj();
                    Object val = BukkitBinding.getProperty(target, prop);
                    frame.pushObj(val);
                    break;
                }

                case NanoOpcode.OP_INVOKE: {
                    int idx = code[ip++];
                    int argCount = code[ip++];
                    String funcName = strPool[idx];

                    // Аргументы глобальной функции
                    for (int i = argCount - 1; i >= 0; i--) {
                        argsBuf[i] = frame.popObj();
                    }

                    Object result = BukkitBinding.invokeGlobal(funcName, copyArgs(argsBuf, argCount), context);

                    for (int i = 0; i < argCount; i++) {
                        argsBuf[i] = null;
                    }

                    frame.pushObj(result);
                    break;
                }

                case NanoOpcode.OP_INVOKE_METHOD: {
                    int idx = code[ip++];
                    int argCount = code[ip++];
                    String methodName = strPool[idx];

                    // Аргументы метода объекта
                    for (int i = argCount - 1; i >= 0; i--) {
                        argsBuf[i] = frame.popObj();
                    }
                    Object target = frame.popObj();

                    Object result = BukkitBinding.invokeMethod(target, methodName, copyArgs(argsBuf, argCount), context);

                    for (int i = 0; i < argCount; i++) {
                        argsBuf[i] = null;
                    }

                    frame.pushObj(result);
                    break;
                }

                case NanoOpcode.OP_ADD: {
                    byte t2 = frame.typeStack[frame.sp - 1];
                    byte t1 = frame.typeStack[frame.sp - 2];
                    if (t1 == NanoStackFrame.TYPE_NUM && t2 == NanoStackFrame.TYPE_NUM) {
                        double b = frame.popNum();
                        double a = frame.popNum();
                        frame.pushNum(a + b);
                    } else {
                        Object b = frame.popObj();
                        Object a = frame.popObj();
                        frame.pushObj(String.valueOf(a) + String.valueOf(b));
                    }
                    break;
                }

                case NanoOpcode.OP_SUB: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushNum(a - b);
                    break;
                }

                case NanoOpcode.OP_MUL: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushNum(a * b);
                    break;
                }

                case NanoOpcode.OP_DIV: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushNum(b == 0.0 ? 0.0 : a / b);
                    break;
                }

                case NanoOpcode.OP_MOD: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushNum(b == 0.0 ? 0.0 : a % b);
                    break;
                }

                case NanoOpcode.OP_NEG: {
                    frame.pushNum(-frame.popNum());
                    break;
                }

                case NanoOpcode.OP_NOT: {
                    frame.pushBool(!frame.popBool());
                    break;
                }

                case NanoOpcode.OP_EQ: {
                    Object b = frame.popObj();
                    Object a = frame.popObj();
                    frame.pushBool(compareEqual(a, b));
                    break;
                }

                case NanoOpcode.OP_NE: {
                    Object b = frame.popObj();
                    Object a = frame.popObj();
                    frame.pushBool(!compareEqual(a, b));
                    break;
                }

                case NanoOpcode.OP_LT: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushBool(a < b);
                    break;
                }

                case NanoOpcode.OP_LE: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushBool(a <= b);
                    break;
                }

                case NanoOpcode.OP_GT: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushBool(a > b);
                    break;
                }

                case NanoOpcode.OP_GE: {
                    double b = frame.popNum();
                    double a = frame.popNum();
                    frame.pushBool(a >= b);
                    break;
                }

                case NanoOpcode.OP_JUMP_IF_FALSE: {
                    int targetIp = code[ip++];
                    if (!frame.peekBool()) {
                        ip = targetIp;
                    }
                    break;
                }

                case NanoOpcode.OP_JUMP_IF_TRUE: {
                    int targetIp = code[ip++];
                    if (frame.peekBool()) {
                        ip = targetIp;
                    }
                    break;
                }

                case NanoOpcode.OP_JUMP: {
                    ip = code[ip++];
                    break;
                }

                case NanoOpcode.OP_POP:
                    frame.pop();
                    break;

                case NanoOpcode.OP_RETURN:
                    return frame.popObj();

                default:
                    throw new IllegalStateException("Unknown opcode: " + op + " at ip=" + (ip - 1));
            }
        }

        return frame.popObj();
    }

    public static boolean executeBoolean(NanoProgram program, NanoContext context) {
        Object res = execute(program, context);
        if (res instanceof Boolean) return (Boolean) res;
        if (res instanceof Number) return ((Number) res).doubleValue() != 0.0;
        return res != null;
    }

    public static double executeDouble(NanoProgram program, NanoContext context) {
        Object res = execute(program, context);
        return BukkitBinding.toDouble(res);
    }

    private static boolean compareEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        if (a instanceof String || b instanceof String) {
            return a.toString().equalsIgnoreCase(b.toString());
        }
        return Objects.equals(a, b);
    }

    private static Object[] copyArgs(Object[] src, int count) {
        Object[] dst = new Object[count];
        System.arraycopy(src, 0, dst, 0, count);
        return dst;
    }

    private static Object[] copyArgsSlice(Object[] src, int start, int end) {
        int len = Math.max(0, end - start);
        Object[] dst = new Object[len];
        System.arraycopy(src, start, dst, 0, len);
        return dst;
    }
}
