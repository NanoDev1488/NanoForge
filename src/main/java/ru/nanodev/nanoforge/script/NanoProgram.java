package ru.nanodev.nanoforge.script;

import java.util.Arrays;

/**
 * Скомпилированная программа NanoScript.
 * Хранит плоский массив инструкций и пулы констант.
 */
public final class NanoProgram {

    public final int[] code;
    public final double[] numPool;
    public final String[] strPool;
    public final String source;

    public NanoProgram(int[] code, double[] numPool, String[] strPool, String source) {
        this.code = code != null ? code : new int[0];
        this.numPool = numPool != null ? numPool : new double[0];
        this.strPool = strPool != null ? strPool : new String[0];
        this.source = source != null ? source : "";
    }

    public String disassemble() {
        StringBuilder sb = new StringBuilder();
        sb.append("// NanoScript Bytecode Disassembly [").append(source).append("]\n");
        sb.append("// Constants: nums=").append(Arrays.toString(numPool))
          .append(", strs=").append(Arrays.toString(strPool)).append("\n");

        int ip = 0;
        while (ip < code.length) {
            int curIp = ip;
            int op = code[ip++];
            sb.append(String.format("%04d  %-16s", curIp, NanoOpcode.name(op)));

            switch (op) {
                case NanoOpcode.OP_CONST_NUM: {
                    int idx = code[ip++];
                    double val = (idx >= 0 && idx < numPool.length) ? numPool[idx] : 0.0;
                    sb.append(" #").append(idx).append(" (").append(val).append(")");
                    break;
                }
                case NanoOpcode.OP_CONST_STR:
                case NanoOpcode.OP_LOAD_VAR:
                case NanoOpcode.OP_LOAD_PROP: {
                    int idx = code[ip++];
                    String val = (idx >= 0 && idx < strPool.length) ? strPool[idx] : "?";
                    sb.append(" #").append(idx).append(" (\"").append(val).append("\")");
                    break;
                }
                case NanoOpcode.OP_INVOKE:
                case NanoOpcode.OP_INVOKE_METHOD: {
                    int idx = code[ip++];
                    int args = code[ip++];
                    String name = (idx >= 0 && idx < strPool.length) ? strPool[idx] : "?";
                    sb.append(" #").append(idx).append(" (\"").append(name).append("\", args=").append(args).append(")");
                    break;
                }
                case NanoOpcode.OP_JUMP:
                case NanoOpcode.OP_JUMP_IF_FALSE:
                case NanoOpcode.OP_JUMP_IF_TRUE: {
                    int target = code[ip++];
                    sb.append(" -> ").append(String.format("%04d", target));
                    break;
                }
                default:
                    break;
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
