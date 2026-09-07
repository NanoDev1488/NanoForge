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
        return apply(text, player, null);
    }

    public static String apply(String text, Player player, String[] commandArgs) {
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
        }

        // PlaceholderAPI - отдельный синтаксис (%...%), конфликтов с {...} быть не может
        result = PlaceholderAPIBridge.apply(result, player);

        return result;
    }
}

// by t.me/NanoDev_mc
