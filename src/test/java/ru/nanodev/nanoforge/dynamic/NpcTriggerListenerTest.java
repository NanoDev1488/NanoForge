package ru.nanodev.nanoforge.dynamic;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import ru.nanodev.nanoforge.integration.CitizensBridge;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class NpcTriggerListenerTest {

    @TempDir
    File tempDir;

    private AddonManager addonManager;
    private NpcTriggerListener listener;
    private Player player;
    private Entity entity;
    private MockedStatic<CitizensBridge> citizensMock;

    @BeforeEach
    void setUp() {
        addonManager = mock(AddonManager.class);
        listener = new NpcTriggerListener(addonManager);
        player = mock(Player.class);
        entity = mock(Entity.class);
        citizensMock = mockStatic(CitizensBridge.class);
    }

    @AfterEach
    void tearDown() {
        citizensMock.close();
    }

    private Addon buildAddonWithNpc(int npcId) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "NpcAddon");
        yaml.set("type", "new");
        yaml.set("npcs.greeter.npc_id", npcId);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "message");
        msg.put("text", "npc says hi");
        yaml.set("npcs.greeter.actions", Collections.singletonList(msg));
        return new Addon(new File(tempDir, "addon.yml"), yaml);
    }

    @Test
    void doesNothingWhenClickedEntityIsNotAnNpc() {
        citizensMock.when(() -> CitizensBridge.getNpcId(entity)).thenReturn(null);

        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getRightClicked()).thenReturn(entity);

        listener.onInteract(event);

        verifyNoInteractions(addonManager);
    }

    @Test
    void runsMatchingNpcActionsWhenIdMatches() {
        Addon addon = buildAddonWithNpc(5);
        when(addonManager.getAddons()).thenReturn(Collections.singletonList(addon));
        citizensMock.when(() -> CitizensBridge.getNpcId(entity)).thenReturn(5);

        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getRightClicked()).thenReturn(entity);
        when(event.getPlayer()).thenReturn(player);

        listener.onInteract(event);

        verify(player).sendMessage("npc says hi");
    }

    @Test
    void doesNotRunActionsWhenNpcIdDoesNotMatch() {
        Addon addon = buildAddonWithNpc(5);
        when(addonManager.getAddons()).thenReturn(Collections.singletonList(addon));
        citizensMock.when(() -> CitizensBridge.getNpcId(entity)).thenReturn(99);

        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getRightClicked()).thenReturn(entity);
        when(event.getPlayer()).thenReturn(player);

        listener.onInteract(event);

        verify(player, never()).sendMessage(anyString());
    }
}

// by t.me/NanoDev_mc
