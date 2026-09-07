package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Мост к PlaceholderAPI (плейсхолдеры вида %player_name%, %vault_eco_balance%
 * и т.д. от ЛЮБЫХ других плагинов, зарегистрировавших свои расширения в PAPI).
 * Как и Vault/WorldGuard - через рефлексию, без жёсткой compile-time
 * зависимости: NanoForge собирается и работает без PlaceholderAPI тоже.
 */
public class PlaceholderAPIBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static Boolean available = null;
    private static Method setPlaceholdersMethod;

    @SuppressWarnings("unchecked")
    private static boolean isAvailable() {
        if (available == null) {
            try {
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                    available = false;
                } else {
                    Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    setPlaceholdersMethod = clazz.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                    available = true;
                }
            } catch (Throwable t) {
                available = false;
            }
            if (!available) {
                LOG.info("[NanoForge] PlaceholderAPI не найден - %плейсхолдеры% от других плагинов работать не будут "
                        + "(свои {player}/{world}/... работают в любом случае).");
            }
        }
        return available;
    }

    /** Только для тестов - сбрасывает закешированное состояние проверки PlaceholderAPI. */
    static void resetForTests() {
        available = null;
        setPlaceholdersMethod = null;
    }

    public static String apply(String text, Player player) {
        if (text == null || player == null) return text;
        try {
            if (!isAvailable()) return text;
            return (String) setPlaceholdersMethod.invoke(null, player, text);
        } catch (Throwable t) {
            return text; // что-то пошло не так - лучше вернуть текст как есть, чем уронить action
        }
    }
}

// by t.me/NanoDev_mc
