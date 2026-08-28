package ru.nanodev.nanoforge.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Всё, что печатается один раз при старте плагина: проверка версии сервера
 * (плагин официально проверен на 1.16 - 1.21.11) и баннер с контактами автора.
 * Ни то, ни другое не мешает плагину запуститься - это просто предупреждения
 * в консоль, а не блокировка загрузки.
 */
public class StartupChecks {

    private static final int[] MIN_VERSION = {1, 16, 0};
    private static final int[] MAX_VERSION = {1, 21, 11};
    private static final String TELEGRAM = "t.me/NanoDev_mc";

    public enum VersionStatus { BELOW_MIN, SUPPORTED, ABOVE_MAX, UNKNOWN }

    /**
     * Чистая логика без обращения к Bukkit - принимает строку вида "1.20.4-R0.1-SNAPSHOT"
     * или просто "1.20.4" и классифицирует её относительно поддерживаемого диапазона.
     * Вынесено отдельно специально для того, чтобы это можно было протестировать
     * юнит-тестом без поднятия сервера (см. StartupChecksTest).
     */
    public static VersionStatus classify(String bukkitVersionRaw) {
        if (bukkitVersionRaw == null || bukkitVersionRaw.trim().isEmpty()) return VersionStatus.UNKNOWN;
        try {
            String mcVersion = bukkitVersionRaw.split("-")[0];
            int[] parsed = parse(mcVersion);
            if (compare(parsed, MIN_VERSION) < 0) return VersionStatus.BELOW_MIN;
            if (compare(parsed, MAX_VERSION) > 0) return VersionStatus.ABOVE_MAX;
            return VersionStatus.SUPPORTED;
        } catch (Throwable t) {
            return VersionStatus.UNKNOWN;
        }
    }

    /** Сверяет версию Minecraft на сервере с диапазоном 1.16 - 1.21.11, печатает предупреждение при выходе за него. */
    public static void checkServerVersion(Plugin plugin) {
        Logger log = plugin.getLogger();
        String raw = Bukkit.getBukkitVersion();
        String mcVersion = raw.split("-")[0];

        switch (classify(raw)) {
            case BELOW_MIN:
                log.warning("[NanoForge] Версия сервера (" + mcVersion + ") НИЖЕ минимальной поддерживаемой (1.16). "
                        + "Плагин может не работать. Если что-то сломано - " + TELEGRAM);
                break;
            case ABOVE_MAX:
                log.warning("[NanoForge] Версия сервера (" + mcVersion + ") ВЫШЕ максимальной проверенной версии (1.21.11). "
                        + "Плагин, скорее всего, будет работать (API обычно обратно совместим), но это не гарантировано. "
                        + "Если что-то сломано - " + TELEGRAM);
                break;
            case UNKNOWN:
                log.warning("[NanoForge] Не удалось определить версию сервера (" + raw + ") для проверки совместимости.");
                break;
            case SUPPORTED:
            default:
                // в диапазоне - ничего не печатаем, всё в порядке
                break;
        }
    }

    /** Баннер при старте - имя плагина, версия, контакт автора. */
    public static void printBanner(Plugin plugin) {
        Logger log = plugin.getLogger();
        log.info("========================================");
        log.info(" NanoForge v" + plugin.getDescription().getVersion());
        log.info(" Автор: " + TELEGRAM);
        log.info(" Вопросы, баги, идеи - туда же");
        log.info("========================================");
    }

    private static int[] parse(String version) {
        String[] parts = version.split("\\.");
        // major должен строго распарситься как число - если тут мусор ("garbage" и т.п.),
        // это НЕ версия Minecraft вообще, и результат должен быть UNKNOWN, а не тихий "0.0.0"
        // (который раньше ошибочно классифицировался как BELOW_MIN). minor/patch мягче -
        // это только доп. цифры, их отсутствие/кривизна не делает всю версию нераспознаваемой.
        int major = Integer.parseInt(parts[0].trim());
        int minor = parts.length > 1 ? safeInt(parts[1]) : 0;
        int patch = parts.length > 2 ? safeInt(parts[2]) : 0;
        return new int[]{major, minor, patch};
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return 0;
    }
}

// by t.me/NanoDev_mc
