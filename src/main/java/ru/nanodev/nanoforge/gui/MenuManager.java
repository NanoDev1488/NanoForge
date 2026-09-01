package ru.nanodev.nanoforge.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.ActionLineParser;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.engine.ConditionChecker;
import ru.nanodev.nanoforge.engine.FancyFont;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Строит инвентарные меню из секции "menus:" в addon.yml и обрабатывает клики.
 * Меню могут открывать другие меню (action type: openmenu) - так собирается
 * любая глубина вложенности: от одной кнопки до огромного дерева подменю.
 *
 * Поддерживает два режима открытия:
 *  - обычный (open)     - клики выполняют actions, перетаскивание предметов запрещено
 *  - редактирования (openEdit) - перетаскивание предметов РАЗРЕШЕНО и сразу
 *    сохраняет внешний вид (материал/имя/лор/кол-во) итогового предмета слота
 *    обратно в addon.yml. Это и есть "редактор меню перетаскиванием предметов":
 *    ставишь любой предмет из инвентаря в слот - он становится иконкой кнопки.
 */
public class MenuManager implements Listener {

    private final NanoForgePlugin plugin;
    private final AddonManager addonManager;

    /** Кто сейчас вводит через чат текст action'а для конкретного слота меню (см. shift-клик в edit-режиме). */
    private final Map<UUID, PendingActionEdit> pendingEdits = new HashMap<>();

    private static class PendingActionEdit {
        final String addonName;
        final String menuKey;
        final int slot;

        PendingActionEdit(String addonName, String menuKey, int slot) {
            this.addonName = addonName;
            this.menuKey = menuKey;
            this.slot = slot;
        }
    }

    /** Короткая обёртка над FancyFont.stylize - для читаемости вызовов ниже. */
    private static String f(String text) {
        return FancyFont.stylize(text);
    }

    public MenuManager(NanoForgePlugin plugin, AddonManager addonManager) {
        this.plugin = plugin;
        this.addonManager = addonManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Открыть меню menuKey из аддона addonName для игрока (обычный режим). */
    public boolean open(Player player, String addonName, String menuKey) {
        return open(player, addonName, menuKey, false);
    }

    /** Открыть меню в режиме редактирования (перетаскивание предметов сохраняется в yaml). */
    public boolean openEdit(Player player, String addonName, String menuKey) {
        return open(player, addonName, menuKey, true);
    }

    private boolean open(Player player, String addonName, String menuKey, boolean editMode) {
        Addon addon = addonManager.get(addonName);
        if (addon == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f("Аддон не найден:") + " " + addonName);
            return false;
        }
        if (!addon.getMenuKeys().contains(menuKey)) {
            player.sendMessage(ChatColor.RED + "✖ " + f("Меню") + " '" + menuKey + "' " + f("не найдено у аддона") + " " + addonName);
            return false;
        }

        String title = ChatColor.translateAlternateColorCodes('&', addon.getMenuTitle(menuKey));
        if (editMode) title = ChatColor.LIGHT_PURPLE + "[Edit] " + ChatColor.RESET + title;
        int rows = addon.getMenuRows(menuKey);

        NanoMenuHolder holder = new NanoMenuHolder(addon.getName(), menuKey);
        holder.setEditMode(editMode);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        ConfigurationSection items = addon.getMenuItemsSection(menuKey);
        if (items != null) {
            for (String slotKey : items.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(slotKey);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (slot < 0 || slot >= inv.getSize()) continue;

                ConfigurationSection item = items.getConfigurationSection(slotKey);
                if (item == null) continue;

                // в обычном режиме пункт может быть скрыт по if: permission/world и т.д.
                // в режиме редактирования показываем ВСЁ, чтобы можно было отредактировать даже скрытые
                if (!editMode && !ConditionChecker.checkVisibility(item, player)) continue;

                inv.setItem(slot, buildItem(item));
            }
        }

        player.openInventory(inv);
        return true;
    }

    private ItemStack buildItem(ConfigurationSection cfg) {
        Material material;
        try {
            material = Material.valueOf(cfg.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material, Math.max(1, cfg.getInt("amount", 1)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = cfg.getString("name", null);
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            List<String> lore = cfg.getStringList("lore");
            if (!lore.isEmpty()) {
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NanoMenuHolder)) return;
        NanoMenuHolder holder = (NanoMenuHolder) event.getInventory().getHolder();

        if (holder.isEditMode()) {
            boolean clickedIsMenu = event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof NanoMenuHolder;

            // shift+клик (левый или правый) ПО САМОМУ МЕНЮ - правка ЛОГИКИ (actions) через чат.
            // shift+клик по своему инвентарю (снизу) должен работать как обычно - не трогаем его.
            if (clickedIsMenu && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
                event.setCancelled(true);
                startActionEdit((Player) event.getWhoClicked(), holder, event.getSlot());
                return;
            }
            // обычный клик/перетаскивание по меню - меняем ТОЛЬКО иконку (материал/имя/лор/кол-во)
            if (clickedIsMenu) {
                int slot = event.getSlot();
                Bukkit.getScheduler().runTask(plugin, () -> syncSlotToYaml(holder, slot));
            }
            return;
        }

        event.setCancelled(true); // в обычном режиме это меню, а не сундук - таскать нельзя

        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof NanoMenuHolder)) {
            return; // клик по инвентарю игрока снизу - игнорируем
        }

        Addon addon = addonManager.get(holder.getAddonName());
        if (addon == null || !addon.isEnabled()) return;

        ConfigurationSection items = addon.getMenuItemsSection(holder.getMenuKey());
        if (items == null) return;

        ConfigurationSection item = items.getConfigurationSection(String.valueOf(event.getSlot()));
        if (item == null) return;

        Player player = (Player) event.getWhoClicked();
        if (!ConditionChecker.checkVisibility(item, player)) return; // скрытый пункт - клик по пустому слоту

        List<?> actions = item.getList("actions", java.util.Collections.emptyList());
        try {
            ActionRunner.run(actions, player, null, this, addon);
        } catch (Throwable t) {
            player.sendMessage(org.bukkit.ChatColor.RED + "✖ " + f("Ошибка при выполнении кнопки меню."));
            plugin.getLogger().warning("[NanoForge] Ошибка в меню аддона '" + addon.getName() + "': " + t);
        }
    }

    /** Сохраняет материал/имя/лор/количество предмета, стоящего в слоте, обратно в addon.yml. */
    private void syncSlotToYaml(NanoMenuHolder holder, int slot) {
        Addon addon = addonManager.get(holder.getAddonName());
        if (addon == null) return;
        Inventory inv = holder.getInventory();
        if (inv == null) return;
        ItemStack stack = inv.getItem(slot);

        YamlConfiguration yaml = addon.getYaml();
        String base = "menus." + holder.getMenuKey() + ".items." + slot;

        if (stack == null || stack.getType() == Material.AIR) {
            // слот опустел - убираем весь пункт (иконку и всё что было настроено под ней)
            yaml.set(base, null);
        } else {
            yaml.set(base + ".material", stack.getType().name());
            yaml.set(base + ".amount", stack.getAmount());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                yaml.set(base + ".name", meta.getDisplayName());
            }
            if (meta != null && meta.hasLore()) {
                yaml.set(base + ".lore", meta.getLore());
            }
            // "actions" и "if", если уже были заданы для этого слота, НЕ трогаем -
            // мы обновили только material/amount/name/lore точечными путями выше.
        }

        try {
            yaml.save(addon.getFile());
        } catch (Exception e) {
            plugin.getLogger().warning("[NanoForge] Не удалось сохранить меню при редактировании: " + e.getMessage());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof NanoMenuHolder)) return;
        NanoMenuHolder holder = (NanoMenuHolder) event.getInventory().getHolder();
        if (holder.isEditMode() && event.getPlayer() instanceof Player) {
            ((Player) event.getPlayer()).sendMessage(ChatColor.GREEN
                    + "✔ " + f("Меню") + " '" + holder.getMenuKey() + "' " + f("сохранено."));
        }
    }

    // ---------- редактирование логики (actions) пункта через чат ----------

    private void startActionEdit(Player player, NanoMenuHolder holder, int slot) {
        pendingEdits.put(player.getUniqueId(), new PendingActionEdit(holder.getAddonName(), holder.getMenuKey(), slot));
        player.closeInventory();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "★ " + f("Правка действия для слота") + " " + slot + " ★");
        player.sendMessage(ChatColor.GRAY + f("Напиши в чат ОДНУ строку в формате:"));
        // сами ключевые слова DSL (message/call/openmenu и т.д.) НЕ прогоняются через FancyFont -
        // это литеральный синтаксис, который игрок должен набрать буквально, как есть
        player.sendMessage(ChatColor.YELLOW + "message <текст>" + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "broadcast <текст>" + ChatColor.GRAY + " | " + ChatColor.YELLOW + "console <команда>");
        player.sendMessage(ChatColor.YELLOW + "call <плагин> <метод> [аргументы]" + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "openmenu <меню> [аддон]" + ChatColor.GRAY + " | " + ChatColor.YELLOW + "closemenu");
        player.sendMessage(ChatColor.YELLOW + "setvar <ключ> <значение> [global]" + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "addvar <ключ> <число> [global]" + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "eco_give/eco_take <число>");
        player.sendMessage(ChatColor.GRAY + f("Это ЗАМЕНИТ весь список actions этого пункта одним новым действием."));
        player.sendMessage(ChatColor.RED + "➤ " + f("Напиши 'cancel' чтобы отменить."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingActionEdit pending = pendingEdits.get(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true); // сообщение не должно уйти в общий чат сервера
        String message = event.getMessage();

        // остальную работу (доступ к Bukkit API/файлам) делаем в основном потоке -
        // AsyncPlayerChatEvent по умолчанию обрабатывается асинхронно.
        Bukkit.getScheduler().runTask(plugin, () -> finishActionEdit(player, pending, message));
    }

    private void finishActionEdit(Player player, PendingActionEdit pending, String message) {
        pendingEdits.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + f("Отменено."));
            return;
        }

        String[] errorOut = new String[1];
        Map<String, Object> action = ActionLineParser.parse(message, errorOut);
        if (action == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f("Не удалось разобрать:") + " " + errorOut[0]);
            return;
        }

        Addon addon = addonManager.get(pending.addonName);
        if (addon == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f("Аддон") + " '" + pending.addonName + "' " + f("больше не существует."));
            return;
        }

        YamlConfiguration yaml = addon.getYaml();
        String base = "menus." + pending.menuKey + ".items." + pending.slot;
        // заменяем ВЕСЬ список actions этого пункта одним введённым действием
        // (если нужно несколько actions подряд - проще дописать через addon.yml вручную)
        yaml.set(base + ".actions", java.util.Collections.singletonList(action));

        try {
            yaml.save(addon.getFile());
            player.sendMessage(ChatColor.GREEN + "✔ " + f("Действие сохранено для слота") + " " + pending.slot + ".");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✖ " + f("Не удалось сохранить:") + " " + e.getMessage());
        }
    }
}

// by t.me/NanoDev_mc
