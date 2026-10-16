package ru.nanodev.nanoforge.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Вспомогательный класс генерации байткода с пулами констант.
 */
public final class BytecodeEmitter {

    private final List<Integer> code = new ArrayList<>();
    private final List<Double> numPool = new ArrayList<>();
    private final List<String> strPool = new ArrayList<>();

    public int addNum(double val) {
        for (int i = 0; i < numPool.size(); i++) {
            if (Double.doubleToLongBits(numPool.get(i)) == Double.doubleToLongBits(val)) {
                return i;
            }
        }
        int idx = numPool.size();
        numPool.add(val);
        return idx;
    }

    public int addStr(String val) {
        if (val == null) val = "";
        for (int i = 0; i < strPool.size(); i++) {
            if (strPool.get(i).equals(val)) {
                return i;
            }
        }
        int idx = strPool.size();
        strPool.add(val);
        return idx;
    }

    public int currentIp() {
        return code.size();
    }

    public void emit(int op) {
        code.add(op);
    }

    public void emit(int op, int arg) {
        code.add(op);
        code.add(arg);
    }

    public void emit(int op, int arg1, int arg2) {
        code.add(op);
        code.add(arg1);
        code.add(arg2);
    }

    public int emitJump(int jumpOp) {
        code.add(jumpOp);
        int placeholderIp = code.size();
        code.add(0); // placeholder для целевого IP
        return placeholderIp;
    }

    public void patchJump(int placeholderIp) {
        code.set(placeholderIp, code.size());
    }

    public NanoProgram build(String source) {
        emit(NanoOpcode.OP_RETURN);
        int[] codeArr = new int[code.size()];
        for (int i = 0; i < code.size(); i++) {
            codeArr[i] = code.get(i);
        }

        double[] numArr = new double[numPool.size()];
        for (int i = 0; i < numPool.size(); i++) {
            numArr[i] = numPool.get(i);
        }

        String[] strArr = strPool.toArray(new String[0]);
        return new NanoProgram(codeArr, numArr, strArr, source);
    }
}
