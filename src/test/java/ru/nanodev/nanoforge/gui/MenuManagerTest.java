package ru.nanodev.nanoforge.gui;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Открытие меню и обработку кликов проверяем без реального сервера: Bukkit
 * статически мокается (как и в остальных тестах проекта), Addon строится на
 * НАСТОЯЩЕМ in-memory YamlConfiguration (без чтения/записи файла) - этого
 * достаточно, т.к. MenuManager читает меню только через ConfigurationSection.
 */
class MenuManagerTest {

    private MockedStatic<Bukkit> bukkitMock;
    private NanoForgePlugin plugin;
    private AddonManager addonManager;
    private MenuManager menuManager;
    private Player player;
    private Inventory createdInventory;

    @BeforeEach
    void setUp() {
        plugin = mock(NanoForgePlugin.class);
        addonManager = mock(AddonManager.class);
        player = mock(Player.class);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        ItemMeta meta = mock(ItemMeta.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        // buildItem() дёргает stack.getItemMeta() -> Bukkit.getItemFactory().getItemMeta(material)
        lenient().when(itemFactory.getItemMeta(any())).thenReturn(meta);
        lenient().when(server.getItemFactory()).thenReturn(itemFactory);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        lenient().when(server.getLogger()).thenReturn(Logger.getLogger("test"));

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(itemFactory);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkitMock.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));

        // MenuManager сам создаёт Inventory через Bukkit.createInventory(holder, size, title) -
        // возвращаем настоящий Mockito-мок Inventory и запоминаем его для проверок.
        bukkitMock.when(() -> Bukkit.createInventory(any(NanoMenuHolder.class), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    createdInventory = mock(Inventory.class);
                    NanoMenuHolder holder = invocation.getArgument(0);
                    int size = invocation.getArgument(1);
                    lenient().when(createdInventory.getHolder()).thenReturn(holder);
                    lenient().when(createdInventory.getSize()).thenReturn(size);
                    return createdInventory;
                });

        menuManager = new MenuManager(plugin, addonManager);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private Addon buildAddonWithMenu() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "TestAddon");
        yaml.set("type", "addon");
        yaml.set("target", "SomePlugin");
        yaml.set("enabled", true);
        yaml.set("menus.main.title", "&aТестовое меню");
        yaml.set("menus.main.rows", 1);

        yaml.set("menus.main.items.2.material", "DIAMOND");
        yaml.set("menus.main.items.2.name", "&eНажми меня");
        Map<String, Object> msgAction = new LinkedHashMap<>();
        msgAction.put("type", "message");
        msgAction.put("text", "привет");
        yaml.set("menus.main.items.2.actions", Collections.singletonList(msgAction));

        yaml.set("menus.main.items.4.material", "BARRIER");
        yaml.set("menus.main.items.4.if.permission", "nano.vip");

        return new Addon(new File("/tmp/nanoforge-menu-manager-test/addon.yml"), yaml);
    }

    @Test
    void openBuildsInventoryWithVisibleItemAndOpensToPlayer() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(player.hasPermission("nano.vip")).thenReturn(false);

        boolean result = menuManager.open(player, "TestAddon", "main");

        assertThat(result).isTrue();
        verify(player).openInventory(createdInventory);
        verify(createdInventory).setItem(eq(2), any(ItemStack.class));
    }

    @Test
    void openSkipsItemFailingPermissionCondition() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(player.hasPermission("nano.vip")).thenReturn(false);

        menuManager.open(player, "TestAddon", "main");

        verify(createdInventory, never()).setItem(eq(4), any(ItemStack.class));
    }

    @Test
    void openShowsItemWhenPermissionConditionPasses() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(player.hasPermission("nano.vip")).thenReturn(true);

        menuManager.open(player, "TestAddon", "main");

        verify(createdInventory).setItem(eq(4), any(ItemStack.class));
    }

    @Test
    void openReturnsFalseAndMessagesWhenAddonMissing() {
        when(addonManager.get("Ghost")).thenReturn(null);

        boolean result = menuManager.open(player, "Ghost", "main");

        assertThat(result).isFalse();
        verify(player).sendMessage(contains("Ghost"));
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void openReturnsFalseAndMessagesWhenMenuMissing() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);

        boolean result = menuManager.open(player, "TestAddon", "no_such_menu");

        assertThat(result).isFalse();
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void clickOnVisibleItemCancelsEventAndRunsItsActions() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(addonManager.get("TestAddon")).thenReturn(addon); // isEnabled() читается из того же addon
        when(player.hasPermission(anyString())).thenReturn(true);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(clickedInv);
        when(event.getClickedInventory()).thenReturn(clickedInv);
        when(event.getSlot()).thenReturn(2);
        when(event.getWhoClicked()).thenReturn(player);

        menuManager.onClick(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage("привет");
    }

    @Test
    void clickOnEmptySlotDoesNothingAndDoesNotThrow() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(clickedInv);
        when(event.getClickedInventory()).thenReturn(clickedInv);
        when(event.getSlot()).thenReturn(7); // пустой слот - в addon.yml ничего не задано
        when(event.getWhoClicked()).thenReturn(player);

        menuManager.onClick(event);

        verify(event).setCancelled(true);
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    void clickIsIgnoredWhenInventoryIsNotANanoMenu() {
        Inventory plainInventory = mock(Inventory.class);
        lenient().when(plainInventory.getHolder()).thenReturn(null);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(plainInventory);

        menuManager.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void clickOnHiddenItemDoesNotRunActionsEvenIfClicked() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(player.hasPermission("nano.vip")).thenReturn(false);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(clickedInv);
        when(event.getClickedInventory()).thenReturn(clickedInv);
        when(event.getSlot()).thenReturn(4); // BARRIER, скрыт без nano.vip
        when(event.getWhoClicked()).thenReturn(player);

        menuManager.onClick(event);

        verify(event).setCancelled(true);
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    void shiftClickFromPlayerInventoryTracksLandedSlotIntoYaml() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        holder.setEditMode(true);

        ItemStack[] beforeContents = new ItemStack[9];
        ItemStack[] afterContents = new ItemStack[9];
        afterContents[6] = new ItemStack(org.bukkit.Material.GOLD_INGOT, 5);

        Inventory topInv = mock(Inventory.class);
        when(topInv.getHolder()).thenReturn(holder);
        when(topInv.getContents()).thenReturn(beforeContents, afterContents);
        when(topInv.getItem(6)).thenReturn(afterContents[6]);
        holder.setInventory(topInv);

        org.bukkit.inventory.PlayerInventory playerInv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInv);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(topInv);
        when(event.getClickedInventory()).thenReturn(playerInv);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        when(event.getWhoClicked()).thenReturn(player);

        menuManager.onClick(event);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), taskCaptor.capture());
        taskCaptor.getValue().run(); // имитируем следующий тик

        assertThat(addon.getYaml().getString("menus.main.items.6.material")).isEqualTo("GOLD_INGOT");
        assertThat(addon.getYaml().getInt("menus.main.items.6.amount")).isEqualTo(5);
    }

    @Test
    void normalClickFromPlayerInventoryWithoutShiftIsNotTrackedAsSync() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        holder.setEditMode(true);
        Inventory topInv = mock(Inventory.class);
        when(topInv.getHolder()).thenReturn(holder);
        holder.setInventory(topInv);

        org.bukkit.inventory.PlayerInventory playerInv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInv);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(topInv);
        when(event.getClickedInventory()).thenReturn(playerInv);
        when(event.getClick()).thenReturn(ClickType.LEFT); // не shift
        when(event.getWhoClicked()).thenReturn(player);

        menuManager.onClick(event);

        verify(scheduler, never()).runTask(any(), any(Runnable.class));
    }

    @Test
    void multiLineChatDslAccumulatesMultipleActionsUntilDone() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        when(player.hasPermission(anyString())).thenReturn(true);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        holder.setEditMode(true);
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        // shift-клик по слоту 7 (пустой пункт в тестовом меню) - входим в режим правки
        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getInventory()).thenReturn(clickedInv);
        when(clickEvent.getClickedInventory()).thenReturn(clickedInv);
        when(clickEvent.getSlot()).thenReturn(7);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        menuManager.onClick(clickEvent);
        verify(player).closeInventory();

        runChatLine(player, uuid, scheduler, "message первая строка");
        runChatLine(player, uuid, scheduler, "broadcast вторая строка");
        runChatLine(player, uuid, scheduler, "done");

        List<?> savedActions = addon.getYaml().getList("menus.main.items.7.actions");
        assertThat(savedActions).hasSize(2);
        assertThat(((Map<?, ?>) savedActions.get(0)).get("type")).isEqualTo("message");
        assertThat(((Map<?, ?>) savedActions.get(1)).get("type")).isEqualTo("broadcast");
    }

    @Test
    void undoRemovesLastLineWithoutEndingSession() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        holder.setEditMode(true);
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getInventory()).thenReturn(clickedInv);
        when(clickEvent.getClickedInventory()).thenReturn(clickedInv);
        when(clickEvent.getSlot()).thenReturn(7);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        menuManager.onClick(clickEvent);

        runChatLine(player, uuid, scheduler, "message оставить");
        runChatLine(player, uuid, scheduler, "message убрать undo-ом");
        runChatLine(player, uuid, scheduler, "undo");
        runChatLine(player, uuid, scheduler, "done");

        List<?> savedActions = addon.getYaml().getList("menus.main.items.7.actions");
        assertThat(savedActions).hasSize(1);
        assertThat(((Map<?, ?>) savedActions.get(0)).get("text")).isEqualTo("оставить");
    }

    @Test
    void cancelDiscardsEverythingWithoutSaving() {
        Addon addon = buildAddonWithMenu();
        when(addonManager.get("TestAddon")).thenReturn(addon);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        NanoMenuHolder holder = new NanoMenuHolder("TestAddon", "main");
        holder.setEditMode(true);
        Inventory clickedInv = mock(Inventory.class);
        lenient().when(clickedInv.getHolder()).thenReturn(holder);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getInventory()).thenReturn(clickedInv);
        when(clickEvent.getClickedInventory()).thenReturn(clickedInv);
        when(clickEvent.getSlot()).thenReturn(7);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        menuManager.onClick(clickEvent);

        runChatLine(player, uuid, scheduler, "message пропадёт");
        runChatLine(player, uuid, scheduler, "cancel");

        assertThat(addon.getYaml().contains("menus.main.items.7.actions")).isFalse();

        // после cancel сессия закрыта - следующая обычная строка в чат уже не перехватывается
        AsyncPlayerChatEvent afterCancel = mock(AsyncPlayerChatEvent.class);
        when(afterCancel.getPlayer()).thenReturn(player);
        when(afterCancel.getMessage()).thenReturn("просто обычное сообщение в чат");
        menuManager.onChat(afterCancel);
        verify(afterCancel, never()).setCancelled(true);
    }

    /** Прогоняет одну строку DSL-сессии через onChat + выполняет запланированную runTask-задачу. */
    private void runChatLine(Player player, java.util.UUID uuid, BukkitScheduler scheduler, String line) {
        AsyncPlayerChatEvent chatEvent = mock(AsyncPlayerChatEvent.class);
        when(chatEvent.getPlayer()).thenReturn(player);
        when(chatEvent.getMessage()).thenReturn(line);

        menuManager.onChat(chatEvent);
        verify(chatEvent).setCancelled(true);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).runTask(eq(plugin), captor.capture());
        captor.getValue().run();
    }

    private static String contains(String substring) {
        return org.mockito.ArgumentMatchers.contains(substring);
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}

// by t.me/NanoDev_mc
