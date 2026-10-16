package ru.nanodev.nanoforge.engine;

import org.bukkit.entity.Player;
import ru.nanodev.nanoforge.integration.PlaceholderAPIBridge;

/**
 * Подстановка простых плейсхолдеров в текст actions.
 * Работают в message/broadcast/console/call args - везде, где ActionRunner
 * обрабатывает строки. Если игрок неизвестен (действие вызвано не из
 * контекста игрока), плейсхолдеры игрока просто не заменяются.
 *
 * Поддерживаемые: {player} {uuid} {world} {x} {y} {z} {health} {level}
 * {args} {arg1} {arg2} ... (аргументы команды аддона, если они есть)
 *
 * Дополнительно, если на сервере установлен PlaceholderAPI, его собственные
 * %плейсхолдеры% (любого другого плагина) тоже подставляются - последним
 * шагом, после всех своих {фигурноскобочных}.
 */
public class PlaceholderUtil {

    public static String apply(String text, Player player) {
        return apply(text, player, null, null, null);
    }

    public static String apply(String text, Player player, String[] commandArgs) {
        return apply(text, player, null, null, commandArgs);
    }

    public static String apply(String text, Player player, org.bukkit.event.Event event,
                               ru.nanodev.nanoforge.model.Addon addon, String[] commandArgs) {
        if (text == null) return null;

        String result = text;
        if (player != null) {
            // каждое поле трогаем только если его плейсхолдер реально есть в тексте -
            // не только ради экономии (getLocation() создаёт новый объект на каждый вызов),
            // но и ради устойчивости: некоторые поля Player могут быть недоступны/null
            // в отдельных контекстах, и незачем их дёргать, если текст их не просит.
            if (result.contains("{player}")) result = result.replace("{player}", player.getName());
            if (result.contains("{uuid}")) result = result.replace("{uuid}", player.getUniqueId().toString());
            if (result.contains("{world}")) result = result.replace("{world}", player.getWorld().getName());
            if (result.contains("{x}") || result.contains("{y}") || result.contains("{z}")) {
                org.bukkit.Location loc = player.getLocation();
                result = result
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()));
            }
            if (result.contains("{health}")) result = result.replace("{health}", String.valueOf((int) player.getHealth()));
            if (result.contains("{level}")) result = result.replace("{level}", String.valueOf(player.getLevel()));
        }

        if (commandArgs != null) {
            result = result.replace("{args}", String.join(" ", commandArgs));
            for (int i = 0; i < commandArgs.length; i++) {
                result = result.replace("{arg" + (i + 1) + "}", commandArgs[i]);
            }
            // {value} - специально для кликов по сгенерированным пунктам меню (menus.*.list),
            // где "значение" элемента списка (имя игрока и т.п.) передаётся первым commandArg -
            // см. MenuManager.onClick(): ActionRunner.run(..., new String[]{generatedValue})
            if (commandArgs.length > 0) {
                result = result.replace("{value}", commandArgs[0]);
            }
        }

        // Серверные плейсхолдеры (не зависят от наличия контекста игрока)
        if (result.contains("{") && result.contains("}")) {
            if (containsIgnoreCase(result, "{maxplayers}") || containsIgnoreCase(result, "{max_players}")) {
                int max = org.bukkit.Bukkit.getServer() != null ? org.bukkit.Bukkit.getMaxPlayers() : 0;
                result = replaceIgnoreCase(result, "{maxplayers}", String.valueOf(max));
                result = replaceIgnoreCase(result, "{max_players}", String.valueOf(max));
            }
            if (containsIgnoreCase(result, "{online}") || containsIgnoreCase(result, "{online_players}")) {
                int online = org.bukkit.Bukkit.getServer() != null ? org.bukkit.Bukkit.getOnlinePlayers().size() : 0;
                result = replaceIgnoreCase(result, "{online_players}", String.valueOf(online));
                result = replaceIgnoreCase(result, "{online}", String.valueOf(online));
            }
        }

        // NanoScript динамические выражения {script:...} или {event.damage * 0.35}
        if (result.contains("{") && result.contains("}")) {
            int startIdx = 0;
            while ((startIdx = result.indexOf('{', startIdx)) != -1) {
                int endIdx = result.indexOf('}', startIdx);
                if (endIdx == -1) break;
                String tag = result.substring(startIdx + 1, endIdx).trim();
                boolean isScriptTag = tag.startsWith("script:") || tag.startsWith("calc:");
                boolean hasExpressionChars = tag.contains("*") || tag.contains("/") || tag.contains("+")
                        || tag.contains("-") || tag.contains(".") || tag.contains("(") || tag.contains("%");

                if (isScriptTag || hasExpressionChars) {
                    String expr = isScriptTag ? tag.substring(tag.indexOf(':') + 1).trim() : tag;
                    try {
                        String evaluated = ru.nanodev.nanoforge.script.NanoScript.evalString(expr, player, event, addon);
                        if (evaluated != null && !evaluated.isEmpty()) {
                            result = result.substring(0, startIdx) + evaluated + result.substring(endIdx + 1);
                            startIdx += evaluated.length();
                            continue;
                        }
                    } catch (Throwable ignored) {}
                }
                startIdx = endIdx + 1;
            }
        }

        // PlaceholderAPI - отдельный синтаксис (%...%), конфликтов с {...} быть не может
        result = PlaceholderAPIBridge.apply(result, player);

        return result;
    }

    private static boolean containsIgnoreCase(String src, String what) {
        return src.toLowerCase().contains(what.toLowerCase());
    }

    private static String replaceIgnoreCase(String src, String target, String replacement) {
        return java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(target), java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(src)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
    }
}

// by t.me/NanoDev_mc
