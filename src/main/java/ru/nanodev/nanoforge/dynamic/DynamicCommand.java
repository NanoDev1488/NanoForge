package ru.nanodev.nanoforge.dynamic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.engine.FancyFont;
import ru.nanodev.nanoforge.model.Addon;
import ru.nanodev.nanoforge.util.Messages;

/**
 * Команда, "выдуманная" из YAML описания аддона/нью-плагина.
 * Регистрируется напрямую в CommandMap сервера (см. AddonManager.enableInternal),
 * поэтому её не нужно объявлять заранее в plugin.yml.
 */
public class DynamicCommand extends Command {

    private final Addon addon;
    private final String cmdKey;

    public DynamicCommand(Addon addon, String cmdKey) {
        super(cmdKey, addon.getCommandDescription(cmdKey), "/" + cmdKey, addon.getCommandAliases(cmdKey));
        this.addon = addon;
        this.cmdKey = cmdKey;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = addon.getCommandTabComplete(cmdKey);
        if (completions == null || completions.isEmpty()) {
            return super.tabComplete(sender, alias, args);
        }
        String lastArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        java.util.List<String> matched = new java.util.ArrayList<>();
        for (String c : completions) {
            if (c.toLowerCase().startsWith(lastArg)) {
                matched.add(c);
            }
        }
        return matched;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!addon.isEnabled()) {
            sender.sendMessage("§c✖ " + FancyFont.stylize(Messages.get("dynamic.command.disabled")));
            return true;
        }
        String permission = addon.getCommandPermission(cmdKey);
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage("§c✖ " + FancyFont.stylize(Messages.get("dynamic.command.no-permission")) + " " + permission);
            return true;
        }
        try {
            ActionRunner.run(addon.getCommandActions(cmdKey), sender, null,
                    NanoForgePlugin.get().getMenuManager(), addon, args);
        } catch (Throwable t) {
            // без этого Bukkit сам напечатал бы игроку/в консоль "An internal error occurred
            // while attempting to perform this command" вместе с полным стектрейсом.
            sender.sendMessage("§c✖ " + FancyFont.stylize(Messages.get("dynamic.command.error")) + " '" + addon.getName() + "'.");
            NanoForgePlugin.get().getLogger().warning(Messages.get("dynamic.command.error-log",
                    "addon", addon.getName(), "command", cmdKey, "error", t));
        }
        return true;
    }
}

// by t.me/NanoDev_mc
