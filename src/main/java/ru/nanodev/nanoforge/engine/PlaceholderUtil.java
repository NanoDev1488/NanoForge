package ru.nanodev.nanoforge.engine;

import org.bukkit.entity.Player;

/**
 * Подстановка простых плейсхолдеров в текст actions.
 * Работают в message/broadcast/console/call args - везде, где ActionRunner
 * обрабатывает строки. Если игрок неизвестен (действие вызвано не из
 * контекста игрока), плейсхолдеры игрока просто не заменяются.
 *
 * Поддерживаемые: {player} {uuid} {world} {x} {y} {z} {health} {level}
 */
public class PlaceholderUtil {

    public static String apply(String text, Player player) {
        if (text == null) return null;
        if (player == null) return text;

        return text
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()))
                .replace("{health}", String.valueOf((int) player.getHealth()))
                .replace("{level}", String.valueOf(player.getLevel()));
    }
}

// by t.me/NanoDev_mc
