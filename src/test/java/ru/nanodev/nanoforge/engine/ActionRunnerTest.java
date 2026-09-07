package ru.nanodev.nanoforge.engine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}

// by t.me/NanoDev_mc
