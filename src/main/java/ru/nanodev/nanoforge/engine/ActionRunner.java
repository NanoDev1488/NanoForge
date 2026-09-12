package ru.nanodev.nanoforge.engine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.integration.ReflectionBridge;
import ru.nanodev.nanoforge.integration.VaultBridge;
import ru.nanodev.nanoforge.model.Addon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Выполняет список действий, описанных в .yml (каждый элемент - Map с ключом type).
 * Один и тот же набор actions работает и в командах, и в событиях, и в пунктах GUI-меню.
 *
 * Каждый action может содержать необязательный блок "if:" (см. ConditionChecker) -
 * если условие не выполняется, action пропускается.
 * В любом text/command/args работают плейсхолдеры {player} {world} {x} {y} {z}
 * {health} {level} {uuid} {result} и, если команда вызвана с аргументами,
 * {args} {arg1} {arg2} ... (см. PlaceholderUtil). Если установлен PlaceholderAPI,
 * его %плейсхолдеры% тоже подставляются - последним шагом.
 *
 * Поддерживаемые type:
 *  - message      { text, if? }
 *  - broadcast    { text, if? }
 *  - console      { command, if? }
 *  - call         { plugin, method, args, save_as?, scope?, if? } -> метод чужого плагина через рефлексию
 *  - openmenu     { menu, addon?, if? }                 -> открыть GUI-меню
 *  - closemenu    { if? }
 *  - setvar       { key, value, scope?: player|global, if? }   -> сохранить переменную аддона
 *  - addvar       { key, amount, scope?: player|global, if? }  -> прибавить число к переменной
 *  - eco_give     { amount, if? }                       -> начислить игроку деньги (Vault)
 *  - eco_take     { amount, if? }                       -> списать деньги (Vault)
 *  - give_item    { material, amount?, name?, lore?, if? }      -> выдать предмет игроку в инвентарь
 *                 (material и amount тоже проходят через плейсхолдеры - можно писать
 *                 material: "{arg1}" amount: "{arg2}" для выдачи по аргументам команды)
 */
public class ActionRunner {

    @SuppressWarnings("unchecked")
    public static void run(List<?> actions, CommandSender sender, Event event, MenuManager menuManager,
                            Addon currentAddon, String[] commandArgs) {
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
                        String text = withResult(colorize(PlaceholderUtil.apply(String.valueOf(action.get("text")), player, commandArgs)), lastCallResult[0]);
                        if (player != null) player.sendMessage(text);
                        else if (sender != null) sender.sendMessage(text);
                        break;
                    }
                    case "broadcast": {
                        String text = withResult(colorize(PlaceholderUtil.apply(String.valueOf(action.get("text")), player, commandArgs)), lastCallResult[0]);
                        Bukkit.broadcastMessage(text);
                        break;
                    }
                    case "console": {
                        String cmd = withResult(PlaceholderUtil.apply(String.valueOf(action.get("command")), player, commandArgs), lastCallResult[0]);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        break;
                    }
                    case "call": {
                        String plugin = String.valueOf(action.get("plugin"));
                        String method = String.valueOf(action.get("method"));
                        List<?> rawArgs = (List<?>) action.getOrDefault("args", java.util.Collections.emptyList());
                        String[] args = rawArgs.stream()
                                .map(a -> PlaceholderUtil.apply(String.valueOf(a), player, commandArgs))
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
                        String value = PlaceholderUtil.apply(String.valueOf(action.get("value")), player, commandArgs);
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
                    case "give_item": {
                        if (player == null) break;
                        String materialName = PlaceholderUtil.apply(
                                String.valueOf(action.getOrDefault("material", "STONE")), player, commandArgs).toUpperCase();
                        String amountRaw = PlaceholderUtil.apply(String.valueOf(action.getOrDefault("amount", "1")), player, commandArgs);
                        int amount = Math.max(1, (int) parseDouble(amountRaw, 1));
                        Material material;
                        try {
                            material = Material.valueOf(materialName);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning("[NanoForge] give_item: неизвестный материал '" + materialName + "'");
                            break;
                        }
                        ItemStack stack = new ItemStack(material, amount);
                        ItemMeta meta = stack.getItemMeta();
                        if (meta != null) {
                            if (action.containsKey("name")) {
                                String name = colorize(PlaceholderUtil.apply(String.valueOf(action.get("name")), player, commandArgs));
                                meta.setDisplayName(name);
                            }
                            if (action.get("lore") instanceof List) {
                                List<String> lore = new ArrayList<>();
                                for (Object line : (List<?>) action.get("lore")) {
                                    lore.add(colorize(PlaceholderUtil.apply(String.valueOf(line), player, commandArgs)));
                                }
                                meta.setLore(lore);
                            }
                            stack.setItemMeta(meta);
                        }
                        player.getInventory().addItem(stack);
                        break;
                    }
                    case "play_sound": {
                        if (player == null) break;
                        String soundName = String.valueOf(action.getOrDefault("sound", "ENTITY_PLAYER_LEVELUP")).toUpperCase();
                        float volume = (float) parseDouble(action.get("volume"), 1.0);
                        float pitch = (float) parseDouble(action.get("pitch"), 1.0);
                        try {
                            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                            player.playSound(player.getLocation(), sound, volume, pitch);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning("[NanoForge] play_sound: неизвестный звук '" + soundName + "'");
                        }
                        break;
                    }
                    case "teleport": {
                        if (player == null) break;
                        org.bukkit.World world = action.containsKey("world")
                                ? Bukkit.getWorld(String.valueOf(action.get("world")))
                                : player.getWorld();
                        if (world == null) {
                            Bukkit.getLogger().warning("[NanoForge] teleport: мир не найден");
                            break;
                        }
                        double x = parseDouble(action.get("x"), player.getLocation().getX());
                        double y = parseDouble(action.get("y"), player.getLocation().getY());
                        double z = parseDouble(action.get("z"), player.getLocation().getZ());
                        player.teleport(new org.bukkit.Location(world, x, y, z));
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

    public static void run(List<?> actions, CommandSender sender, Event event, MenuManager menuManager, Addon currentAddon) {
        run(actions, sender, event, menuManager, currentAddon, null);
    }

    /** Старая сигнатура для обратной совместимости (без меню/переменных/аргументов). */
    public static void run(List<?> actions, CommandSender sender, Event event) {
        run(actions, sender, event, null, null, null);
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
