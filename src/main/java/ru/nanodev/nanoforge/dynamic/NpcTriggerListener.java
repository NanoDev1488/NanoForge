package ru.nanodev.nanoforge.dynamic;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.integration.CitizensBridge;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;

import java.util.List;

/**
 * addon.yml:
 * <pre>
 * npcs:
 *   greeter:
 *     npc_id: 5
 *     actions:
 *       - type: message
 *         text: "&aПривет, путник!"
 * </pre>
 * Ловит клик по ЛЮБОЙ сущности через обычный Bukkit PlayerInteractEntityEvent
 * (компилируется и работает без Citizens на classpath вообще), и ТОЛЬКО если
 * {@link CitizensBridge} через рефлексию подтверждает, что это реально NPC
 * Citizens с нужным id - выполняет actions. Без Citizens на сервере
 * CitizensBridge.getNpcId() всегда вернёт null, и обработчик - no-op.
 */
public class NpcTriggerListener implements Listener {

    private final AddonManager addonManager;

    public NpcTriggerListener(AddonManager addonManager) {
        this.addonManager = addonManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Integer npcId = CitizensBridge.getNpcId(event.getRightClicked());
        if (npcId == null) return;

        for (Addon addon : addonManager.getAddons()) {
            ConfigurationSection npcsSection = addon.getYaml().getConfigurationSection("npcs");
            if (npcsSection == null) continue;
            for (String key : npcsSection.getKeys(false)) {
                ConfigurationSection npc = npcsSection.getConfigurationSection(key);
                if (npc == null) continue;
                if (npc.getInt("npc_id", -1) != npcId) continue;

                Object actions = npc.getList("actions");
                if (actions instanceof List) {
                    ActionRunner.run((List<?>) actions, event.getPlayer(), event, null, addon);
                }
            }
        }
    }
}

// by t.me/NanoDev_mc
