package ru.nanodev.nanoforge.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Общая сборка "ItemStack с покрашенным именем/лором" - раньше эта логика
 * (создать stack, взять meta, перевести &-коды в имени и в каждой строке
 * лора, setItemMeta обратно) жила только внутри MenuManager.buildItem() и
 * писалась бы ЕЩЁ РАЗ для GUI-визарда создания аддонов. Теперь один метод.
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    public static ItemStack build(Material material, int amount, String coloredName, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (coloredName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', coloredName));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack build(Material material, String coloredName, String... lore) {
        return build(material, 1, coloredName, lore.length == 0 ? Collections.emptyList() : java.util.Arrays.asList(lore));
    }
}

// by t.me/NanoDev_mc
