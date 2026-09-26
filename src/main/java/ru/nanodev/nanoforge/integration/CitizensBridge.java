package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.logging.Logger;

/**
 * В отличие от NanoForgeExpansion (PlaceholderAPI), для Citizens компиляция
 * БЕЗ зависимости от их jar'а полностью возможна - нам не нужно расширять
 * никакой их класс/интерфейс, только вызывать статический метод и читать
 * поле id с уже существующего NPC-объекта. Поэтому это обычный мост чистой
 * рефлексией, как VaultBridge/WorldGuardBridge/LuckPermsBridge - работает
 * (условие/триггер просто неактивны) даже без Citizens на classpath вообще.
 */
public class CitizensBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static Boolean available = null;
    private static boolean warnedMissing = false;

    private static boolean pluginPresent() {
        if (available == null) {
            try {
                available = Bukkit.getPluginManager().getPlugin("Citizens") != null;
            } catch (Throwable t) {
                available = false;
            }
            if (!available && !warnedMissing) {
                warnedMissing = true;
                LOG.info("[NanoForge] Citizens не найден - секции 'npcs:' в addon.yml работать не будут.");
            }
        }
        return available;
    }

    /** Только для тестов. */
    static void resetForTests() {
        available = null;
        warnedMissing = false;
    }

    /** null, если entity не является NPC Citizens (или Citizens не установлен). */
    public static Integer getNpcId(Entity entity) {
        if (entity == null || !pluginPresent()) return null;
        try {
            Class<?> citizensApi = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = citizensApi.getMethod("getNPCRegistry").invoke(null);
            Object npc = registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
            if (npc == null) return null;
            return (Integer) npc.getClass().getMethod("getId").invoke(npc);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Проверка NPC Citizens не удалась (возможно другая версия API): " + t);
            return null;
        }
    }
}

// by t.me/NanoDev_mc
