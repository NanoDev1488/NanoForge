package ru.nanodev.nanoforge.command;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.FancyFont;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.model.Addon;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.util.Messages;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик /nano. Текстовые сообщения игроку оформлены через FancyFont.stylize()
 * (см. класс) и несколько "нормальных" символов-декораций (★ ➤ • ✔ ✖ ⚠) -
 * пользовательские данные (имена аддонов/плагинов, пути) шрифтом НЕ прогоняются,
 * чтобы оставались читаемыми как есть.
 *
 * Сам ТЕКСТ сообщений (без цвета/символа/переменных) живёт в messages.yml
 * (см. {@link Messages}) - здесь только собирается итоговая строка вокруг него,
 * ровно как раньше собиралась вокруг строкового литерала.
 */
public class NanoCommand implements CommandExecutor, TabCompleter {

    private final NanoForgePlugin plugin;
    private final AddonManager manager;
    private final MenuManager menuManager;
    private final ru.nanodev.nanoforge.wizard.AddonWizard addonWizard;

    public NanoCommand(NanoForgePlugin plugin, AddonManager manager, MenuManager menuManager,
                        ru.nanodev.nanoforge.wizard.AddonWizard addonWizard) {
        this.plugin = plugin;
        this.manager = manager;
        this.menuManager = menuManager;
        this.addonWizard = addonWizard;
    }

    /** Короткая обёртка над FancyFont.stylize - только для читаемости вызовов ниже. */
    private static String f(String text) {
        return FancyFont.stylize(text);
    }

    private static String m(String path) {
        return Messages.get(path);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nano.admin")) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.no-permission")));
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
            case "validate":
                return handleValidate(sender, args);
            case "get":
                return handleGet(sender, args);
            case "set":
                return handleSet(sender, args);
            case "diff":
                return handleDiff(sender, args);
            case "debug":
                return handleDebug(sender, args);
            case "update":
                return handleUpdate(sender);
            case "wizard":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.wizard.only-player")));
                    return true;
                }
                addonWizard.start((Player) sender);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleDuplicate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano duplicate <аддон> <новое_имя>");
            return true;
        }
        String source = args[1];
        String newName = args[2];
        if (manager.get(source) == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + source);
            return true;
        }
        if (manager.get(newName) != null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.duplicate.name-label")) + " '" + newName + "' " + f(m("command.already-exists")));
            return true;
        }
        Addon copy = manager.duplicate(source, newName);
        if (copy == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.duplicate.failed")));
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.duplicate.created")) + " '" + source + "' → '" + newName + "' "
                + f(m("command.duplicate.created-suffix")));
        return true;
    }

    private boolean handleExport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano export <аддон>");
            return true;
        }
        String name = args[1];
        if (manager.get(name) == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + name);
            return true;
        }
        try {
            java.io.File zip = manager.exportAddon(name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.export.exported")) + " " + zip.getPath());
        } catch (java.io.IOException e) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.export.failed")) + " " + e.getMessage());
        }
        return true;
    }

    private boolean handleVars(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano vars <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        java.util.Map<String, Object> vars = addon.getStorage().getAllGlobalVars();
        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.vars.header")) + " " + addon.getName() + " ★");
        if (vars.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + f(m("command.vars.empty")));
        } else {
            for (java.util.Map.Entry<String, Object> e : vars.entrySet()) {
                sender.sendMessage(ChatColor.YELLOW + "• " + e.getKey() + " = " + ChatColor.GRAY + e.getValue());
            }
        }
        return true;
    }

    private boolean handleImport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano import <файл.zip> <новое_имя>");
            sender.sendMessage(ChatColor.GRAY + f(m("command.import.usage-hint")) + " plugins/NanoForge/imports/");
            return true;
        }
        String fileName = args[1];
        String newName = args[2];
        if (manager.get(newName) != null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.import.name-label")) + " '" + newName + "' " + f(m("command.already-exists")));
            return true;
        }
        try {
            Addon addon = manager.importAddon(fileName, newName);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.import.imported")) + " '" + addon.getName() + "' "
                    + f(m("command.import.imported-suffix")));
        } catch (java.io.IOException e) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.import.failed")) + " " + e.getMessage());
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        manager.reloadAll();
        Messages.reload(plugin);
        sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.reload.success"))
                + " (" + manager.getAddons().size() + " " + f(m("command.reload.unit")) + ")");
        return true;
    }

    /**
     * Ручная проверка обновлений (та же, что автоматически выполняется при старте сервера,
     * см. {@link ru.nanodev.nanoforge.update.UpdateChecker}). Запрос к GitHub асинхронный,
     * поэтому отправитель сразу получает "проверяю...", а результат придёт чуть позже тем же
     * sender'ом (для игрока это безопасно - Bukkit сам переносит sendMessage в главный поток
     * из scheduler'а, а здесь используется runTask внутри checkForUpdates/UpdateChecker).
     */
    private boolean handleUpdate(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "➤ " + f(m("command.update.checking"))
                + " " + ChatColor.GRAY + "(NanoDev1488/NanoForge)");
        plugin.checkForUpdates(result ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.update.result")) + " " + ChatColor.GRAY + result)));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano info <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "★ " + addon.getName() + " ★");
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.type-label")) + " " + ChatColor.GRAY + addon.getType());
        if (addon.getTargetPlugin() != null) {
            org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(addon.getTargetPlugin());
            String statusText;
            if (target == null) statusText = ChatColor.RED + " (" + f(m("command.info.target-not-installed")) + ")";
            else if (!target.isEnabled()) statusText = ChatColor.RED + " (" + f(m("command.info.target-disabled")) + ")";
            else statusText = ChatColor.GREEN + " (" + f(m("command.info.target-active")) + ")";
            sender.sendMessage(ChatColor.YELLOW + f(m("command.info.target-label")) + " " + ChatColor.GRAY + addon.getTargetPlugin() + statusText);

            if (target != null && !target.isEnabled() && sender instanceof Player
                    && ru.nanodev.nanoforge.integration.PlugManBridge.isAvailable()) {
                ChatButtons.sendRunCommandButton((Player) sender,
                        ChatColor.GRAY + "  ", ChatColor.GREEN + "" + ChatColor.BOLD + "[" + f(m("command.info.enable-button-label")) + " " + addon.getTargetPlugin() + "]",
                        ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(addon.getTargetPlugin()),
                        "&a" + f(m("command.info.enable-button-hover")));
            }
        }
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.status-label")) + " "
                + (addon.isEnabled() ? ChatColor.GREEN + f(m("command.info.status-enabled")) : ChatColor.RED + f(m("command.info.status-disabled"))));
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.commands-label")) + " " + ChatColor.GRAY + addon.getCommandKeys());
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.events-label")) + " " + ChatColor.GRAY + addon.getEventKeys());
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.menus-label")) + " " + ChatColor.GRAY + addon.getMenuKeys());
        sender.sendMessage(ChatColor.YELLOW + f(m("command.info.folder-label")) + " " + ChatColor.GRAY + addon.getFolder().getPath());
        return true;
    }

    private boolean handleMenu(CommandSender sender, String[] args, boolean edit) {
        // /nano menu <аддон> <меню>   или   /nano edit <аддон> <меню>
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.menu.player-only")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano " + args[0] + " <аддон> <меню>");
            return true;
        }
        if (edit) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + f(m("command.menu.edit-mode-hint")));
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
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano create <addon|new> ...");
            return true;
        }
        String kind = args[1].toLowerCase();

        if (kind.equals("addon")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano create addon <плагин> <имя_аддона>");
                return true;
            }
            String targetPlugin = args[2];
            String name = args[3];

            if (Bukkit.getPluginManager().getPlugin(targetPlugin) == null) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ " + f(m("command.create.target-missing-warning")) + " '" + targetPlugin + "' "
                        + f(m("command.create.target-missing-warning-suffix")));
            }
            if (manager.get(name) != null) {
                sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.create.addon-name-label")) + " '" + name + "' " + f(m("command.already-exists")));
                return true;
            }

            Addon addon = manager.createAddon(targetPlugin, name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.create.created-addon")) + " '" + addon.getName() + "' "
                    + f(m("command.create.created-for")) + " '" + targetPlugin + "'.");
            sender.sendMessage(ChatColor.GRAY + f(m("command.create.edit-hint")) + " plugins/NanoForge/addons/" + addon.getName() + "/addon.yml");
            if (addon.getTargetApiFile().exists()) {
                sender.sendMessage(ChatColor.GRAY + f(m("command.create.target-api-hint")) + " '" + targetPlugin + "': plugins/NanoForge/addons/"
                        + addon.getName() + "/target-api.txt");
            }
            sender.sendMessage(ChatColor.GRAY + "➤ " + f(m("command.create.enable-hint")) + " /nano enable " + addon.getName());
            return true;

        } else if (kind.equals("new")) {
            String name = args[2];
            if (manager.get(name) != null) {
                sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.create.plugin-name-label")) + " '" + name + "' " + f(m("command.already-exists")));
                return true;
            }
            Addon addon = manager.createNew(name);
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.create.created-new")) + " '" + addon.getName() + "'.");
            sender.sendMessage(ChatColor.GRAY + f(m("command.create.edit-hint")) + " plugins/NanoForge/addons/" + addon.getName() + "/addon.yml");
            sender.sendMessage(ChatColor.GRAY + "➤ " + f(m("command.create.enable-hint")) + " /nano enable " + addon.getName());
            return true;

        } else {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.create.bad-kind")));
            return true;
        }
    }

    private boolean handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano " + (enable ? "enable" : "disable") + " <имя>");
            return true;
        }
        String name = args[1];
        Addon addon = manager.get(name);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.toggle.not-found")) + " " + name);
            return true;
        }

        boolean ok = enable ? manager.enable(name) : manager.disable(name);
        if (ok) {
            sender.sendMessage(ChatColor.GREEN + "✔ " + (enable ? f(m("command.toggle.enabled")) : f(m("command.toggle.disabled"))) + " " + name);
        } else if (enable && addon.getTargetPlugin() != null) {
            reportMissingOrDisabledTarget(sender, addon);
        } else {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.toggle.failed")) + " " + name);
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
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.toggle.cannot-enable")) + " '" + addon.getName() + "': "
                    + f(m("command.toggle.plugin-label")) + " '" + targetName + "' " + f(m("command.toggle.target-not-installed")));
            return;
        }

        if (!target.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.toggle.cannot-enable")) + " '" + addon.getName() + "': "
                    + f(m("command.toggle.plugin-label")) + " '" + targetName + "' " + f(m("command.toggle.target-disabled")));
            if (sender instanceof Player && ru.nanodev.nanoforge.integration.PlugManBridge.isAvailable()) {
                ChatButtons.sendRunCommandButton((Player) sender,
                        ChatColor.GRAY + "➤ " + f(m("command.toggle.enable-from-here")) + " ",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "[" + f(m("command.toggle.enable-button-label")) + " " + targetName + "]",
                        ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(targetName),
                        "&a" + f(m("command.toggle.enable-button-hover")) + " /" + ru.nanodev.nanoforge.integration.PlugManBridge.enableCommand(targetName));
                sender.sendMessage(ChatColor.GRAY + "(" + f(m("command.toggle.retry-hint")) + " /nano enable " + addon.getName() + " " + f(m("command.toggle.retry-hint-suffix")) + ")");
            }
            return;
        }

        // target есть и включён, но enable всё равно вернул false - что-то ещё пошло не так
        sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.toggle.cannot-enable")) + " '" + addon.getName() + "' "
                + f(m("command.toggle.unknown-reason")));
    }

    private boolean handleValidate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return handleAuditAll(sender);
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        printValidationReport(sender, addon);
        return true;
    }

    /** /nano validate без аргумента - проверить СРАЗУ все аддоны одной командой. */
    private boolean handleAuditAll(CommandSender sender) {
        java.util.Collection<Addon> addons = manager.getAddons();
        if (addons.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ " + f(m("command.validate.no-addons")));
            return true;
        }
        int totalErrors = 0;
        int totalWarnings = 0;
        for (Addon addon : addons) {
            List<String> issues = ru.nanodev.nanoforge.manager.AddonValidator.validate(addon);
            long errors = issues.stream().filter(i -> i.startsWith("ERROR")).count();
            long warnings = issues.stream().filter(i -> i.startsWith("WARN")).count();
            totalErrors += errors;
            totalWarnings += warnings;
            String status = errors > 0 ? ChatColor.RED + "✖ " + errors + " " + m("command.validate.errors-suffix")
                    : warnings > 0 ? ChatColor.YELLOW + "⚠ " + warnings + " " + m("command.validate.warnings-suffix")
                    : ChatColor.GREEN + "✔ " + m("command.validate.ok");
            sender.sendMessage(ChatColor.YELLOW + "• " + addon.getName() + ChatColor.GRAY + " - " + status);
        }
        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.validate.total-label")) + " " + addons.size() + " " + f(m("command.validate.total-addons-suffix"))
                + " " + totalErrors + " " + f(m("command.validate.total-errors-suffix")) + " " + totalWarnings + " " + f(m("command.validate.total-warnings-suffix")));
        if (totalErrors > 0 || totalWarnings > 0) {
            sender.sendMessage(ChatColor.GRAY + f(m("command.validate.details-hint")) + " /nano validate <аддон>");
        }
        return true;
    }

    private void printValidationReport(CommandSender sender, Addon addon) {
        List<String> issues = ru.nanodev.nanoforge.manager.AddonValidator.validate(addon);
        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.validate.report-header")) + " '" + addon.getName() + "' ★");
        for (String issue : issues) {
            if (issue.startsWith("ERROR")) {
                sender.sendMessage(ChatColor.RED + "✖ " + issue.substring("ERROR: ".length()));
            } else if (issue.startsWith("WARN")) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ " + issue.substring("WARN: ".length()));
            } else {
                sender.sendMessage(ChatColor.GREEN + "✔ " + issue.substring("OK: ".length()));
            }
        }
    }

    /** Плоский путь ключа (a.b.c) -> текущее значение из addon.yml, без загрузки в игру. */
    private boolean handleGet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano get <аддон> <путь>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        String path = args[2];
        if (!addon.getYaml().contains(path)) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.get.path-not-found")) + " " + path);
            return true;
        }
        Object value = addon.getYaml().get(path);
        sender.sendMessage(ChatColor.YELLOW + path + ChatColor.GRAY + " = " + ChatColor.WHITE + describeValue(value));
        return true;
    }

    /**
     * Меняет одно скалярное значение (строка/число/bool) по плоскому пути прямо в addon.yml,
     * без открытия файла руками. Списки/секции (menus.*.items.*.lore, actions[...] и т.п.)
     * этой командой намеренно не редактируются - для них /nano edit (GUI) или сам файл.
     * Табкомплит на аргументе-значении подставляет ТЕКУЩЕЕ значение по этому пути, чтобы
     * можно было поправить одну букву вместо перепечатывания всего значения заново.
     */
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano set <аддон> <путь> <значение>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        String path = args[2];
        Object oldValue = addon.getYaml().get(path);
        if (oldValue instanceof java.util.List || oldValue instanceof ConfigurationSection) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.set.path-is-section"))
                    + " " + path);
            sender.sendMessage(ChatColor.GRAY + f(m("command.set.path-is-section-hint"))
                    + " /nano edit " + f(m("command.set.path-is-section-hint-suffix")));
            return true;
        }

        String rawValue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        Object newValue = coerceToMatchType(rawValue, oldValue);
        ru.nanodev.nanoforge.manager.AddonBackup.backup(addon, plugin.getLogger());
        addon.getYaml().set(path, newValue);
        try {
            addon.getYaml().save(addon.getFile());
        } catch (java.io.IOException e) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.set.save-failed")) + " " + e.getMessage());
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "✔ " + path + ChatColor.GRAY + ": "
                + ChatColor.WHITE + describeValue(oldValue) + ChatColor.GRAY + " -> " + ChatColor.WHITE + describeValue(newValue));
        if (addon.isEnabled()) {
            sender.sendMessage(ChatColor.GRAY + "(" + f(m("command.set.reload-hint")) + " /nano reload)");
        }
        return true;
    }

    private static String describeValue(Object value) {
        return value == null ? ChatColor.GRAY + m("command.no-value") : String.valueOf(value);
    }

    /** Пытается сохранить исходный тип значения (число/bool), если по этому пути уже что-то было. */
    private static Object coerceToMatchType(String raw, Object oldValue) {
        if (oldValue instanceof Boolean) {
            if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                return Boolean.parseBoolean(raw);
            }
            return raw;
        }
        if (oldValue instanceof Integer) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        if (oldValue instanceof Double || oldValue instanceof Float) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        if (oldValue == null) {
            // новое значение (путь раньше не существовал) - угадываем тип по содержимому
            if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) return Boolean.parseBoolean(raw);
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored1) {
                try {
                    return Double.parseDouble(raw);
                } catch (NumberFormatException ignored2) {
                    return raw;
                }
            }
        }
        return raw;
    }

    /**
     * Сравнивает то, что СЕЙЧАС в памяти (то, чем аддон реально пользуется прямо
     * сейчас), с тем, что лежит на диске в addon.yml - то есть именно то, что
     * применится при следующем /nano reload. Полезно, когда addon.yml правили
     * руками (через SFTP/текстовый редактор) и хочется увидеть diff ПЕРЕД
     * перезагрузкой, а не после - если после перезагрузки окажется, что кто-то
     * забыл закрывающую кавычку, диагностировать это уже сложнее.
     */
    private boolean handleDiff(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano diff <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        if (!addon.getFile().exists()) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.diff.file-missing")) + " " + addon.getFile().getPath());
            return true;
        }

        org.bukkit.configuration.file.YamlConfiguration onDisk =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(addon.getFile());
        org.bukkit.configuration.file.YamlConfiguration inMemory = addon.getYaml();

        java.util.Set<String> allKeys = new java.util.TreeSet<>();
        allKeys.addAll(inMemory.getKeys(true));
        allKeys.addAll(onDisk.getKeys(true));

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (String key : allKeys) {
            boolean inMem = inMemory.contains(key) && !inMemory.isConfigurationSection(key);
            boolean onDiskHas = onDisk.contains(key) && !onDisk.isConfigurationSection(key);
            if (inMem && !onDiskHas) {
                removed.add(key);
            } else if (!inMem && onDiskHas) {
                added.add(key);
            } else if (inMem && onDiskHas) {
                Object memVal = inMemory.get(key);
                Object diskVal = onDisk.get(key);
                if (!java.util.Objects.equals(memVal, diskVal)) {
                    changed.add(key + ": " + memVal + " -> " + diskVal);
                }
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.diff.no-changes")));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.diff.header")) + " '" + addon.getName() + "' ★");
        for (String key : added) {
            sender.sendMessage(ChatColor.GREEN + "+ " + key + ChatColor.GRAY + " = " + onDisk.get(key));
        }
        for (String key : changed) {
            sender.sendMessage(ChatColor.YELLOW + "~ " + key);
        }
        for (String key : removed) {
            sender.sendMessage(ChatColor.RED + "- " + key + ChatColor.GRAY + " (" + f(m("command.diff.was")) + " " + inMemory.get(key) + ")");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "➤ " + f(m("command.usage-label")) + " /nano debug <аддон>");
            return true;
        }
        Addon addon = manager.get(args[1]);
        if (addon == null) {
            sender.sendMessage(ChatColor.RED + "✖ " + f(m("command.addon-not-found")) + " " + args[1]);
            return true;
        }
        boolean nowEnabled = ru.nanodev.nanoforge.util.ActionDebugger.toggle(addon.getName());
        if (nowEnabled) {
            sender.sendMessage(ChatColor.GREEN + "✔ " + f(m("command.debug.enabled")) + " '" + addon.getName()
                    + "' " + f(m("command.debug.enabled-suffix")));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ " + f(m("command.debug.disabled")) + " '" + addon.getName() + "'.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "★ " + f(m("command.list.header")) + " ★");
        for (Addon a : manager.getAddons()) {
            String status = a.isEnabled() ? ChatColor.GREEN + f(m("command.list.enabled")) : ChatColor.RED + f(m("command.list.disabled"));
            sender.sendMessage(ChatColor.YELLOW + "• " + a.getName() + " (" + a.getType() + ", " + status + ChatColor.YELLOW + ")");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + m("command.help.header"));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano create addon <плагин> <имя> " + ChatColor.GRAY + "- " + f(m("command.help.create-addon")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano create new <имя> " + ChatColor.GRAY + "- " + f(m("command.help.create-new")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano enable <имя>");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano disable <имя>");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano list");
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano menu <аддон> <меню> " + ChatColor.GRAY + "- " + f(m("command.help.menu")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano edit <аддон> <меню> " + ChatColor.GRAY + "- " + f(m("command.help.edit")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano info <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.info")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano reload " + ChatColor.GRAY + "- " + f(m("command.help.reload")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano duplicate <аддон> <имя> " + ChatColor.GRAY + "- " + f(m("command.help.duplicate")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano export <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.export")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano import <файл.zip> <имя> " + ChatColor.GRAY + "- " + f(m("command.help.import")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano vars <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.vars")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano validate <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.validate")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano get <аддон> <путь> " + ChatColor.GRAY + "- " + f(m("command.help.get")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano set <аддон> <путь> <значение> " + ChatColor.GRAY + "- " + f(m("command.help.set")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano validate " + ChatColor.GRAY + "- " + f(m("command.help.validate-all")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano diff <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.diff")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano debug <аддон> " + ChatColor.GRAY + "- " + f(m("command.help.debug")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano update " + ChatColor.GRAY + "- " + f(m("command.help.update")));
        sender.sendMessage(ChatColor.YELLOW + "➤ /nano wizard " + ChatColor.GRAY + "- " + f(m("command.help.wizard")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "enable", "disable", "list", "menu", "edit", "info", "reload",
                    "duplicate", "export", "import", "vars", "validate", "get", "set", "diff", "debug", "update", "wizard"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(Arrays.asList("addon", "new"), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")
                || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("duplicate") || args[0].equalsIgnoreCase("export") || args[0].equalsIgnoreCase("vars")
                || args[0].equalsIgnoreCase("validate") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("diff") || args[0].equalsIgnoreCase("debug"))) {
            return filter(manager.getAddonNames(), args[1]);
        }

        // /nano get|set <аддон> <тут все пути ключей addon.yml, включая вложенные>
        if (args.length == 3 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            Addon addon = manager.get(args[1]);
            if (addon == null) return new ArrayList<>();
            List<String> allPaths = new ArrayList<>(addon.getYaml().getKeys(true));
            java.util.Collections.sort(allPaths);
            return filter(allPaths, args[2]);
        }

        // /nano set <аддон> <путь> <тут ОДНО значение - то, что там уже сейчас лежит>
        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            Addon addon = manager.get(args[1]);
            if (addon == null) return new ArrayList<>();
            Object current = addon.getYaml().get(args[2]);
            if (current == null || current instanceof java.util.List || current instanceof ConfigurationSection) {
                return new ArrayList<>();
            }
            String currentAsText = String.valueOf(current);
            // ставим текущее значение первым кандидатом табкомплита независимо от того, что уже
            // напечатано - по нажатию Tab оно подставится целиком, и его можно доредактировать
            return Collections.singletonList(currentAsText);
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
