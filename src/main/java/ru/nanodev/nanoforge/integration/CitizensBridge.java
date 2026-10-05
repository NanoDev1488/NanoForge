package ru.nanodev.nanoforge.integration;

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
    private static final ru.nanodev.nanoforge.util.PluginPresenceCache PRESENCE =
            new ru.nanodev.nanoforge.util.PluginPresenceCache("Citizens",
                    "[NanoForge] Citizens не найден - секции 'npcs:' в addon.yml работать не будут.", LOG);

    /** Только для тестов. */
    static void resetForTests() {
        PRESENCE.resetForTests();
    }

    /** null, если entity не является NPC Citizens (или Citizens не установлен). */
    public static Integer getNpcId(Entity entity) {
        if (entity == null || !PRESENCE.isPresent()) return null;
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
