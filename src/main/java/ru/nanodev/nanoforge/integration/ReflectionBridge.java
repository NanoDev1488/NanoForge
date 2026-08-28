package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Позволяет дёргать методы у других активных плагинов сервера по имени плагина + имени метода.
 * Работает только с методами без аргументов или с аргументами типа String/int/boolean/double,
 * которые передаются как строки и приводятся автоматически.
 */
public class ReflectionBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");

    /**
     * Вызвать метод у главного класса плагина target.
     * @param target имя плагина (как оно в plugin.yml того плагина / папке plugins)
     * @param method имя метода
     * @param rawArgs аргументы в виде строк (будут приведены к нужным типам, если совпадёт число)
     * @return результат метода, или null если вызов не удался
     */
    public static Object call(String target, String method, String[] rawArgs) {
        Plugin plugin;
        try {
            plugin = Bukkit.getPluginManager().getPlugin(target);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Не удалось обратиться к серверу плагинов при вызове " + target + "#" + method + ": " + t);
            return null;
        }
        if (plugin == null) {
            LOG.warning("[NanoForge] Плагин не найден или выключен: " + target);
            return null;
        }
        try {
            for (Method m : plugin.getClass().getMethods()) {
                if (!m.getName().equals(method)) continue;
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length != rawArgs.length) continue;

                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = coerce(paramTypes[i], rawArgs[i]);
                }
                m.setAccessible(true);
                return m.invoke(plugin, args);
            }
            LOG.warning("[NanoForge] Метод " + method + "(" + rawArgs.length + " арг.) не найден в " + target);
        } catch (Exception e) {
            // одна строка вместо полного стектрейса - подробности в e.toString()/сообщении причины
            LOG.warning("[NanoForge] Ошибка вызова " + target + "#" + method + ": " + e);
        }
        return null;
    }

    private static Object coerce(Class<?> type, String value) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        return value;
    }

    /** Достать сам объект-инстанс плагина (для более гибких кастомных интеграций в коде). */
    public static Plugin getPluginInstance(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }
}

// by t.me/NanoDev_mc
