package ru.nanodev.nanoforge.dynamic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.engine.FancyFont;
import ru.nanodev.nanoforge.model.Addon;

/**
 * Команда, "выдуманная" из YAML описания аддона/нью-плагина.
 * Регистрируется напрямую в CommandMap сервера (см. AddonManager.enableInternal),
 * поэтому её не нужно объявлять заранее в plugin.yml.
 */
public class DynamicCommand extends Command {

    private final Addon addon;
    private final String cmdKey;

    public DynamicCommand(Addon addon, String cmdKey) {
        super(cmdKey, addon.getCommandDescription(cmdKey), "/" + cmdKey, java.util.Collections.emptyList());
        this.addon = addon;
        this.cmdKey = cmdKey;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!addon.isEnabled()) {
            sender.sendMessage("§c✖ " + FancyFont.stylize("Этот аддон сейчас выключен."));
            return true;
        }
        String permission = addon.getCommandPermission(cmdKey);
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage("§c✖ " + FancyFont.stylize("У тебя нет права:") + " " + permission);
            return true;
        }
        try {
            ActionRunner.run(addon.getCommandActions(cmdKey), sender, null,
                    NanoForgePlugin.get().getMenuManager(), addon);
        } catch (Throwable t) {
            // без этого Bukkit сам напечатал бы игроку/в консоль "An internal error occurred
            // while attempting to perform this command" вместе с полным стектрейсом.
            sender.sendMessage("§c✖ " + FancyFont.stylize("Ошибка при выполнении команды аддона") + " '" + addon.getName() + "'.");
            NanoForgePlugin.get().getLogger().warning("[NanoForge] Ошибка в команде аддона '"
                    + addon.getName() + "' (" + cmdKey + "): " + t);
        }
        return true;
    }
}

// by t.me/NanoDev_mc
