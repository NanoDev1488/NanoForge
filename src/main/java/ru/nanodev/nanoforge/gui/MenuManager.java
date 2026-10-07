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
import ru.nanodev.nanoforge.util.Messages;

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
        final List<Map<String, Object>> collected = new ArrayList<>();

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

    private static String m(String path) {
        return Messages.get(path);
    }

    public MenuManager(NanoForgePlugin plugin, AddonManager addonManager) {
        this.plugin = plugin;
        this.addonManager = addonManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Открыть меню menuKey из аддона addonName для игрока (обычный режим). */
    /** Закрывает открытое меню этого аддона у всех игроков, у кого оно сейчас открыто (аддон выключается/удаляется). */
    public void closeMenusForAddon(String addonName) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.InventoryView view = p.getOpenInventory();
            if (view == null) continue;
            Object holder = view.getTopInventory().getHolder();
            if (holder instanceof NanoMenuHolder && ((NanoMenuHolder) holder).getAddonName().equalsIgnoreCase(addonName)) {
                p.closeInventory();
            }
        }
    }

    public boolean open(Player player, String addonName, String menuKey) {
        return open(player, addonName, menuKey, false);
    }

    /** Открыть меню в режиме редактирования (перетаскивание предметов сохраняется в yaml). */
    public boolean openEdit(Player player, String addonName, String menuKey) {
        return open(player, addonName, menuKey, true);
    }

    /** Текущая страница на игрока+аддон+меню - переживает переоткрытие через page_next/page_prev. */
    private final Map<String, Integer> pageTracker = new HashMap<>();

    private String pageKey(Player player, String addonName, String menuKey) {
        return player.getUniqueId() + "::" + addonName + "::" + menuKey;
    }

    /** delta=+1/-1. Переоткрывает то же меню на новой странице (без выхода из edit-режима, если он был). */
    public void changePage(Player player, String addonName, String menuKey, int delta, boolean editMode) {
        String key = pageKey(player, addonName, menuKey);
        int current = pageTracker.getOrDefault(key, 0);
        pageTracker.put(key, Math.max(0, current + delta));
        open(player, addonName, menuKey, editMode);
    }

    private boolean open(Player player, String addonName, String menuKey, boolean editMode) {
        Addon addon = addonManager.get(addonName);
        if (addon == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.addon-not-found")) + " " + addonName);
            return false;
        }
        if (!addon.getMenuKeys().contains(menuKey)) {
            player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.menu-not-found-part1")) + " '" + menuKey + "' " + f(m("menu.menu-not-found-part2")) + " " + addonName);
            return false;
        }

        int rows = addon.getMenuRows(menuKey);
        int size = rows * 9;
        ConfigurationSection listCfg = addon.getYaml().getConfigurationSection("menus." + menuKey + ".list");

        // если у меню есть "list:" - страница/maxpage нужны ЕЩЁ ДО создания инвентаря (заголовок
        // может содержать {page}/{maxpage}), поэтому список значений резолвим один раз здесь
        List<String> listValues = listCfg != null ? resolveListSource(listCfg.getString("source", "")) : java.util.Collections.emptyList();
        int slotsFrom = listCfg != null ? listCfg.getInt("slots_from", 0) : 0;
        int slotsTo = listCfg != null ? Math.min(listCfg.getInt("slots_to", size - 1), size - 1) : -1;
        int pageSize = Math.max(1, slotsTo - slotsFrom + 1);
        int maxPage = listValues.isEmpty() ? 0 : (listValues.size() - 1) / pageSize;
        int page = Math.min(maxPage, pageTracker.getOrDefault(pageKey(player, addonName, menuKey), 0));

        String title = ChatColor.translateAlternateColorCodes('&', addon.getMenuTitle(menuKey));
        title = title.replace("{page}", String.valueOf(page + 1)).replace("{maxpage}", String.valueOf(maxPage + 1));
        if (editMode) title = ChatColor.LIGHT_PURPLE + "[Edit] " + ChatColor.RESET + title;

        NanoMenuHolder holder = new NanoMenuHolder(addon.getName(), menuKey);
        holder.setEditMode(editMode);
        holder.setCurrentPage(page);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        if (listCfg != null && !listValues.isEmpty()) {
            ConfigurationSection itemTemplate = listCfg.getConfigurationSection("item");
            if (itemTemplate != null) {
                int startIndex = page * pageSize;
                for (int i = 0; i < pageSize; i++) {
                    int idx = startIndex + i;
                    if (idx >= listValues.size()) break;
                    int slot = slotsFrom + i;
                    if (slot > slotsTo || slot >= size) break;
                    String value = listValues.get(idx);
                    inv.setItem(slot, buildGeneratedItem(itemTemplate, value));
                    holder.putGeneratedValue(slot, value);
                }
            }
        }

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

    /**
     * Источники для menus.*.list.source - сейчас только "online_players" (имена игроков
     * онлайн прямо сейчас). Список специально сделан коротким и расширяемым - неизвестный
     * source просто даёт пустой список (меню без сгенерированных пунктов), а не ошибку.
     */
    private List<String> resolveListSource(String source) {
        if ("online_players".equalsIgnoreCase(source)) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return java.util.Collections.emptyList();
    }

    /** Как buildItem(), но подставляет {value} (например, имя игрока) в имя/лор итогового предмета. */
    private ItemStack buildGeneratedItem(ConfigurationSection template, String value) {
        ItemStack stack = buildItem(template);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                meta.setDisplayName(meta.getDisplayName().replace("{value}", value));
            }
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.replaceAll(line -> line.replace("{value}", value));
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildItem(ConfigurationSection cfg) {
        Material material = ru.nanodev.nanoforge.util.MaterialUtil.tryParse(cfg.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        return ru.nanodev.nanoforge.util.ItemBuilder.build(
                material, Math.max(1, cfg.getInt("amount", 1)), cfg.getString("name", null), cfg.getStringList("lore"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NanoMenuHolder)) return;
        NanoMenuHolder holder = (NanoMenuHolder) event.getInventory().getHolder();

        if (holder.isEditMode()) {
            boolean clickedIsMenu = event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof NanoMenuHolder;

            // shift+клик (левый или правый) ПО САМОМУ МЕНЮ - правка ЛОГИКИ (actions) через чат.
            // shift+клик ИЗ своего инвентаря наверх обрабатывается ниже отдельной веткой.
            if (clickedIsMenu && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
                event.setCancelled(true);
                startActionEdit((Player) event.getWhoClicked(), holder, event.getSlot());
                return;
            }
            // обычный клик/перетаскивание по меню - меняем ТОЛЬКО иконку (материал/имя/лор/кол-во)
            if (clickedIsMenu) {
                int slot = event.getSlot();
                Bukkit.getScheduler().runTask(plugin, () -> syncSlotToYaml(holder, slot));
                return;
            }

            // shift+клик ИЗ своего инвентаря В меню (обычный ванильный "закинуть предмет
            // одним кликом") - ванильная логика сама решает, в какой именно слот сверху он
            // попадёт (возможно, сразу в несколько - если предмет стакуется на существующие
            // стопки). Событие клика этого слота не сообщает - поэтому снимаем "снимок" верхнего
            // инвентаря ДО обработки клика (сейчас, синхронно - клик ещё не применён) и на
            // следующем тике сравниваем с тем, что получилось, чтобы понять, что именно изменилось.
            boolean shiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
            boolean fromPlayerInventory = event.getClickedInventory() != null
                    && event.getClickedInventory().equals(((Player) event.getWhoClicked()).getInventory());
            if (shiftClick && fromPlayerInventory) {
                Inventory topInv = holder.getInventory();
                ItemStack[] before = topInv != null ? topInv.getContents().clone() : new ItemStack[0];
                Bukkit.getScheduler().runTask(plugin, () -> syncShiftClickedSlots(holder, before));
            }
            return;
        }

        event.setCancelled(true); // в обычном режиме это меню, а не сундук - таскать нельзя

        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof NanoMenuHolder)) {
            return; // клик по инвентарю игрока снизу - игнорируем
        }

        Addon addon = addonManager.get(holder.getAddonName());
        if (addon == null || !addon.isEnabled()) return;

        Player player = (Player) event.getWhoClicked();

        // сгенерированный пункт (menus.*.list) - actions берутся из шаблона list.item,
        // а не из menus.*.items, и {value} в actions подставляется как аргумент клика
        String generatedValue = holder.getGeneratedValue(event.getSlot());
        if (generatedValue != null) {
            ConfigurationSection listCfg = addon.getYaml().getConfigurationSection("menus." + holder.getMenuKey() + ".list");
            ConfigurationSection itemTemplate = listCfg != null ? listCfg.getConfigurationSection("item") : null;
            List<?> generatedActions = itemTemplate != null
                    ? itemTemplate.getList("actions", java.util.Collections.emptyList())
                    : java.util.Collections.emptyList();
            try {
                ActionRunner.run(generatedActions, player, event, this, addon, new String[]{generatedValue});
            } catch (Throwable t) {
                player.sendMessage(org.bukkit.ChatColor.RED + "✖ " + f(m("menu.action-error")));
                plugin.getLogger().warning(Messages.get("menu.error.generated-item", "addon", addon.getName(), "error", t));
            }
            return;
        }

        ConfigurationSection items = addon.getMenuItemsSection(holder.getMenuKey());
        if (items == null) return;

        ConfigurationSection item = items.getConfigurationSection(String.valueOf(event.getSlot()));
        if (item == null) return;

        if (!ConditionChecker.checkVisibility(item, player)) return; // скрытый пункт - клик по пустому слоту

        List<?> actions = item.getList("actions", java.util.Collections.emptyList());
        try {
            ActionRunner.run(actions, player, event, this, addon);
        } catch (Throwable t) {
            player.sendMessage(org.bukkit.ChatColor.RED + "✖ " + f(m("menu.action-error")));
            plugin.getLogger().warning(Messages.get("menu.error.menu-item", "addon", addon.getName(), "error", t));
        }
    }

    /** Сравнивает "было/стало" верхнего инвентаря после shift-клика и синхронизирует каждый изменившийся слот. */
    private void syncShiftClickedSlots(NanoMenuHolder holder, ItemStack[] before) {
        Inventory inv = holder.getInventory();
        if (inv == null) return;
        ItemStack[] after = inv.getContents();
        int max = Math.min(before.length, after.length);
        for (int slot = 0; slot < max; slot++) {
            if (!java.util.Objects.equals(before[slot], after[slot])) {
                syncSlotToYaml(holder, slot);
            }
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
            plugin.getLogger().warning(Messages.get("menu.error.save-failed", "error", e.getMessage()));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof NanoMenuHolder)) return;
        NanoMenuHolder holder = (NanoMenuHolder) event.getInventory().getHolder();
        if (holder.isEditMode() && event.getPlayer() instanceof Player) {
            ((Player) event.getPlayer()).sendMessage(ChatColor.GREEN
                    + "✔ " + f(m("menu.saved")) + " '" + holder.getMenuKey() + "' " + f(m("menu.saved-suffix")));
        }
    }

    // ---------- редактирование логики (actions) пункта через чат ----------

    private void startActionEdit(Player player, NanoMenuHolder holder, int slot) {
        pendingEdits.put(player.getUniqueId(), new PendingActionEdit(holder.getAddonName(), holder.getMenuKey(), slot));
        player.closeInventory();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "★ " + f(m("menu.edit.header")) + " " + slot + " ★");
        player.sendMessage(ChatColor.GRAY + f(m("menu.edit.intro")));
        // сами ключевые слова DSL (message/call/openmenu и т.д.) НЕ прогоняются через FancyFont -
        // это литеральный синтаксис, который игрок должен набрать буквально, как есть
        player.sendMessage(m("menu.edit.dsl-line1"));
        player.sendMessage(m("menu.edit.dsl-line2"));
        player.sendMessage(m("menu.edit.dsl-line3"));
        player.sendMessage(ChatColor.AQUA + "➤ " + f(m("menu.edit.append-hint")));
        player.sendMessage(ChatColor.AQUA + "➤ " + f("'done'") + " - " + f(m("menu.edit.save-hint-done")) + "   "
                + ChatColor.AQUA + "'undo'" + ChatColor.GRAY + " - " + f(m("menu.edit.save-hint-undo")));
        player.sendMessage(ChatColor.RED + "➤ " + f("'cancel'") + " - " + f(m("menu.edit.cancel-hint")));
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
        Bukkit.getScheduler().runTask(plugin, () -> handleActionEditLine(player, pending, message));
    }

    /**
     * Обрабатывает ОДНУ строку многострочной чат-DSL сессии редактирования.
     * В отличие от старой версии (одна строка -> сразу сохранение и выход),
     * теперь строки НАКАПЛИВАЮТСЯ в pending.collected, пока игрок не напишет
     * 'done' (сохранить всё разом) или 'cancel' (отменить сессию целиком).
     * 'undo' убирает последнюю добавленную строку, не завершая сессию.
     */
    private void handleActionEditLine(Player player, PendingActionEdit pending, String message) {
        String trimmed = message.trim();

        if (trimmed.equalsIgnoreCase("cancel")) {
            pendingEdits.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + f(m("menu.edit.cancelled")));
            return;
        }

        if (trimmed.equalsIgnoreCase("undo")) {
            if (pending.collected.isEmpty()) {
                player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.edit.undo-empty")));
            } else {
                pending.collected.remove(pending.collected.size() - 1);
                player.sendMessage(ChatColor.YELLOW + "↩ " + f(m("menu.edit.undo-done")) + " "
                        + pending.collected.size());
            }
            return; // сессия продолжается
        }

        if (trimmed.equalsIgnoreCase("done") || trimmed.equalsIgnoreCase("save")) {
            pendingEdits.remove(player.getUniqueId());
            saveCollectedActions(player, pending);
            return;
        }

        String[] errorOut = new String[1];
        Map<String, Object> action = ActionLineParser.parse(trimmed, errorOut);
        if (action == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.edit.parse-failed")) + " " + errorOut[0]);
            player.sendMessage(ChatColor.GRAY + f(m("menu.edit.parse-failed-hint")));
            return; // сессия продолжается, прогресс не теряется
        }

        pending.collected.add(action);
        player.sendMessage(ChatColor.GREEN + "✔ " + f(m("menu.edit.added")) + " (#" + pending.collected.size() + "): "
                + ChatColor.GRAY + trimmed);
        player.sendMessage(ChatColor.GRAY + f(m("menu.edit.continue-hint-part1")) + " " + ChatColor.AQUA + "'done'"
                + ChatColor.GRAY + " " + f(m("menu.edit.continue-hint-part2")));
    }

    private void saveCollectedActions(Player player, PendingActionEdit pending) {
        if (pending.collected.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ " + f(m("menu.edit.nothing-collected")));
            return;
        }

        Addon addon = addonManager.get(pending.addonName);
        if (addon == null) {
            player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.edit.addon-gone")) + " '" + pending.addonName + "' " + f(m("menu.edit.addon-gone-suffix")));
            return;
        }

        YamlConfiguration yaml = addon.getYaml();
        String base = "menus." + pending.menuKey + ".items." + pending.slot;
        ru.nanodev.nanoforge.manager.AddonBackup.backup(addon, plugin.getLogger());
        // заменяем ВЕСЬ список actions этого пункта тем, что накопилось за сессию
        yaml.set(base + ".actions", pending.collected);

        try {
            yaml.save(addon.getFile());
            player.sendMessage(ChatColor.GREEN + "✔ " + f(m("menu.edit.save-success-part1")) + " " + pending.collected.size() + " "
                    + f(m("menu.edit.save-success-part2")) + " " + pending.slot + ".");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✖ " + f(m("menu.edit.save-failed")) + " " + e.getMessage());
        }
    }
}

// by t.me/NanoDev_mc
