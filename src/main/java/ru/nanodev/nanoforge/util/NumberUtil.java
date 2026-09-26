package ru.nanodev.nanoforge.util;

/**
 * Безопасный парсинг чисел из значений YAML (Integer/Double/String - YAML-парсер
 * Bukkit может отдать любое из этого в зависимости от того, как число записано
 * в addon.yml). До 1.5.0 этот же метод был продублирован дважды под разными
 * именами - {@code ActionRunner.parseDouble} и {@code ConditionChecker.toDouble} -
 * с абсолютно одинаковой реализацией. Теперь он один, и любой новый action/condition
 * обязан использовать именно его, а не писать свою копию.
 */
public final class NumberUtil {

    private NumberUtil() {
    }

    /** Возвращает def, если o == null или не парсится как число. */
    public static double toDouble(Object o, double def) {
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Возвращает def, если o == null или не парсится как целое число. */
    public static int toInt(Object o, int def) {
        return (int) toDouble(o, def);
    }
}

// by t.me/NanoDev_mc
