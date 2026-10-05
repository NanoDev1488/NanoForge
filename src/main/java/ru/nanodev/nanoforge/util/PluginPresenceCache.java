package ru.nanodev.nanoforge.util;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * До 1.6.0 каждый мост в integration/ (WorldGuardBridge, LuckPermsBridge,
 * CitizensBridge), которому нужно было ТОЛЬКО ответить "плагин X установлен?"
 * (без дополнительного кеширования разрешённых через рефлексию классов/методов,
 * как у Vault/PlaceholderAPI), независимо писал одну и ту же пару полей
 * (Boolean available + boolean warnedMissing) и один и тот же метод проверки.
 * Теперь один класс, а не три копии, которые легко могли незаметно разойтись
 * при следующей правке (например, забыть сбросить кеш в одной из трёх копий
 * при добавлении нового resetForTests()).
 */
public final class PluginPresenceCache {

    private final String pluginName;
    private final String infoMessageIfMissing;
    private final Logger logger;

    private Boolean available;
    private boolean warnedMissing;

    /**
     * @param pluginName           точное имя плагина, как оно объявлено в его plugin.yml
     * @param infoMessageIfMissing что напечатать в консоль ОДИН РАЗ, если плагин не найден;
     *                             null - значит вообще ничего не печатать
     */
    public PluginPresenceCache(String pluginName, String infoMessageIfMissing, Logger logger) {
        this.pluginName = pluginName;
        this.infoMessageIfMissing = infoMessageIfMissing;
        this.logger = logger;
    }

    public boolean isPresent() {
        if (available == null) {
            try {
                available = Bukkit.getPluginManager().getPlugin(pluginName) != null;
            } catch (Throwable t) {
                available = false;
            }
            if (!available && !warnedMissing && infoMessageIfMissing != null) {
                warnedMissing = true;
                logger.info(infoMessageIfMissing);
            }
        }
        return available;
    }

    /** Только для тестов - сбрасывает закешированное состояние между тестовыми методами. */
    public void resetForTests() {
        available = null;
        warnedMissing = false;
    }
}

// by t.me/NanoDev_mc
