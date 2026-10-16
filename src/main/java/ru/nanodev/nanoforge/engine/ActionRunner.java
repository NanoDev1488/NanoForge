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
 *  - console      { command, if? }  ИЛИ  { commands: [cmd1, cmd2, ...], if? }
 *                 -> выполнить одну или несколько команд от консоли подряд;
 *                 защищено общим rate-limit'ом (util/ConsoleRateLimiter) на случай
 *                 зацикливания в самом addon.yml
 *  - random       { actions: [ {action1}, {action2, weight: 3}, ... ] }
 *                 -> выполняется РОВНО ОДИН случайно выбранный action из списка;
 *                 weight (по умолчанию 1) задаёт относительный вес
 *  - sound_stop   { sound?, if? }   -> остановить конкретный звук игроку, либо ВСЕ
 *                 звуки сразу, если 'sound' не указан
 *  - discord_webhook { url, content, if? } -> POST-запрос в Discord webhook, ВСЕГДА
 *                 асинхронно (никогда не блокирует поток сервера); ошибки сети/Discord
 *                 только логируются, сервер из-за них не падает
 *  - call         { plugin, method, args, save_as?, scope?, if? } -> метод чужого плагина через рефлексию
 *  - openmenu     { menu, addon?, if? }                 -> открыть GUI-меню
 *  - closemenu    { if? }
 *  - page_next / page_prev {}       -> листать menus.*.list на страницу вперёд/назад;
 *                 работает только по клику ВНУТРИ меню (нужен InventoryClickEvent)
 *  - setvar       { key, value, scope?: player|global, if? }   -> сохранить переменную аддона
 *  - addvar       { key, amount, scope?: player|global, if? }  -> прибавить число к переменной
 *  - eco_give     { amount, if? }                       -> начислить игроку деньги (Vault)
 *  - eco_take     { amount, if? }                       -> списать деньги (Vault)
 *  - give_item    { material, amount?, name?, lore?, if? }      -> выдать предмет игроку в инвентарь
 *                 (material и amount тоже проходят через плейсхолдеры - можно писать
 *                 material: "{arg1}" amount: "{arg2}" для выдачи по аргументам команды)
 *  - play_sound   { sound, volume?, pitch?, if? }        -> проиграть звук игроку
 *  - particle     { particle, count?, offset_x?, offset_y?, offset_z?, extra?, if? }
 *                 -> частицы в позиции игрока (имя из org.bukkit.Particle)
 *  - teleport     { world?, x?, y?, z?, if? }            -> телепортировать игрока
 *  - title        { title?, subtitle?, actionbar?, fadein?, stay?, fadeout?, if? }
 *                 -> title/subtitle (тики fadein/stay/fadeout) и/или строка в actionbar;
 *                 любое из полей можно опустить (например, только actionbar)
 *  - delay        { ticks, actions, if? } -> выполнить вложенный список actions через
 *                 N тиков (20 = 1 сек.); {result} внутри вложенных actions НЕ наследуется
 *                 из actions до delay - отсчёт {result} начинается заново
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
            Map<String, Object> action = normalizeAction(raw);
            if (action == null) continue;
            boolean debug = ru.nanodev.nanoforge.util.ActionDebugger.isEnabled(currentAddonName);
            String debugType = debug ? String.valueOf(action.getOrDefault("type", "?")) : null;

            try {
                if (!ConditionChecker.check(action, player, event, currentAddon)) {
                    if (debug) {
                        Bukkit.getLogger().info(ru.nanodev.nanoforge.util.Messages.get("engine.debug.skipped",
                                "addon", currentAddonName, "type", debugType));
                    }
                    continue; // условие не прошло - пропускаем этот action, идём к следующему
                }
                if (debug) {
                    String playerPart = player != null ? " (игрок " + player.getName() + ")" : "";
                    Bukkit.getLogger().info(ru.nanodev.nanoforge.util.Messages.get("engine.debug.running",
                            "addon", currentAddonName, "type", debugType, "player", playerPart));
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
                        List<String> commands = new java.util.ArrayList<>();
                        Object commandsList = action.get("commands");
                        if (commandsList instanceof List) {
                            for (Object c : (List<?>) commandsList) commands.add(String.valueOf(c));
                        } else if (action.containsKey("command")) {
                            commands.add(String.valueOf(action.get("command")));
                        }
                        for (String rawCmd : commands) {
                            String cmd = withResult(PlaceholderUtil.apply(rawCmd, player, commandArgs), lastCallResult[0]);
                            if (!ru.nanodev.nanoforge.util.ConsoleRateLimiter.allow()) {
                                Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.console.rate-limit-exceeded",
                                        "max", ru.nanodev.nanoforge.util.ConsoleRateLimiter.MAX_PER_SECOND, "command", cmd));
                                continue;
                            }
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        }
                        break;
                    }
                    case "random": {
                        Object choicesRaw = action.get("actions");
                        if (!(choicesRaw instanceof List) || ((List<?>) choicesRaw).isEmpty()) break;
                        List<?> choices = (List<?>) choicesRaw;
                        double totalWeight = 0;
                        double[] weights = new double[choices.size()];
                        for (int i = 0; i < choices.size(); i++) {
                            Object c = choices.get(i);
                            double w = 1.0;
                            if (c instanceof Map && ((Map<?, ?>) c).containsKey("weight")) {
                                w = ru.nanodev.nanoforge.util.NumberUtil.toDouble(((Map<?, ?>) c).get("weight"), 1.0);
                            }
                            weights[i] = Math.max(0, w);
                            totalWeight += weights[i];
                        }
                        if (totalWeight <= 0) break;
                        double roll = Math.random() * totalWeight;
                        double acc = 0;
                        for (int i = 0; i < choices.size(); i++) {
                            acc += weights[i];
                            if (roll <= acc) {
                                Object chosen = choices.get(i);
                                if (chosen instanceof Map) {
                                    ActionRunner.run(java.util.Collections.singletonList(chosen), sender, event, menuManager, currentAddon, commandArgs);
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "sound_stop": {
                        if (player == null) break;
                        String soundName = String.valueOf(action.getOrDefault("sound", ""));
                        try {
                            if (soundName.isEmpty()) {
                                player.stopAllSounds();
                            } else {
                                player.stopSound(org.bukkit.Sound.valueOf(soundName.toUpperCase()));
                            }
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.sound-stop.unknown-sound", "sound", soundName));
                        }
                        break;
                    }
                    case "discord_webhook": {
                        String url = String.valueOf(action.getOrDefault("url", ""));
                        if (url.isEmpty() || "null".equals(url)) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.discord-webhook.missing-url"));
                            break;
                        }
                        String content = withResult(PlaceholderUtil.apply(
                                String.valueOf(action.getOrDefault("content", "")), player, commandArgs), lastCallResult[0]);
                        // HTTP-запрос ВСЕГДА асинхронно - никогда не блокируем поток сервера
                        // ожиданием ответа Discord (сеть может тормозить/не отвечать вообще).
                        Bukkit.getScheduler().runTaskAsynchronously(ru.nanodev.nanoforge.NanoForgePlugin.get(),
                                () -> sendDiscordWebhook(url, content));
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
                    case "script": {
                        String code = String.valueOf(action.getOrDefault("code", action.getOrDefault("script", "")));
                        code = PlaceholderUtil.apply(code, player, event, currentAddon, commandArgs);
                        Object result = ru.nanodev.nanoforge.script.NanoScript.eval(code, player, event, currentAddon);
                        lastCallResult[0] = result;
                        if (action.containsKey("save_as") && currentAddon != null) {
                            String key = String.valueOf(action.get("save_as"));
                            boolean global = "global".equalsIgnoreCase(String.valueOf(action.getOrDefault("scope", "player")));
                            String resultStr = result != null ? String.valueOf(result) : "";
                            if (global) currentAddon.getStorage().setGlobalVar(key, resultStr);
                            else if (player != null) currentAddon.getStorage().setVar(player, key, resultStr);
                        }
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
                    case "page_next":
                    case "page_prev": {
                        if (menuManager == null || player == null) break;
                        Object holderObj = (event instanceof org.bukkit.event.inventory.InventoryClickEvent)
                                ? ((org.bukkit.event.inventory.InventoryClickEvent) event).getInventory().getHolder()
                                : null;
                        if (!(holderObj instanceof ru.nanodev.nanoforge.gui.NanoMenuHolder)) break;
                        ru.nanodev.nanoforge.gui.NanoMenuHolder holder = (ru.nanodev.nanoforge.gui.NanoMenuHolder) holderObj;
                        int delta = "page_next".equals(type) ? 1 : -1;
                        menuManager.changePage(player, holder.getAddonName(), holder.getMenuKey(), delta, holder.isEditMode());
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
                        double amount = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("amount"), 0);
                        boolean global = "global".equalsIgnoreCase(String.valueOf(action.getOrDefault("scope", "player")));
                        if (global) currentAddon.getStorage().addGlobalVar(key, amount);
                        else if (player != null) currentAddon.getStorage().addVar(player, key, amount);
                        break;
                    }
                    case "eco_give": {
                        if (player != null) VaultBridge.deposit(player, ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("amount"), 0));
                        break;
                    }
                    case "eco_take": {
                        if (player != null) VaultBridge.withdraw(player, ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("amount"), 0));
                        break;
                    }
                    case "give_item": {
                        if (player == null) break;
                        String materialName = PlaceholderUtil.apply(
                                String.valueOf(action.getOrDefault("material", "STONE")), player, commandArgs).toUpperCase();
                        String amountRaw = PlaceholderUtil.apply(String.valueOf(action.getOrDefault("amount", "1")), player, commandArgs);
                        int amount = Math.max(1, (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(amountRaw, 1));
                        Material material = ru.nanodev.nanoforge.util.MaterialUtil.tryParse(materialName);
                        if (material == null) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.give-item.unknown-material", "material", materialName));
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
                        float volume = (float) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("volume"), 1.0);
                        float pitch = (float) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("pitch"), 1.0);
                        try {
                            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                            player.playSound(player.getLocation(), sound, volume, pitch);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.play-sound.unknown-sound", "sound", soundName));
                        }
                        break;
                    }
                    case "particle": {
                        if (player == null) break;
                        String particleName = String.valueOf(action.getOrDefault("particle", "FLAME")).toUpperCase();
                        int count = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("count"), 10);
                        double offsetX = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("offset_x"), 0.5);
                        double offsetY = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("offset_y"), 0.5);
                        double offsetZ = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("offset_z"), 0.5);
                        double extra = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("extra"), 0.0);
                        try {
                            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName);
                            player.spawnParticle(particle, player.getLocation(), count, offsetX, offsetY, offsetZ, extra);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.particle.unknown-particle", "particle", particleName));
                        }
                        break;
                    }
                    case "teleport": {
                        if (player == null) break;
                        org.bukkit.World world = action.containsKey("world")
                                ? Bukkit.getWorld(String.valueOf(action.get("world")))
                                : player.getWorld();
                        if (world == null) {
                            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.teleport.world-not-found"));
                            break;
                        }
                        double x = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("x"), player.getLocation().getX());
                        double y = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("y"), player.getLocation().getY());
                        double z = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("z"), player.getLocation().getZ());
                        player.teleport(new org.bukkit.Location(world, x, y, z));
                        break;
                    }
                    case "title": {
                        Player targetPlayer = resolveTargetPlayer(action, player, event);
                        if (targetPlayer == null) break;
                        if (action.containsKey("title") || action.containsKey("subtitle")) {
                            String titleText = action.containsKey("title")
                                    ? colorize(PlaceholderUtil.apply(String.valueOf(action.get("title")), targetPlayer, event, currentAddon, commandArgs))
                                    : "";
                            String subtitleText = action.containsKey("subtitle")
                                    ? colorize(PlaceholderUtil.apply(String.valueOf(action.get("subtitle")), targetPlayer, event, currentAddon, commandArgs))
                                    : "";
                            int fadeIn = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("fadein"), 10);
                            int stay = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("stay"), 70);
                            int fadeOut = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("fadeout"), 20);
                            targetPlayer.sendTitle(titleText, subtitleText, fadeIn, stay, fadeOut);
                        }
                        if (action.containsKey("actionbar")) {
                            String bar = withResult(colorize(PlaceholderUtil.apply(
                                    String.valueOf(action.get("actionbar")), targetPlayer, event, currentAddon, commandArgs)), lastCallResult[0]);
                            targetPlayer.spigot().sendMessage(
                                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    new net.md_5.bungee.api.chat.TextComponent(bar));
                        }
                        break;
                    }
                    case "delay": {
                        Object nested = action.get("actions");
                        if (!(nested instanceof List)) break;
                        long ticks = (long) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("ticks"), 20);
                        List<?> nestedActions = (List<?>) nested;
                        CommandSender fSender = sender;
                        Event fEvent = event;
                        MenuManager fMenuManager = menuManager;
                        Addon fAddon = currentAddon;
                        String[] fArgs = commandArgs;
                        Bukkit.getScheduler().runTaskLater(ru.nanodev.nanoforge.NanoForgePlugin.get(),
                                () -> ActionRunner.run(nestedActions, fSender, fEvent, fMenuManager, fAddon, fArgs),
                                ticks);
                        break;
                    }
                    case "potion_effect": {
                        Player effTarget = resolveTargetPlayer(action, player, event);
                        if (effTarget == null) break;
                        String effectName = String.valueOf(action.getOrDefault("effect", "SPEED")).toUpperCase();
                        int durationTicks = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("duration"), 100);
                        int amplifier = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("amplifier"), 0);
                        boolean ambient = Boolean.parseBoolean(String.valueOf(action.getOrDefault("ambient", "false")));
                        boolean particles = Boolean.parseBoolean(String.valueOf(action.getOrDefault("particles", "true")));
                        org.bukkit.potion.PotionEffectType typeObj = org.bukkit.potion.PotionEffectType.getByName(effectName);
                        if (typeObj != null) {
                            effTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(typeObj, durationTicks, amplifier, ambient, particles));
                        } else {
                            Bukkit.getLogger().warning("[NanoForge] Unknown potion effect: " + effectName);
                        }
                        break;
                    }
                    case "heal": {
                        Player healTarget = resolveTargetPlayer(action, player, event);
                        if (healTarget == null) break;
                        Object amountRaw = action.get("amount");
                        double amount = -1;
                        if (amountRaw instanceof Number) {
                            amount = ((Number) amountRaw).doubleValue();
                        } else if (amountRaw != null) {
                            String expr = PlaceholderUtil.apply(String.valueOf(amountRaw), healTarget, event, currentAddon, commandArgs);
                            amount = ru.nanodev.nanoforge.script.NanoScript.evalDouble(expr, healTarget, event, currentAddon);
                        }
                        double maxHealth = healTarget.getMaxHealth();
                        if (amount <= 0) {
                            healTarget.setHealth(maxHealth);
                        } else {
                            healTarget.setHealth(Math.min(maxHealth, healTarget.getHealth() + amount));
                        }
                        break;
                    }
                    case "feed": {
                        Player feedTarget = resolveTargetPlayer(action, player, event);
                        if (feedTarget == null) break;
                        Object amountRaw = action.get("amount");
                        double amount = -1;
                        if (amountRaw instanceof Number) {
                            amount = ((Number) amountRaw).doubleValue();
                        } else if (amountRaw != null) {
                            String expr = PlaceholderUtil.apply(String.valueOf(amountRaw), feedTarget, event, currentAddon, commandArgs);
                            amount = ru.nanodev.nanoforge.script.NanoScript.evalDouble(expr, feedTarget, event, currentAddon);
                        }
                        if (amount <= 0) {
                            feedTarget.setFoodLevel(20);
                        } else {
                            feedTarget.setFoodLevel(Math.min(20, (int) (feedTarget.getFoodLevel() + amount)));
                        }
                        break;
                    }
                    case "lightning": {
                        org.bukkit.Location loc = null;
                        if (action.containsKey("x") && action.containsKey("y") && action.containsKey("z")) {
                            org.bukkit.World w = action.containsKey("world")
                                    ? Bukkit.getWorld(String.valueOf(action.get("world")))
                                    : (player != null ? player.getWorld() : null);
                            if (w != null) {
                                double x = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("x"), 0);
                                double y = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("y"), 0);
                                double z = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("z"), 0);
                                loc = new org.bukkit.Location(w, x, y, z);
                            }
                        } else if (player != null) {
                            loc = player.getLocation();
                        }
                        if (loc != null && loc.getWorld() != null) {
                            boolean effectOnly = Boolean.parseBoolean(String.valueOf(action.getOrDefault("effect_only", "false")));
                            if (effectOnly) {
                                loc.getWorld().strikeLightningEffect(loc);
                            } else {
                                loc.getWorld().strikeLightning(loc);
                            }
                        }
                        break;
                    }
                    case "db_execute": {
                        if (currentAddon == null) break;
                        org.bukkit.configuration.ConfigurationSection dbSec = currentAddon.getDatabaseSection();
                        if (dbSec == null) {
                            Bukkit.getLogger().warning("[NanoForge] Addon " + currentAddon.getName() + " has no 'database:' section configured.");
                            break;
                        }
                        String sql = PlaceholderUtil.apply(String.valueOf(action.getOrDefault("sql", "")), player, commandArgs);
                        List<Object> params = new ArrayList<>();
                        Object rawParams = action.get("params");
                        if (rawParams instanceof List) {
                            for (Object p : (List<?>) rawParams) {
                                params.add(PlaceholderUtil.apply(String.valueOf(p), player, commandArgs));
                            }
                        }
                        ru.nanodev.nanoforge.database.DatabaseManager db = new ru.nanodev.nanoforge.database.DatabaseManager(currentAddon.getFile().getParentFile(), dbSec);
                        int affected = db.executeUpdate(sql, params);
                        lastCallResult[0] = affected;
                        if (action.containsKey("save_as")) {
                            String varKey = String.valueOf(action.get("save_as"));
                            boolean global = "global".equalsIgnoreCase(String.valueOf(action.get("scope")));
                            if (global || player == null) {
                                currentAddon.getStorage().setGlobalVar(varKey, String.valueOf(affected));
                            } else {
                                currentAddon.getStorage().setVar(player, varKey, String.valueOf(affected));
                            }
                        }
                        break;
                    }
                    case "db_query": {
                        if (currentAddon == null) break;
                        org.bukkit.configuration.ConfigurationSection dbSec = currentAddon.getDatabaseSection();
                        if (dbSec == null) {
                            Bukkit.getLogger().warning("[NanoForge] Addon " + currentAddon.getName() + " has no 'database:' section configured.");
                            break;
                        }
                        String sql = PlaceholderUtil.apply(String.valueOf(action.getOrDefault("sql", "")), player, commandArgs);
                        List<Object> params = new ArrayList<>();
                        Object rawParams = action.get("params");
                        if (rawParams instanceof List) {
                            for (Object p : (List<?>) rawParams) {
                                params.add(PlaceholderUtil.apply(String.valueOf(p), player, commandArgs));
                            }
                        }
                        ru.nanodev.nanoforge.database.DatabaseManager db = new ru.nanodev.nanoforge.database.DatabaseManager(currentAddon.getFile().getParentFile(), dbSec);
                        List<Map<String, Object>> rows = db.executeQuery(sql, params);
                        Object resultVal = rows.isEmpty() ? null : (rows.get(0).values().isEmpty() ? null : rows.get(0).values().iterator().next());
                        lastCallResult[0] = resultVal;
                        if (action.containsKey("save_as")) {
                            String varKey = String.valueOf(action.get("save_as"));
                            boolean global = "global".equalsIgnoreCase(String.valueOf(action.get("scope")));
                            String saveStr = resultVal == null ? "" : String.valueOf(resultVal);
                            if (global || player == null) {
                                currentAddon.getStorage().setGlobalVar(varKey, saveStr);
                            } else {
                                currentAddon.getStorage().setVar(player, varKey, saveStr);
                            }
                        }
                        break;
                    }
                    default:
                        Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.unknown-action-type", "type", type));
                }
            } catch (Throwable t) {
                // ЛЮБАЯ ошибка внутри одного action (кривой параметр, NPE, метод не нашёлся и т.д.)
                // не должна ронять всю цепочку и уж тем более всплывать наверх огромным
                // стектрейсом Bukkit'а - логируем одну понятную строку и идём к следующему action.
                String addonLabel = currentAddonName != null ? currentAddonName : "?";
                Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.action-error",
                        "addon", addonLabel, "type", action.getOrDefault("type", "?"), "error", t));
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

    /**
     * Вызывается ТОЛЬКО из уже асинхронного потока (см. discord_webhook выше) -
     * сам HttpURLConnection здесь синхронный, но это ок, т.к. поток и так не
     * поток сервера. Любая ошибка (сеть недоступна, вебхук удалён, таймаут)
     * просто логируется - падать всему серверу из-за недоступного Discord
     * совершенно ни к чему.
     */
    private static void sendDiscordWebhook(String url, String content) {
        try {
            String json = "{\"content\":\"" + escapeJson(content) + "\"}";
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            byte[] body = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            if (code >= 300) {
                Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.discord-webhook.bad-response", "code", code));
            }
            conn.disconnect();
        } catch (Exception e) {
            Bukkit.getLogger().warning(ru.nanodev.nanoforge.util.Messages.get("engine.discord-webhook.send-failed", "error", e));
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static Player resolvePlayer(CommandSender sender, Event event) {
        if (sender instanceof Player) return (Player) sender;
        if (event instanceof org.bukkit.event.player.PlayerEvent) {
            return ((org.bukkit.event.player.PlayerEvent) event).getPlayer();
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            org.bukkit.entity.Entity damager = ((org.bukkit.event.entity.EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof org.bukkit.entity.Projectile) {
                org.bukkit.projectiles.ProjectileSource s = ((org.bukkit.entity.Projectile) damager).getShooter();
                if (s instanceof Player) return (Player) s;
            }
            if (damager instanceof Player) return (Player) damager;
            org.bukkit.entity.Entity victim = ((org.bukkit.event.entity.EntityDamageByEntityEvent) event).getEntity();
            if (victim instanceof Player) return (Player) victim;
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageEvent) {
            org.bukkit.entity.Entity entity = ((org.bukkit.event.entity.EntityDamageEvent) event).getEntity();
            if (entity instanceof Player) return (Player) entity;
        }
        return null;
    }

    private static Player resolveTargetPlayer(Map<String, Object> action, Player defaultPlayer, Event event) {
        if (action != null && action.containsKey("target")) {
            String t = String.valueOf(action.get("target")).trim().toLowerCase();
            if ("attacker".equals(t) && event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.entity.Entity d = ((org.bukkit.event.entity.EntityDamageByEntityEvent) event).getDamager();
                if (d instanceof org.bukkit.entity.Projectile) {
                    org.bukkit.projectiles.ProjectileSource s = ((org.bukkit.entity.Projectile) d).getShooter();
                    if (s instanceof Player) return (Player) s;
                }
                if (d instanceof Player) return (Player) d;
            } else if ("victim".equals(t) && event instanceof org.bukkit.event.entity.EntityDamageEvent) {
                org.bukkit.entity.Entity ent = ((org.bukkit.event.entity.EntityDamageEvent) event).getEntity();
                if (ent instanceof Player) return (Player) ent;
            }
        }
        return defaultPlayer;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeAction(Object raw) {
        if (raw instanceof Map) return (Map<String, Object>) raw;
        if (!(raw instanceof String)) return null;

        String line = ((String) raw).trim();
        if (line.isEmpty() || line.startsWith("#")) return null;

        Map<String, Object> map = new java.util.HashMap<>();
        int colon = line.indexOf(':');
        if (colon == -1) {
            map.put("type", "script");
            map.put("code", line);
            return map;
        }

        String type = line.substring(0, colon).trim().toLowerCase();
        String rest = line.substring(colon + 1).trim();

        if ("actionbar".equals(type)) {
            map.put("type", "title");
            if (rest.contains(",")) {
                int c = rest.indexOf(',');
                String target = rest.substring(0, c).trim();
                String text = stripQuotes(rest.substring(c + 1).trim());
                map.put("target", target);
                map.put("actionbar", text);
            } else {
                map.put("actionbar", stripQuotes(rest));
            }
            return map;
        }

        if ("message".equals(type) || "broadcast".equals(type)) {
            map.put("type", type);
            if (rest.contains(",")) {
                int c = rest.indexOf(',');
                String target = rest.substring(0, c).trim();
                String text = stripQuotes(rest.substring(c + 1).trim());
                map.put("target", target);
                map.put("text", text);
            } else {
                map.put("text", stripQuotes(rest));
            }
            return map;
        }

        if ("console".equals(type)) {
            map.put("type", "console");
            map.put("command", stripQuotes(rest));
            return map;
        }

        if ("script".equals(type)) {
            map.put("type", "script");
            map.put("code", stripQuotes(rest));
            return map;
        }

        map.put("type", type);
        parseParamString(rest, map);
        return map;
    }

    private static void parseParamString(String rest, Map<String, Object> map) {
        if (rest == null || rest.isEmpty()) return;
        String[] parts = rest.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq != -1) {
                String key = part.substring(0, eq).trim().toLowerCase();
                String val = stripQuotes(part.substring(eq + 1).trim());
                map.put(key, val);
            } else {
                if (i == 0) {
                    if ("attacker".equalsIgnoreCase(part) || "victim".equalsIgnoreCase(part) || "player".equalsIgnoreCase(part)) {
                        map.put("target", part.toLowerCase());
                    } else if ("particle".equalsIgnoreCase(String.valueOf(map.get("type")))) {
                        map.put("particle", part);
                    } else {
                        map.put("target", part);
                    }
                }
            }
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            if (s.length() >= 2) return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String colorize(String s) {
        if (s == null) return "";
        // Поддержка HEX цветов <#RRGGBB> и &#RRGGBB для Paper/Spigot 1.16+
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<#([A-Fa-f0-9]{6})>|&#([A-Fa-f0-9]{6})").matcher(s);
            StringBuffer buffer = new StringBuffer();
            while (m.find()) {
                String hex = m.group(1) != null ? m.group(1) : m.group(2);
                try {
                    net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of("#" + hex);
                    m.appendReplacement(buffer, color.toString());
                } catch (Throwable ignored) {
                    m.appendReplacement(buffer, "");
                }
            }
            m.appendTail(buffer);
            s = buffer.toString();
        } catch (Throwable ignored) {}
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}

// by t.me/NanoDev_mc
