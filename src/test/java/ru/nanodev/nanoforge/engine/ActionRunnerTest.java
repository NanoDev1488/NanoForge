package ru.nanodev.nanoforge.engine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Проверяет type: give_item и подстановку {argN}/{args} внутри ActionRunner.
 * ItemStack.getItemMeta() внутри Bukkit идёт через Bukkit.getItemFactory() -
 * поэтому Bukkit тут статически мокается (как и в остальных тестах проекта).
 */
class ActionRunnerTest {

    private MockedStatic<Bukkit> bukkitMock;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        ru.nanodev.nanoforge.util.ConsoleRateLimiter.resetForTests();
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        lenient().when(player.getInventory()).thenReturn(inventory);

        Server server = mock(Server.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        ItemMeta meta = mock(ItemMeta.class);
        lenient().when(itemFactory.getItemMeta(any(Material.class))).thenReturn(meta);
        lenient().when(server.getItemFactory()).thenReturn(itemFactory);

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(itemFactory);
        bukkitMock.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    private Map<String, Object> action(String type) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        return a;
    }

    @Test
    void giveItemAddsStackToPlayerInventory() {
        Map<String, Object> a = action("give_item");
        a.put("material", "DIAMOND");
        a.put("amount", 5);

        ActionRunner.run(Collections.singletonList(a), player, null);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(Material.DIAMOND);
        assertThat(captor.getValue().getAmount()).isEqualTo(5);
    }

    @Test
    void giveItemDefaultsToOneWhenAmountMissing() {
        Map<String, Object> a = action("give_item");
        a.put("material", "STONE");

        ActionRunner.run(Collections.singletonList(a), player, null);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
    }

    @Test
    void giveItemWithUnknownMaterialDoesNotThrowOrAddAnything() {
        Map<String, Object> a = action("give_item");
        a.put("material", "NOT_A_REAL_MATERIAL");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void giveItemWithNullPlayerIsNoOpAndDoesNotThrow() {
        Map<String, Object> a = action("give_item");
        a.put("material", "STONE");

        ActionRunner.run(Collections.singletonList(a), null, null);
        // если дошли сюда без исключения - тест прошёл
    }

    @Test
    void giveItemSubstitutesPlaceholdersInMaterialAndAmount() {
        Map<String, Object> a = action("give_item");
        a.put("material", "{arg1}");
        a.put("amount", "{arg2}");

        ActionRunner.run(Collections.singletonList(a), player, null, null, null, new String[]{"GOLD_INGOT", "7"});

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(captor.getValue().getAmount()).isEqualTo(7);
    }

    @Test
    void messageActionSubstitutesCommandArgs() {
        Map<String, Object> a = action("message");
        a.put("text", "arg was: {arg1}");

        ActionRunner.run(Collections.singletonList(a), player, null, null, null, new String[]{"hello"});

        verify(player).sendMessage("arg was: hello");
    }

    @Test
    void messageActionSubstitutesJoinedArgs() {
        Map<String, Object> a = action("message");
        a.put("text", "все аргументы: {args}");

        ActionRunner.run(Collections.singletonList(a), player, null, null, null, new String[]{"x", "y", "z"});

        verify(player).sendMessage("все аргументы: x y z");
    }

    @Test
    void particleSpawnsNamedParticleAtPlayerLocation() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 1, 2, 3);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Object> a = action("particle");
        a.put("particle", "heart");
        a.put("count", 5);
        a.put("offset_x", 0.1);
        a.put("offset_y", 0.2);
        a.put("offset_z", 0.3);
        a.put("extra", 0.0);

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).spawnParticle(org.bukkit.Particle.HEART, loc, 5, 0.1, 0.2, 0.3, 0.0);
    }

    @Test
    void particleWithUnknownNameLogsWarningAndDoesNotThrow() {
        when(player.getLocation()).thenReturn(new org.bukkit.Location(null, 0, 0, 0));

        Map<String, Object> a = action("particle");
        a.put("particle", "NOT_A_REAL_PARTICLE");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player, never()).spawnParticle(any(org.bukkit.Particle.class), any(org.bukkit.Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void particleUsesDefaultsWhenFieldsOmitted() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 0, 0, 0);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Object> a = action("particle");
        // без "particle" вообще - должен по умолчанию взять FLAME

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).spawnParticle(org.bukkit.Particle.FLAME, loc, 10, 0.5, 0.5, 0.5, 0.0);
    }

    @Test
    void playSoundPlaysNamedSoundAtPlayerLocation() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 1, 2, 3);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Object> a = action("play_sound");
        a.put("sound", "entity_player_levelup");
        a.put("volume", 0.5);
        a.put("pitch", 2.0);

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
    }

    @Test
    void playSoundWithUnknownNameLogsWarningAndDoesNotThrow() {
        when(player.getLocation()).thenReturn(new org.bukkit.Location(null, 0, 0, 0));

        Map<String, Object> a = action("play_sound");
        a.put("sound", "NOT_A_REAL_SOUND");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player, never()).playSound(any(org.bukkit.Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
    }

    @Test
    void teleportMovesPlayerToGivenCoordinatesInCurrentWorld() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));

        Map<String, Object> a = action("teleport");
        a.put("x", 100.5);
        a.put("y", 64.0);
        a.put("z", -30.0);

        ActionRunner.run(Collections.singletonList(a), player, null);

        ArgumentCaptor<org.bukkit.Location> captor = ArgumentCaptor.forClass(org.bukkit.Location.class);
        verify(player).teleport(captor.capture());
        assertThat(captor.getValue().getWorld()).isEqualTo(world);
        assertThat(captor.getValue().getX()).isEqualTo(100.5);
        assertThat(captor.getValue().getY()).isEqualTo(64.0);
        assertThat(captor.getValue().getZ()).isEqualTo(-30.0);
    }

    @Test
    void teleportToNamedWorldLooksItUpViaBukkit() {
        org.bukkit.World currentWorld = mock(org.bukkit.World.class);
        org.bukkit.World targetWorld = mock(org.bukkit.World.class);
        when(player.getWorld()).thenReturn(currentWorld);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(currentWorld, 0, 0, 0));
        bukkitMock.when(() -> Bukkit.getWorld("world_nether")).thenReturn(targetWorld);

        Map<String, Object> a = action("teleport");
        a.put("world", "world_nether");
        a.put("x", 1);
        a.put("y", 2);
        a.put("z", 3);

        ActionRunner.run(Collections.singletonList(a), player, null);

        ArgumentCaptor<org.bukkit.Location> captor = ArgumentCaptor.forClass(org.bukkit.Location.class);
        verify(player).teleport(captor.capture());
        assertThat(captor.getValue().getWorld()).isEqualTo(targetWorld);
    }

    @Test
    void teleportToUnknownWorldLogsWarningAndDoesNotTeleport() {
        org.bukkit.World currentWorld = mock(org.bukkit.World.class);
        when(player.getWorld()).thenReturn(currentWorld);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(currentWorld, 0, 0, 0));
        bukkitMock.when(() -> Bukkit.getWorld("no_such_world")).thenReturn(null);

        Map<String, Object> a = action("teleport");
        a.put("world", "no_such_world");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    @Test
    void titleActionSendsTitleAndSubtitleWithConfiguredTiming() {
        Map<String, Object> a = action("title");
        a.put("title", "&aПривет");
        a.put("subtitle", "&7подзаголовок");
        a.put("fadein", 5);
        a.put("stay", 40);
        a.put("fadeout", 15);

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).sendTitle("§aПривет", "§7подзаголовок", 5, 40, 15);
    }

    @Test
    void titleActionOnlyActionbarDoesNotSendEmptyTitle() {
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        Map<String, Object> a = action("title");
        a.put("actionbar", "&eНа полосе действий");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
        ArgumentCaptor<net.md_5.bungee.api.chat.BaseComponent> captor =
                ArgumentCaptor.forClass(net.md_5.bungee.api.chat.BaseComponent.class);
        verify(spigot).sendMessage(eq(net.md_5.bungee.api.ChatMessageType.ACTION_BAR), captor.capture());
        assertThat(((net.md_5.bungee.api.chat.TextComponent) captor.getValue()).getText())
                .isEqualTo("§eНа полосе действий");
    }

    @Test
    void titleActionSubstitutesResultPlaceholderInActionbar() {
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        Map<String, Object> call = action("call");
        call.put("plugin", "AnyPlugin");
        call.put("method", "anyMethod");
        Map<String, Object> titleAction = action("title");
        titleAction.put("actionbar", "результат: {result}");

        bukkitMock.when(() -> Bukkit.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));

        ActionRunner.run(Arrays.asList(call, titleAction), player, null);

        ArgumentCaptor<net.md_5.bungee.api.chat.BaseComponent> captor =
                ArgumentCaptor.forClass(net.md_5.bungee.api.chat.BaseComponent.class);
        verify(spigot).sendMessage(eq(net.md_5.bungee.api.ChatMessageType.ACTION_BAR), captor.capture());
        assertThat(((net.md_5.bungee.api.chat.TextComponent) captor.getValue()).getText())
                .isEqualTo("результат: null");
    }
    @Test
    void delayActionSchedulesNestedActionsAndDoesNotRunThemImmediately() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        Map<String, Object> nestedMessage = action("message");
        nestedMessage.put("text", "отложенное сообщение");

        Map<String, Object> delay = action("delay");
        delay.put("ticks", 40);
        delay.put("actions", Collections.singletonList(nestedMessage));

        ActionRunner.run(Collections.singletonList(delay), player, null);

        // ничего не должно выполниться немедленно - только запланироваться
        verify(player, never()).sendMessage(anyString());

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(), taskCaptor.capture(), eq(40L));

        // сам факт выполнения запланированной задачи планировщиком здесь не тестируем
        // (это ответственность Bukkit) - но проверяем, что ЗАПУСК этой самой задачи
        // действительно прогоняет вложенные actions
        taskCaptor.getValue().run();
        verify(player).sendMessage("отложенное сообщение");
    }

    @Test
    void delayActionWithoutNestedActionsDoesNothing() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        Map<String, Object> delay = action("delay");
        delay.put("ticks", 20);

        ActionRunner.run(Collections.singletonList(delay), player, null);

        verify(scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
    }

    @Test
    void consoleActionDispatchesSingleCommand() {
        org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        bukkitMock.when(Bukkit::getConsoleSender).thenReturn(console);

        Map<String, Object> a = action("console");
        a.put("command", "say hi");

        ActionRunner.run(Collections.singletonList(a), player, null);

        bukkitMock.verify(() -> Bukkit.dispatchCommand(console, "say hi"));
    }

    @Test
    void consoleActionWithCommandsListDispatchesAllInOrder() {
        org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        bukkitMock.when(Bukkit::getConsoleSender).thenReturn(console);

        Map<String, Object> a = action("console");
        a.put("commands", Arrays.asList("say first", "say second"));

        ActionRunner.run(Collections.singletonList(a), player, null);

        bukkitMock.verify(() -> Bukkit.dispatchCommand(console, "say first"));
        bukkitMock.verify(() -> Bukkit.dispatchCommand(console, "say second"));
    }

    @Test
    void consoleActionStopsDispatchingOnceRateLimitExceeded() {
        org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        bukkitMock.when(Bukkit::getConsoleSender).thenReturn(console);

        List<String> manyCommands = new java.util.ArrayList<>();
        for (int i = 0; i < ru.nanodev.nanoforge.util.ConsoleRateLimiter.MAX_PER_SECOND + 10; i++) {
            manyCommands.add("say " + i);
        }
        Map<String, Object> a = action("console");
        a.put("commands", manyCommands);

        ActionRunner.run(Collections.singletonList(a), player, null);

        bukkitMock.verify(() -> Bukkit.dispatchCommand(eq(console), anyString()),
                times(ru.nanodev.nanoforge.util.ConsoleRateLimiter.MAX_PER_SECOND));
    }

    @Test
    void randomActionAlwaysRunsExactlyOneOfTheGivenActions() {
        Map<String, Object> a = action("message");
        a.put("text", "A");
        Map<String, Object> b = action("message");
        b.put("text", "B");

        Map<String, Object> random = action("random");
        random.put("actions", Arrays.asList(a, b));

        ActionRunner.run(Collections.singletonList(random), player, null);

        verify(player, times(1)).sendMessage(anyString());
    }

    @Test
    void randomActionWithZeroWeightNeverPicksThatChoice() {
        Map<String, Object> never = action("message");
        never.put("text", "NEVER");
        never.put("weight", 0);
        Map<String, Object> always = action("message");
        always.put("text", "ALWAYS");
        always.put("weight", 1);

        Map<String, Object> random = action("random");
        random.put("actions", Arrays.asList(never, always));

        for (int i = 0; i < 20; i++) {
            ActionRunner.run(Collections.singletonList(random), player, null);
        }

        verify(player, never()).sendMessage("NEVER");
        verify(player, times(20)).sendMessage("ALWAYS");
    }

    @Test
    void soundStopWithNamedSoundStopsOnlyThatSound() {
        Map<String, Object> a = action("sound_stop");
        a.put("sound", "entity_player_levelup");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).stopSound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP);
    }

    @Test
    void soundStopWithoutSoundFieldStopsAllSounds() {
        Map<String, Object> a = action("sound_stop");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player).stopAllSounds();
    }

    @Test
    void soundStopWithUnknownNameLogsWarningAndDoesNotThrow() {
        Map<String, Object> a = action("sound_stop");
        a.put("sound", "NOT_A_REAL_SOUND");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(player, never()).stopSound(any(org.bukkit.Sound.class));
        verify(player, never()).stopAllSounds();
    }

    @Test
    void discordWebhookSchedulesAsyncTaskWhenUrlProvided() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        Map<String, Object> a = action("discord_webhook");
        a.put("url", "https://discord.com/api/webhooks/fake/fake");
        a.put("content", "привет из теста");

        ActionRunner.run(Collections.singletonList(a), player, null);

        // саму задачу НЕ выполняем (это был бы реальный сетевой запрос) - только
        // проверяем, что она вообще запланирована асинхронно, а не выполнена
        // синхронно в потоке вызова (что заблокировало бы сервер при недоступной сети).
        verify(scheduler).runTaskAsynchronously(any(), any(Runnable.class));
    }

    @Test
    void discordWebhookDoesNothingWhenUrlMissing() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);

        Map<String, Object> a = action("discord_webhook");
        a.put("content", "без урла");

        ActionRunner.run(Collections.singletonList(a), player, null);

        verify(scheduler, never()).runTaskAsynchronously(any(), any(Runnable.class));
    }

}

// by t.me/NanoDev_mc
