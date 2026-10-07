package ru.nanodev.nanoforge.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка доступа ко ВСЕМ текстам плагина (messages.yml) - и тем, что видит
 * игрок/админ в чате, и тем, что уходит в консоль сервера. Задача класса: НЕ
 * менять что сообщения говорят, а только откуда они берутся - вместо строковых
 * литералов, разбросанных по коду, единый YAML-файл, который можно редактировать
 * без пересборки плагина и перечитать на лету через /nano reload.
 *
 * Если messages.yml ещё не загружен ни разу (например, {@link #init} ещё не
 * вызывался - так происходит в юнит-тестах, которые создают классы напрямую,
 * минуя {@code NanoForgePlugin#onEnable}), ключи резолвятся из ДЕФОЛТНОГО
 * messages.yml, зашитого внутрь jar (classpath-ресурс) - это гарантирует, что
 * get() всегда возвращает реальный текст, а не "[missing message]" там, где
 * плагин просто не успел/не смог сохранить файл на диск.
 */
public final class Messages {

    private static volatile YamlConfiguration cache;

    /** Чтобы не заспамить консоль одним и тем же missing-key warning'ом на каждый вызов. */
    private static final Set<String> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    private Messages() {
    }

    /**
     * Вызывается один раз из {@code NanoForgePlugin#onEnable} (сразу после
     * saveDefaultConfig()). Копирует дефолтный messages.yml из jar'а на диск
     * (plugins/NanoForge/messages.yml), только если там его ещё нет - то есть
     * НЕ перезатирает правки сервер-админа при каждом рестарте - и загружает
     * файл с диска в память, чтобы get() дальше читал именно его (с диска можно
     * редактировать текст без пересборки плагина).
     */
    public static synchronized void init(Plugin plugin) {
        plugin.saveResource("messages.yml", false);
        reload(plugin);
    }

    /** /nano reload - перечитывает messages.yml с диска без перезапуска сервера. */
    public static synchronized void reload(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (file.exists()) {
            cache = YamlConfiguration.loadConfiguration(file);
        } else {
            // файла на диске ещё нет (например, saveResource не сработал) - подстраховываемся
            // дефолтом из jar'а, чтобы /nano reload не оставил плагин без текстов вообще.
            cache = loadBundledDefault();
        }
        WARNED_MISSING.clear();
    }

    /** Дефолтный messages.yml из classpath-ресурса (внутри jar'а, либо test-classpath в юнит-тестах). */
    private static YamlConfiguration loadBundledDefault() {
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    private static YamlConfiguration cfg() {
        YamlConfiguration local = cache;
        if (local == null) {
            synchronized (Messages.class) {
                local = cache;
                if (local == null) {
                    local = loadBundledDefault();
                    cache = local;
                }
            }
        }
        return local;
    }

    /** Без плейсхолдеров - просто перевод &-кодов в § и подстановка ключа. */
    public static String get(String path) {
        return get(path, (Object[]) null);
    }

    /**
     * @param placeholderPairs пары имя/значение, например
     *                          {@code Messages.get("command.update.result", "result", result)}
     *                          заменит "{result}" на значение result в тексте по этому пути.
     */
    public static String get(String path, Object... placeholderPairs) {
        String raw = cfg().getString(path);
        if (raw == null) {
            if (WARNED_MISSING.add(path)) {
                org.bukkit.Bukkit.getLogger().warning("[NanoForge] В messages.yml не найден ключ: " + path);
            }
            return "§c[missing message: " + path + "]";
        }

        String withPlaceholders = applyPlaceholders(raw, placeholderPairs);
        return ChatColor.translateAlternateColorCodes('&', withPlaceholders);
    }

    private static String applyPlaceholders(String text, Object[] pairs) {
        if (pairs == null || pairs.length == 0) return text;
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String name = String.valueOf(pairs[i]);
            String value = String.valueOf(pairs[i + 1]);
            result = result.replace("{" + name + "}", value);
        }
        return result;
    }

    /** Только для тестов - сбрасывает кэш, чтобы тесты не зависели от порядка запуска друг друга. */
    static void resetForTests() {
        cache = null;
        WARNED_MISSING.clear();
    }
}

// by t.me/NanoDev_mc
