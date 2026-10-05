package ru.nanodev.nanoforge.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ItemBuilderTest {

    private MockedStatic<Bukkit> bukkitMock;
    private ItemMeta meta;

    @BeforeEach
    void setUp() {
        meta = mock(ItemMeta.class);
        ItemFactory factory = mock(ItemFactory.class);
        when(factory.getItemMeta(any())).thenReturn(meta);
        Server server = mock(Server.class);
        when(server.getItemFactory()).thenReturn(factory);

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(factory);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void buildSetsMaterialAndAmount() {
        ItemStack stack = ItemBuilder.build(Material.DIAMOND, 5, null, Collections.emptyList());
        assertThat(stack.getType()).isEqualTo(Material.DIAMOND);
        assertThat(stack.getAmount()).isEqualTo(5);
    }

    @Test
    void amountBelowOneIsClampedToOne() {
        ItemStack stack = ItemBuilder.build(Material.STONE, 0, null, Collections.emptyList());
        assertThat(stack.getAmount()).isEqualTo(1);
    }

    @Test
    void colorCodesInNameAreTranslated() {
        ItemBuilder.build(Material.DIAMOND, 1, "&aПривет", Collections.emptyList());
        verify(meta).setDisplayName("§aПривет");
    }

    @Test
    void colorCodesInLoreAreTranslated() {
        ItemBuilder.build(Material.DIAMOND, 1, null, Arrays.asList("&7строка 1", "&cстрока 2"));
        verify(meta).setLore(Arrays.asList("§7строка 1", "§cстрока 2"));
    }

    @Test
    void nullNameDoesNotCallSetDisplayName() {
        ItemBuilder.build(Material.DIAMOND, 1, null, Collections.emptyList());
        verify(meta, never()).setDisplayName(anyString());
    }

    @Test
    void emptyLoreDoesNotCallSetLore() {
        ItemBuilder.build(Material.DIAMOND, 1, "&aИмя", Collections.emptyList());
        verify(meta, never()).setLore(any());
    }

    @Test
    void varargsOverloadWorks() {
        ItemStack stack = ItemBuilder.build(Material.EMERALD, "&bИмя", "&7строка");
        assertThat(stack.getType()).isEqualTo(Material.EMERALD);
        assertThat(stack.getAmount()).isEqualTo(1);
        verify(meta).setDisplayName("§bИмя");
        verify(meta).setLore(Collections.singletonList("§7строка"));
    }
}

// by t.me/NanoDev_mc
