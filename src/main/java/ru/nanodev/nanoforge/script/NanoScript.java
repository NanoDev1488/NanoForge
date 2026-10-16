package ru.nanodev.nanoforge.script;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import ru.nanodev.nanoforge.model.Addon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный фасад для NanoScript Expression Engine & Micro-VM.
 * Автоматически кэширует скомпилированный байткод и предоставляет удобные методы eval.
 */
public final class NanoScript {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, NanoProgram> PROGRAM_CACHE = new ConcurrentHashMap<>();

    private NanoScript() {}

    public static NanoProgram compile(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return new NanoProgram(new int[0], new double[0], new String[0], "");
        }
        String trimmed = expression.trim();
        NanoProgram cached = PROGRAM_CACHE.get(trimmed);
        if (cached != null) {
            return cached;
        }

        NanoProgram compiled = NanoParser.parseAndCompile(trimmed);
        if (PROGRAM_CACHE.size() < MAX_CACHE_SIZE) {
            PROGRAM_CACHE.put(trimmed, compiled);
        }
        return compiled;
    }

    public static Object eval(String expression, NanoContext context) {
        NanoProgram program = compile(expression);
        return NanoVM.execute(program, context);
    }

    public static Object eval(String expression, Player player, Event event, Addon addon) {
        NanoContext ctx = NanoContext.of(player, event, addon);
        return eval(expression, ctx);
    }

    public static boolean evalBoolean(String expression, Player player, Event event, Addon addon) {
        if (expression == null || expression.trim().isEmpty()) return true;
        NanoContext ctx = NanoContext.of(player, event, addon);
        NanoProgram program = compile(expression);
        return NanoVM.executeBoolean(program, ctx);
    }

    public static double evalDouble(String expression, Player player, Event event, Addon addon) {
        if (expression == null || expression.trim().isEmpty()) return 0.0;
        NanoContext ctx = NanoContext.of(player, event, addon);
        NanoProgram program = compile(expression);
        return NanoVM.executeDouble(program, ctx);
    }

    public static String evalString(String expression, Player player, Event event, Addon addon) {
        Object val = eval(expression, player, event, addon);
        if (val == null) return "";
        if (val instanceof Double) {
            double d = (Double) val;
            if (d == (long) d) return String.valueOf((long) d);
        }
        return String.valueOf(val);
    }

    public static void clearCache() {
        PROGRAM_CACHE.clear();
    }
}
