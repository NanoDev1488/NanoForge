package ru.nanodev.nanoforge.command;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.FancyFont;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;
import ru.nanodev.nanoforge.gui.MenuManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик /nano. Текстовые сообщения игроку оформлены через FancyFont.stylize()
 * (см. класс) и несколько "нормальных" символов-декораций (★ ➤ • ✔ ✖ ⚠) -
 * пользовательские данные (имена аддонов/плагинов, пути) шрифтом НЕ прогоняются,
 * чтобы оставались читаемыми как есть.
 */
public class NanoCommand implements CommandExecutor, TabCompleter {

    private final NanoForgePlugin plugin;
    private final AddonManager manager;
    private final MenuManager menuManager;

    public NanoCommand(NanoForgePlugin plugin, AddonManager manager, MenuManager menuManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.menuManager = menuManager;
    }

    /** Короткая обёртка над FancyFont.stylize - только для читаемости вызовов ниже. */
    private static String f(String text) {
        return FancyFont.stylize(text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nano.admin")) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Нет прав."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreate(sender, args);
            case "enable":
                return handleToggle(sender, args, true);
            case "disable":
                return handleToggle(sender, args, false);
            case "list":
                return handleList(sender);
            case "menu":
                return handleMenu(sender, args, false);
            case "edit":
                return handleMenu(sender, args, true);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender, args);
            case "duplicate":
                return handleDuplicate(sender, args);
            case "export":
                return handleExport(sender, args);
            case "import":
                return handleImport(sender, args);
            case "vars":
                return handleVars(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleDuplicate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano duplicate <аддон> <новое_имя>");
            return true;
        }
        String source = args[1];
        String newName = args[2];
        if (manager.get(source) == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон не найден:") + " " + source);
            return true;
        }
        if (manager.get(newName) != null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон с именем") + " '" + newName + "' " + f("уже существует."));
            return true;
        }
        Addon copy = manager.duplicate(source, newName);
        if (copy == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Не удалось создать копию."));
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "✔ " + f("Создана копия") + " '" + source + "' → '" + newName + "' "
                + f("(выключена, включи вручную)."));
        return true;
    }

    private boolean handleExport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano export <аддон>");
            return true;
        }
        String name = args[1];
        if (manager.get(name) == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон не найден:") + " " + name);
            return true;
        }
        try {
            java.io.File zip = manager.exportAddon(name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f("Аддон экспортирован:") + " " + zip.getPath());
        } catch (java.io.IOException e) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Ошибка экспорта:") + " " + e.getMessage());
        }
        return true;
    }

    private boolean handleVars(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano vars <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон не найден:") + " " + args[1]);
            return true;
        }
        java.util.Map<String, Object> vars = addon.getStorage().getAllGlobalVars();
        sender.sendMessage(ChatColor.GOLD + "★ " + f("Глобальные переменные") + " " + addon.getName() + " ★");
        if (vars.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + f("(пусто)"));
        } else {
            for (java.util.Map.Entry<String, Object> e : vars.entrySet()) {
                sender.sendMessage(ChatColor.YELLOW + "• " + e.getKey() + " = " + ChatColor.GRAY + e.getValue());
            }
        }
        return true;
    }

    private boolean handleImport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano import <файл.zip> <новое_имя>");
            sender.sendMessage(ChatColor.GRAY + f("Файл должен лежать в") + " plugins/NanoForge/imports/");
            return true;
        }
        String fileName = args[1];
        String newName = args[2];
        if (manager.get(newName) != null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон с именем") + " '" + newName + "' " + f("уже существует."));
            return true;
        }
        try {
            Addon addon = manager.importAddon(fileName, newName);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f("Аддон импортирован как") + " '" + addon.getName() + "' "
                    + f("(выключен, проверь и включи вручную)."));
        } catch (java.io.IOException e) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Ошибка импорта:") + " " + e.getMessage());
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        manager.reloadAll();
        sender.sendMessage(ChatColor.GREEN + "✔ " + f("NanoForge: все аддоны перезагружены с диска")
                + " (" + manager.getAddons().size() + " " + f("шт.") + ")");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano info <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон не найден:") + " " + args[1]);
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "★ " + addon.getName() + " ★");
        sender.sendMessage(ChatColor.YELLOW + f("Тип:") + " " + ChatColor.GRAY + addon.getType());
        if (addon.getTargetPlugin() != null) {
            org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(addon.getTargetPlugin());
            String statusText;
            if (target == null) statusText = ChatColor.RED + " (" + f("не установлен на сервере") + ")";
            else if (!target.isEnabled()) statusText = ChatColor.RED + " (" + f("установлен, но ВЫКЛЮЧЕН") + ")";
            else statusText = ChatColor.GREEN + " (" + f("активен") + ")";
            sender.sendMessage(ChatColor.YELLOW + f("Целевой плагин:") + " " + ChatColor.GRAY + addon.getTargetPlugin() + statusText);

            if (target != null && !target.isEnabled() && sender instanceof Player
                    && ru.nanodev.nanoforge.integration.PlugManBridge.isAvailable()) {
                ChatButtons.sendRunCommandButton((Player) sender,
                        ChatColor.GRAY + "  ", ChatColor.GREEN + "" + ChatColor.BOLD + "[" + f("Включить") + " " + addon.getTargetPlugin() + "]",
                        ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(addon.getTargetPlugin()),
                        "&a" + f("Кликни, чтобы включить через PlugMan"));
            }
        }
        sender.sendMessage(ChatColor.YELLOW + f("Статус:") + " "
                + (addon.isEnabled() ? ChatColor.GREEN + f("включён") : ChatColor.RED + f("выключен")));
        sender.sendMessage(ChatColor.YELLOW + f("Команды:") + " " + ChatColor.GRAY + addon.getCommandKeys());
        sender.sendMessage(ChatColor.YELLOW + f("События:") + " " + ChatColor.GRAY + addon.getEventKeys());
        sender.sendMessage(ChatColor.YELLOW + f("Меню:") + " " + ChatColor.GRAY + addon.getMenuKeys());
        sender.sendMessage(ChatColor.YELLOW + f("Папка:") + " " + ChatColor.GRAY + addon.getFolder().getPath());
        return true;
    }

    private boolean handleMenu(CommandSender sender, String[] args, boolean edit) {
        // /nano menu <аддон> <меню>   или   /nano edit <аддон> <меню>
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Меню можно открыть только игроку."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano " + args[0] + " <аддон> <меню>");
            return true;
        }
        if (edit) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + f("Режим редактирования: перетаскивай предметы в слоты - ")
                    + f("они станут иконками кнопок. Закрой меню, чтобы сохранить."));
            menuManager.openEdit((Player) sender, args[1], args[2]);
        } else {
            menuManager.open((Player) sender, args[1], args[2]);
        }
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        // /nano create addon <targetPlugin> <name>
        // /nano create new <name>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano create <addon|new> ...");
            return true;
        }
        String kind = args[1].toLowerCase();

        if (kind.equals("addon")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano create addon <плагин> <имя_аддона>");
                return true;
            }
            String targetPlugin = args[2];
            String name = args[3];

            if (Bukkit.getPluginManager().getPlugin(targetPlugin) == null) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ " + f("Внимание: плагин") + " '" + targetPlugin + "' "
                        + f("сейчас не найден на сервере, но файл всё равно создам."));
            }
            if (manager.get(name) != null) {
                sender.sendMessage(ChatColor.RED + "✖ " + f("Аддон с именем") + " '" + name + "' " + f("уже существует."));
                return true;
            }

            Addon addon = manager.createAddon(targetPlugin, name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f("Создан аддон") + " '" + addon.getName() + "' "
                    + f("для плагина") + " '" + targetPlugin + "'.");
            sender.sendMessage(ChatColor.GRAY + f("Редактируй:") + " plugins/NanoForge/addons/" + addon.getName() + "/addon.yml");
            if (addon.getTargetApiFile().exists()) {
                sender.sendMessage(ChatColor.GRAY + f("Список классов/методов") + " '" + targetPlugin + "': plugins/NanoForge/addons/"
                        + addon.getName() + "/target-api.txt");
            }
            sender.sendMessage(ChatColor.GRAY + "➤ " + f("Включить:") + " /nano enable " + addon.getName());
            return true;

        } else if (kind.equals("new")) {
            String name = args[2];
            if (manager.get(name) != null) {
                sender.sendMessage(ChatColor.RED + "✖ " + f("Плагин с именем") + " '" + name + "' " + f("уже существует."));
                return true;
            }
            Addon addon = manager.createNew(name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f("Создан новый мини-плагин") + " '" + addon.getName() + "'.");
            sender.sendMessage(ChatColor.GRAY + f("Редактируй:") + " plugins/NanoForge/addons/" + addon.getName() + "/addon.yml");
            sender.sendMessage(ChatColor.GRAY + "➤ " + f("Включить:") + " /nano enable " + addon.getName());
            return true;

        } else {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Второй аргумент должен быть 'addon' или 'new'."));
            return true;
        }
    }

    private boolean handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f("Использование:") + " /nano " + (enable ? "enable" : "disable") + " <имя>");
            return true;
        }
        String name = args[1];
        Addon addon = manager.get(name);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Не найдено:") + " " + name);
            return true;
        }

        boolean ok = enable ? manager.enable(name) : manager.disable(name);
        if (ok) {
            sender.sendMessage(ChatColor.GREEN + "✔ " + (enable ? f("Включено:") : f("Выключено:")) + " " + name);
        } else if (enable && addon.getTargetPlugin() != null) {
            reportMissingOrDisabledTarget(sender, addon);
        } else {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Не удалось выполнить операцию для:") + " " + name);
        }
        return true;
    }

    /**
     * Причина отказа при /nano enable почти всегда одна из двух: целевого плагина вообще нет
     * на сервере, либо он есть, но выключен. Во втором случае, если стоит PlugMan/PlugManX,
     * предлагаем игроку кликабельную кнопку для включения одним нажатием.
     */
    private void reportMissingOrDisabledTarget(CommandSender sender, Addon addon) {
        String targetName = addon.getTargetPlugin();
        org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(targetName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Не удалось включить") + " '" + addon.getName() + "': "
                    + f("плагин") + " '" + targetName + "' " + f("не установлен на сервере."));
            return;
        }

        if (!target.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "✖ " + f("Не удалось включить") + " '" + addon.getName() + "': "
                    + f("плагин") + " '" + targetName + "' " + f("установлен, но сейчас ВЫКЛЮЧЕН."));
            if (sender instanceof Player && ru.nanodev.nanoforge.integration.PlugManBridge.isAvailable()) {
                ChatButtons.sendRunCommandButton((Player) sender,
                        ChatColor.GRAY + "➤ " + f("Можно включить прямо отсюда:") + " ",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "[" + f("Включить") + " " + targetName + "]",
                        ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(targetName),
                        "&a" + f("Кликни, чтобы выполнить:") + " /" + ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(targetName));
                sender.sendMessage(ChatColor.GRAY + "(" + f("после включения запусти") + " /nano enable " + addon.getName() + " " + f("ещё раз") + ")");
            }
            return;
        }

        // target есть и включён, но enable всё равно вернул false - что-то ещё пошло не так
        sender.sendMessage(ChatColor.RED + "✖ " + f("Не удалось включить") + " '" + addon.getName() + "' "
                + f("по неизвестной причине - смотри консоль."));
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "★ " + f("Аддоны NanoForge") + " ★");
        for (Addon a : manager.getAddons()) {
            String status = a.isEnabled() ? ChatColor.GREEN + f("вкл") : ChatColor.RED + f("выкл");
            sender.sendMessage(ChatColor.YELLOW + "• " + a.getName() + " (" + a.getType() + ", " + status + ChatColor.YELLOW + ")");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "★ NanoForge ★");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano create addon <плагин> <имя> " + ChatColor.GRAY + "- " + f("новый аддон к плагину"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano create new <имя> " + ChatColor.GRAY + "- " + f("новый самостоятельный мини-плагин"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano enable <имя>");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano disable <имя>");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano list");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano menu <аддон> <меню> " + ChatColor.GRAY + "- " + f("открыть GUI-меню аддона"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano edit <аддон> <меню> " + ChatColor.GRAY + "- " + f("редактировать меню перетаскиванием предметов"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano info <аддон> " + ChatColor.GRAY + "- " + f("подробности об аддоне"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano reload " + ChatColor.GRAY + "- " + f("перечитать все аддоны с диска"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano duplicate <аддон> <имя> " + ChatColor.GRAY + "- " + f("клонировать аддон"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano export <аддон> " + ChatColor.GRAY + "- " + f("упаковать в .zip для переноса"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano import <файл.zip> <имя> " + ChatColor.GRAY + "- " + f("импортировать из imports/"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "enable", "disable", "list", "menu", "edit", "info", "reload",
                    "duplicate", "export", "import", "vars"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(Arrays.asList("addon", "new"), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")
                || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("duplicate") || args[0].equalsIgnoreCase("export") || args[0].equalsIgnoreCase("vars"))) {
            return filter(manager.getAddonNames(), args[1]);
        }

        // /nano menu|edit <аддон> <тут список меню этого аддона>
        if (args.length == 3 && (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("edit"))) {
            Addon addon = manager.get(args[1]);
            if (addon == null) return new ArrayList<>();
            return filter(addon.getMenuKeys(), args[2]);
        }

        // /nano create addon <тут список плагинов из папки plugins>
        if (args.length == 3 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("addon")) {
            List<String> pluginNames = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .map(Plugin::getName)
                    .collect(Collectors.toList());
            return filter(pluginNames, args[2]);
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}

// by t.me/NanoDev_mc
