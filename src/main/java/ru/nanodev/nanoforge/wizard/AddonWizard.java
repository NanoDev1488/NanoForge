package ru.nanodev.nanoforge.wizard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;
import ru.nanodev.nanoforge.util.ItemBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /nano wizard - GUI-мастер создания аддона вместо ручного набора
 * /nano create + правки addon.yml с нуля. Сама СОЗДАНИЕ-логика ПОЛНОСТЬЮ
 * переиспользует {@link AddonManager#createNew} / {@link AddonManager#createAddon} -
 * визард только СОБИРАЕТ ответы (тип / целевой плагин / имя) через GUI и чат,
 * ничего не дублирует из существующей логики создания аддонов.
 *
 * Поток: меню "тип" -> (если "аддон к плагину") постраничный список включённых
 * плагинов -> имя вводится в чат -> создание -> подсказка, что делать дальше.
 */
public class AddonWizard implements Listener {

    private static final int PLUGIN_PAGE_SIZE = 45; // 5 рядов из 6 (последний - навигация)

    private final NanoForgePlugin plugin;
    private final AddonManager addonManager;

    private static class PendingName {
        final boolean addonType;
        final String targetPlugin; // null, если addonType == false

        PendingName(boolean addonType, String targetPlugin) {
            this.addonType = addonType;
            this.targetPlugin = targetPlugin;
        }
    }

    private final Map<UUID, PendingName> pendingNamePrompts = new HashMap<>();

    public AddonWizard(NanoForgePlugin plugin, AddonManager addonManager) {
        this.plugin = plugin;
        this.addonManager = addonManager;
    }

    public void start(Player player) {
        openTypeChoice(player);
    }

    private void openTypeChoice(Player player) {
        Inventory inv = Bukkit.createInventory(new WizardHolder(WizardHolder.Stage.TYPE_CHOICE, 0),
                27, ChatColor.GOLD + "NanoForge \u2192 создать аддон");
        inv.setItem(11, ItemBuilder.build(Material.BOOK, "&aМини-плагин",
                "&7Самостоятельная команда/меню,", "&7не завязан ни на какой другой плагин."));
        inv.setItem(15, ItemBuilder.build(Material.CHEST, "&bАддон к плагину",
                "&7Обёртка/рефлексия над уже", "&7установленным на сервере плагином."));
        inv.setItem(22, ItemBuilder.build(Material.BARRIER, "&cОтмена"));
        player.openInventory(inv);
    }

    private List<Plugin> enabledPlugins() {
        List<Plugin> plugins = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.isEnabled() && !p.getName().equals(plugin.getName())) {
                plugins.add(p);
            }
        }
        plugins.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
        return plugins;
    }

    private void openPluginChoice(Player player, int page) {
        List<Plugin> plugins = enabledPlugins();
        int maxPage = plugins.isEmpty() ? 0 : (plugins.size() - 1) / PLUGIN_PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(new WizardHolder(WizardHolder.Stage.PLUGIN_CHOICE, page),
                54, ChatColor.GOLD + "Выбери плагин (" + (page + 1) + "/" + (maxPage + 1) + ")");

        int start = page * PLUGIN_PAGE_SIZE;
        for (int i = 0; i < PLUGIN_PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= plugins.size()) break;
            Plugin p = plugins.get(idx);
            inv.setItem(i, ItemBuilder.build(Material.PAPER, "&e" + p.getName(),
                    "&7v" + p.getDescription().getVersion()));
        }
        if (plugins.isEmpty()) {
            inv.setItem(22, ItemBuilder.build(Material.BARRIER, "&cНа сервере нет других включённых плагинов"));
        }
        if (page > 0) inv.setItem(45, ItemBuilder.build(Material.ARROW, "&a\u2190 Назад"));
        if (page < maxPage) inv.setItem(53, ItemBuilder.build(Material.ARROW, "&aВперёд \u2192"));
        inv.setItem(49, ItemBuilder.build(Material.BARRIER, "&cОтмена"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holderObj = event.getInventory().getHolder();
        if (!(holderObj instanceof WizardHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        WizardHolder holder = (WizardHolder) holderObj;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (holder.getStage() == WizardHolder.Stage.TYPE_CHOICE) {
            if (event.getSlot() == 11) {
                player.closeInventory();
                promptForName(player, false, null);
            } else if (event.getSlot() == 15) {
                openPluginChoice(player, 0);
            } else if (event.getSlot() == 22) {
                player.closeInventory();
                player.sendMessage(ChatColor.GRAY + "Отменено.");
            }
            return;
        }

        // PLUGIN_CHOICE
        if (event.getSlot() == 49) {
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "Отменено.");
            return;
        }
        if (event.getSlot() == 45) {
            openPluginChoice(player, holder.getPage() - 1);
            return;
        }
        if (event.getSlot() == 53) {
            openPluginChoice(player, holder.getPage() + 1);
            return;
        }
        String targetPlugin = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        player.closeInventory();
        promptForName(player, true, targetPlugin);
    }

    private void promptForName(Player player, boolean addonType, String targetPlugin) {
        pendingNamePrompts.put(player.getUniqueId(), new PendingName(addonType, targetPlugin));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "★ " + ChatColor.RESET
                + (addonType ? "Аддон к плагину '" + targetPlugin + "'" : "Мини-плагин") + " ★");
        player.sendMessage(ChatColor.GRAY + "Напиши в чат ИМЯ (без пробелов) - или 'cancel' для отмены.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingName pending = pendingNamePrompts.get(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> finishWizard(player, pending, message));
    }

    private void finishWizard(Player player, PendingName pending, String rawName) {
        pendingNamePrompts.remove(player.getUniqueId());

        if (rawName.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Отменено, ничего не создано.");
            return;
        }
        if (rawName.isEmpty() || rawName.contains(" ") || rawName.contains("/") || rawName.contains("\\")) {
            player.sendMessage(ChatColor.RED + "✖ Имя не должно быть пустым и не должно содержать пробелы/слэши. "
                    + "Начни заново: /nano wizard");
            return;
        }
        if (addonManager.get(rawName) != null) {
            player.sendMessage(ChatColor.RED + "✖ Аддон с именем '" + rawName + "' уже существует. "
                    + "Начни заново с другим именем: /nano wizard");
            return;
        }

        Addon created = pending.addonType
                ? addonManager.createAddon(pending.targetPlugin, rawName)
                : addonManager.createNew(rawName);

        player.sendMessage(ChatColor.GREEN + "✔ Создано: " + created.getName()
                + ChatColor.GRAY + " (" + (pending.addonType ? "addon -> " + pending.targetPlugin : "new") + ")");
        player.sendMessage(ChatColor.YELLOW + "➤ /nano edit " + created.getName() + " main "
                + ChatColor.GRAY + "- настроить пример-меню");
        player.sendMessage(ChatColor.YELLOW + "➤ /nano enable " + created.getName() + " "
                + ChatColor.GRAY + "- включить, когда будет готов");
    }
}

// by t.me/NanoDev_mc
