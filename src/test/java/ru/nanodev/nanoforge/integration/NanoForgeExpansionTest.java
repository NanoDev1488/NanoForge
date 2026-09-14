package ru.nanodev.nanoforge.integration;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NanoForgeExpansionTest {

    @TempDir
    File tempDir;

    private NanoForgePlugin plugin;
    private AddonManager addonManager;
    private NanoForgeExpansion expansion;
    private Addon addon;

    @BeforeEach
    void setUp() {
        plugin = mock(NanoForgePlugin.class);
        addonManager = mock(AddonManager.class);
        when(plugin.getAddonManager()).thenReturn(addonManager);
        when(plugin.getDescription()).thenReturn(
                new org.bukkit.plugin.PluginDescriptionFile("NanoForge", "1.4.0", "ru.nanodev.nanoforge.NanoForgePlugin"));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "TestAddon");
        yaml.set("type", "new");
        yaml.set("enabled", true);
        addon = new Addon(new File(tempDir, "addon.yml"), yaml);
        when(addonManager.get("TestAddon")).thenReturn(addon);

        expansion = new NanoForgeExpansion(plugin);
    }

    @Test
    void identifierAndMetadataAreStable() {
        assertThat(expansion.getIdentifier()).isEqualTo("nanoforge");
        assertThat(expansion.persist()).isTrue();
        assertThat(expansion.getVersion()).isEqualTo("1.4.0");
    }

    @Test
    void returnsNullForUnknownAddon() {
        when(addonManager.get("Ghost")).thenReturn(null);

        String result = expansion.onRequest(null, "Ghost_somekey");

        assertThat(result).isNull();
    }

    @Test
    void returnsNullWhenNoUnderscoreSeparator() {
        String result = expansion.onRequest(null, "TestAddon");

        assertThat(result).isNull();
    }

    @Test
    void enabledKeyReflectsAddonState() {
        assertThat(expansion.onRequest(null, "TestAddon_enabled")).isEqualTo("true");

        addon.setEnabled(false);
        assertThat(expansion.onRequest(null, "TestAddon_enabled")).isEqualTo("false");
    }

    @Test
    void fallsBackToGlobalVarWhenNoPlayerGiven() {
        addon.getStorage().setGlobalVar("score", "42");

        String result = expansion.onRequest(null, "TestAddon_score");

        assertThat(result).isEqualTo("42");
    }

    @Test
    void returnsEmptyStringWhenNeitherPlayerNorGlobalVarExists() {
        String result = expansion.onRequest(null, "TestAddon_does_not_exist");

        assertThat(result).isEqualTo("");
    }

    @Test
    void prefersOnlinePlayerVarOverGlobalVar() {
        Player player = mock(Player.class);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.isOnline()).thenReturn(true);
        when(offline.getPlayer()).thenReturn(player);

        addon.getStorage().setGlobalVar("score", "global-value");
        addon.getStorage().setVar(player, "score", "player-value");

        String result = expansion.onRequest(offline, "TestAddon_score");

        assertThat(result).isEqualTo("player-value");
    }

    @Test
    void fallsBackToGlobalVarWhenOfflinePlayerHasNoPersonalValue() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.isOnline()).thenReturn(true);
        when(offline.getPlayer()).thenReturn(player);

        addon.getStorage().setGlobalVar("score", "global-value");

        String result = expansion.onRequest(offline, "TestAddon_score");

        assertThat(result).isEqualTo("global-value");
    }
}

// by t.me/NanoDev_mc
