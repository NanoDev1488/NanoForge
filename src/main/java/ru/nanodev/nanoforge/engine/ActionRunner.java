package ru.nanodev.nanoforge.engine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.integration.ReflectionBridge;
import ru.nanodev.nanoforge.integration.VaultBridge;
import ru.nanodev.nanoforge.model.Addon;

import java.util.List;
import java.util.Map;

/**
 * Выполняет список действий, описанных в .yml (каждый элемент - Map с ключом type).
 * Один и тот же набор actions работает и в командах, и в событиях, и в пунктах GUI-меню.
 *
 * Каждый action может содержать необязательный блок "if:" (см. ConditionChecker) -
 * если условие не выполняется, action пропускается.
 * В любом text/command/args работают плейсхолдеры {player} {world} {x} {y} {z}
 * {health} {level} {uuid} (см. PlaceholderUtil).
 *
 * Поддерживаемые type:
 *  - message      { text, if? }
 *  - broadcast    { text, if? }
 *  - console      { command, if? }
 *  - call         { plugin, method, args, if? }        -> метод чужого плагина через рефлексию
 *  - openmenu     { menu, addon?, if? }                 -> открыть GUI-меню
 *  - closemenu    { if? }
 *  - setvar       { key, value, scope?: player|global, if? }   -> сохранить переменную аддона
 *  - addvar       { key, amount, scope?: player|global, if? }  -> прибавить число к переменной
 *  - eco_give     { amount, if? }                       -> начислить игроку деньги (Vault)
 *  - eco_take     { amount, if? }                       -> списать деньги (Vault)
 */
public class ActionRunner {

    @SuppressWarnings("unchecked")
    public static void run(List<?> actions, CommandSender sender, Event event, MenuManager menuManager, Addon currentAddon) {
        if (actions == null) return;
        Player player = resolvePlayer(sender, event);
        String currentAddonName = currentAddon != null ? currentAddon.getName() : null;
        Object[] lastCallResult = { null }; // хранится в массиве, чтобы менять из тела case внутри switch

        for (Object raw : actions) {
            if (!(raw instanceof Map)) continue;
            Map<String, Object> action = (Map<String, Object>) raw;

            try {
                if (!ConditionChecker.check(action, player, currentAddon)) {
                    continue; // условие не прошло - пропускаем этот action, идём к следующему
                }

                String type = String.valueOf(action.getOrDefault("type", "")).toLowerCase();

                switch (type) {
                    case "message": {
                        String text = withResult(colorize(PlaceholderUtil.apply(String.valueOf(action.get("text")), player)), lastCallResult[0]);
                        if (player != null) player.sendMessage(text);
                        else if (sender != null) sender.sendMessage(text);
                        break;
                    }
                    case "broadcast": {
                        String text = withResult(colorize(PlaceholderUtil.apply(String.valueOf(action.get("text")), player)), lastCallResult[0]);
                        Bukkit.broadcastMessage(text);
                        break;
                    }
                    case "console": {
                        String cmd = withResult(PlaceholderUtil.apply(String.valueOf(action.get("command")), player), lastCallResult[0]);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        break;
                    }
                    case "call": {
                        String plugin = String.valueOf(action.get("plugin"));
                        String method = String.valueOf(action.get("method"));
                        List<?> rawArgs = (List<?>) action.getOrDefault("args", java.util.Collections.emptyList());
                        String[] args = rawArgs.stream()
                                .map(a -> PlaceholderUtil.apply(String.valueOf(a), player))
                                .toArray(String[]::new);
                        Object result = ReflectionBridge.call(plugin, method, args);
                        // если указан save_as - результат вызова сохраняется в переменную аддона
                        // и становится доступен как плейсхолдер {result} в actions ПОСЛЕ этого call
                        if (action.containsKey("save_as") && currentAddon != null) {
                            String key = String.valueOf(action.get("save_as"));
                            boolean global = "global".equalsIgnoreCase(String.valueOf(action.getOrDefault("scope", "player")));
                            String resultStr = String.valueOf(result);
                            if (global) currentAddon.getStorage().setGlobalVar(key, resultStr);
                            else if (player != null) currentAddon.getStorage().setVar(player, key, resultStr);
                        }
                        lastCallResult[0] = result;
                        break;
                    }
                    case "openmenu": {
                        if (menuManager == null || player == null) break;
                        String menuKey = String.valueOf(action.get("menu"));
                        String addonName = action.containsKey("addon") ? String.valueOf(action.get("addon")) : currentAddonName;
                        menuManager.open(player, addonName, menuKey);
                        break;
                    }
                    case "closemenu": {
                        if (player != null) player.closeInventory();
                        break;
                    }
                    case "setvar": {
                        if (currentAddon == null) break;
                        String key = String.valueOf(action.get("key"));
                        String value = PlaceholderUtil.apply(String.valueOf(action.get("value")), player);
                        boolean global = "global".equalsIgnoreCase(String.valueOf(action.getOrDefault("scope", "player")));
                        if (global) currentAddon.getStorage().setGlobalVar(key, value);
                        else if (player != null) currentAddon.getStorage().setVar(player, key, value);
                        break;
                    }
                    case "addvar": {
                        if (currentAddon == null) break;
                        String key = String.valueOf(action.get("key"));
                        double amount = parseDouble(action.get("amount"), 0);
                        boolean global = "global".equalsIgnoreCase(String.valueOf(action.getOrDefault("scope", "player")));
                        if (global) currentAddon.getStorage().addGlobalVar(key, amount);
                        else if (player != null) currentAddon.getStorage().addVar(player, key, amount);
                        break;
                    }
                    case "eco_give": {
                        if (player != null) VaultBridge.deposit(player, parseDouble(action.get("amount"), 0));
                        break;
                    }
                    case "eco_take": {
                        if (player != null) VaultBridge.withdraw(player, parseDouble(action.get("amount"), 0));
                        break;
                    }
                    default:
                        Bukkit.getLogger().warning("[NanoForge] Неизвестный тип действия: " + type);
                }
            } catch (Throwable t) {
                // ЛЮБАЯ ошибка внутри одного action (кривой параметр, NPE, метод не нашёлся и т.д.)
                // не должна ронять всю цепочку и уж тем более всплывать наверх огромным
                // стектрейсом Bukkit'а - логируем одну понятную строку и идём к следующему action.
                String addonLabel = currentAddonName != null ? currentAddonName : "?";
                Bukkit.getLogger().warning("[NanoForge] Аддон '" + addonLabel + "': ошибка в action ("
                        + action.getOrDefault("type", "?") + "): " + t);
            }
        }
    }

    /** Старая сигнатура для обратной совместимости (без меню/переменных). */
    public static void run(List<?> actions, CommandSender sender, Event event) {
        run(actions, sender, event, null, null);
    }

    private static String withResult(String text, Object result) {
        if (text == null) return null;
        return text.replace("{result}", String.valueOf(result));
    }

    private static double parseDouble(Object o, double def) {
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Player resolvePlayer(CommandSender sender, Event event) {
        if (sender instanceof Player) return (Player) sender;
        if (event instanceof org.bukkit.event.player.PlayerEvent) {
            return ((org.bukkit.event.player.PlayerEvent) event).getPlayer();
        }
        return null;
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}

// by t.me/NanoDev_mc
