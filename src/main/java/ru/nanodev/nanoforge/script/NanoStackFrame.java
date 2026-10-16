package ru.nanodev.nanoforge.script;

/**
 * Переиспользуемый стековый фрейм для NanoVM.
 * Полностью исключает аллокации (zero garbage) в цикле исполнения в тике.
 */
public final class NanoStackFrame {

    public static final byte TYPE_NUM = 0;
    public static final byte TYPE_OBJ = 1;
    public static final byte TYPE_NULL = 2;

    private static final int INITIAL_CAPACITY = 64;

    public double[] numStack = new double[INITIAL_CAPACITY];
    public Object[] objStack = new Object[INITIAL_CAPACITY];
    public byte[] typeStack = new byte[INITIAL_CAPACITY];
    public int sp = 0;

    public void reset() {
        while (sp > 0) {
            sp--;
            objStack[sp] = null;
        }
    }

    private void ensureCapacity() {
        if (sp >= numStack.length) {
            int newCap = numStack.length * 2;
            double[] newNum = new double[newCap];
            Object[] newObj = new Object[newCap];
            byte[] newType = new byte[newCap];
            System.arraycopy(numStack, 0, newNum, 0, numStack.length);
            System.arraycopy(objStack, 0, newObj, 0, objStack.length);
            System.arraycopy(typeStack, 0, newType, 0, typeStack.length);
            numStack = newNum;
            objStack = newObj;
            typeStack = newType;
        }
    }

    public void pushNum(double val) {
        ensureCapacity();
        numStack[sp] = val;
        objStack[sp] = null;
        typeStack[sp] = TYPE_NUM;
        sp++;
    }

    public void pushBool(boolean val) {
        ensureCapacity();
        numStack[sp] = val ? 1.0 : 0.0;
        objStack[sp] = null;
        typeStack[sp] = TYPE_NUM;
        sp++;
    }

    public void pushObj(Object val) {
        if (val == null) {
            pushNull();
        } else if (val instanceof Number) {
            pushNum(((Number) val).doubleValue());
        } else if (val instanceof Boolean) {
            pushBool((Boolean) val);
        } else {
            ensureCapacity();
            objStack[sp] = val;
            numStack[sp] = 0.0;
            typeStack[sp] = TYPE_OBJ;
            sp++;
        }
    }

    public void pushNull() {
        ensureCapacity();
        numStack[sp] = 0.0;
        objStack[sp] = null;
        typeStack[sp] = TYPE_NULL;
        sp++;
    }

    public void pop() {
        if (sp > 0) {
            sp--;
            objStack[sp] = null;
        }
    }

    public double popNum() {
        if (sp <= 0) return 0.0;
        sp--;
        double val = numStack[sp];
        objStack[sp] = null;
        return val;
    }

    public boolean popBool() {
        if (sp <= 0) return false;
        sp--;
        boolean b = isTruthy(sp);
        objStack[sp] = null;
        return b;
    }

    public Object popObj() {
        if (sp <= 0) return null;
        sp--;
        Object obj = toObject(sp);
        objStack[sp] = null;
        return obj;
    }

    public double peekNum() {
        if (sp <= 0) return 0.0;
        return numStack[sp - 1];
    }

    public boolean peekBool() {
        if (sp <= 0) return false;
        return isTruthy(sp - 1);
    }

    public Object peekObj() {
        if (sp <= 0) return null;
        return toObject(sp - 1);
    }

    public boolean isTruthy(int idx) {
        byte t = typeStack[idx];
        if (t == TYPE_NUM) {
            return numStack[idx] != 0.0;
        } else if (t == TYPE_OBJ) {
            Object o = objStack[idx];
            return o != null && !Boolean.FALSE.equals(o);
        }
        return false;
    }

    public Object toObject(int idx) {
        byte t = typeStack[idx];
        if (t == TYPE_NUM) {
            double v = numStack[idx];
            if (v == (long) v) {
                return (long) v;
            }
            return v;
        } else if (t == TYPE_OBJ) {
            return objStack[idx];
        }
        return null;
    }
}
