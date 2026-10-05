package ru.nanodev.nanoforge.wizard;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AddonWizardTest {

    private MockedStatic<Bukkit> bukkitMock;
    private NanoForgePlugin plugin;
    private AddonManager addonManager;
    private AddonWizard wizard;
    private Player player;
    private PluginManager pluginManager;
    private Inventory createdInventory;
    private ItemMeta meta;

    @BeforeEach
    void setUp() {
        plugin = mock(NanoForgePlugin.class);
        when(plugin.getName()).thenReturn("NanoForge");
        addonManager = mock(AddonManager.class);
        wizard = new AddonWizard(plugin, addonManager);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);

        meta = mock(ItemMeta.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        lenient().when(itemFactory.getItemMeta(any())).thenReturn(meta);
        lenient().when(server.getItemFactory()).thenReturn(itemFactory);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(itemFactory);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkitMock.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));

        bukkitMock.when(() -> Bukkit.createInventory(any(WizardHolder.class), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    createdInventory = mock(Inventory.class);
                    WizardHolder holder = invocation.getArgument(0);
                    lenient().when(createdInventory.getHolder()).thenReturn(holder);
                    return createdInventory;
                });
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    private Plugin fakePlugin(String name, String version) {
        Plugin p = mock(Plugin.class);
        when(p.getName()).thenReturn(name);
        when(p.isEnabled()).thenReturn(true);
        PluginDescriptionFile desc = mock(PluginDescriptionFile.class);
        lenient().when(desc.getVersion()).thenReturn(version);
        lenient().when(p.getDescription()).thenReturn(desc);
        return p;
    }

    @Test
    void startOpensTypeChoiceMenu() {
        wizard.start(player);
        verify(player).openInventory(createdInventory);
    }

    @Test
    void clickingMiniPluginPromptsForNameInChat() {
        wizard.start(player);

        InventoryClickEvent event = clickEvent(11);
        wizard.onClick(event);

        verify(player).closeInventory();
        verify(player).sendMessage(contains("Мини-плагин"));
    }

    @Test
    void clickingCancelInTypeChoiceDoesNotPromptForName() {
        wizard.start(player);

        InventoryClickEvent event = clickEvent(22);
        wizard.onClick(event);

        // после отмены сообщение в чат НЕ должно перехватываться визардом
        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("просто сообщение");
        wizard.onChat(chat);
        verify(chat, never()).setCancelled(true);
    }

    @Test
    void miniPluginFlowCreatesAddonViaCreateNew() {
        Addon created = mock(Addon.class);
        when(created.getName()).thenReturn("MyPlugin");
        when(addonManager.createNew("MyPlugin")).thenReturn(created);
        when(addonManager.get("MyPlugin")).thenReturn(null); // имя ещё не занято

        wizard.start(player);
        wizard.onClick(clickEvent(11)); // "Мини-плагин"

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("MyPlugin");
        wizard.onChat(chat);

        verify(chat).setCancelled(true);
        verify(addonManager).createNew("MyPlugin");
        verify(addonManager, never()).createAddon(anyString(), anyString());
        verify(player).sendMessage(contains("Создано"));
    }

    @Test
    void pluginChoiceFlowCreatesAddonViaCreateAddon() {
        Plugin fake = fakePlugin("CachesManager", "1.6");
        when(pluginManager.getPlugins()).thenReturn(new Plugin[]{fake});

        Addon created = mock(Addon.class);
        when(created.getName()).thenReturn("MyAddon");
        when(addonManager.createAddon("CachesManager", "MyAddon")).thenReturn(created);
        when(addonManager.get("MyAddon")).thenReturn(null);

        wizard.start(player);
        wizard.onClick(clickEvent(15)); // "Аддон к плагину" -> открывает список

        // симулируем клик по первому (единственному) плагину в списке
        when(meta.getDisplayName()).thenReturn("§eCachesManager");
        InventoryClickEvent pluginClick = clickEvent(0);
        wizard.onClick(pluginClick);

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("MyAddon");
        wizard.onChat(chat);

        verify(addonManager).createAddon("CachesManager", "MyAddon");
    }

    @Test
    void rejectsNameWithSpaces() {
        when(addonManager.get(anyString())).thenReturn(null);

        wizard.start(player);
        wizard.onClick(clickEvent(11));

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("имя с пробелом");
        wizard.onChat(chat);

        verify(addonManager, never()).createNew(anyString());
        verify(player).sendMessage(contains("✖"));
    }

    @Test
    void rejectsNameThatAlreadyExists() {
        Addon existing = mock(Addon.class);
        when(addonManager.get("Existing")).thenReturn(existing);

        wizard.start(player);
        wizard.onClick(clickEvent(11));

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("Existing");
        wizard.onChat(chat);

        verify(addonManager, never()).createNew("Existing");
    }

    @Test
    void cancelKeywordAbortsWithoutCreatingAnything() {
        wizard.start(player);
        wizard.onClick(clickEvent(11));

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(chat.getMessage()).thenReturn("cancel");
        wizard.onChat(chat);

        verify(addonManager, never()).createNew(anyString());
        verify(addonManager, never()).createAddon(anyString(), anyString());
    }

    @Test
    void clickOutsideWizardInventoryIsIgnored() {
        Inventory plainInventory = mock(Inventory.class);
        lenient().when(plainInventory.getHolder()).thenReturn(null);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(plainInventory);

        wizard.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    private InventoryClickEvent clickEvent(int slot) {
        ItemStack item = mock(ItemStack.class);
        lenient().when(item.getType()).thenReturn(org.bukkit.Material.STONE);
        lenient().when(item.getItemMeta()).thenReturn(meta);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(createdInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getSlot()).thenReturn(slot);
        when(event.getCurrentItem()).thenReturn(item);
        return event;
    }

    private static String contains(String s) {
        return org.mockito.ArgumentMatchers.contains(s);
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}

// by t.me/NanoDev_mc
