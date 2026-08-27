package ru.nanodev.nanoforge.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Кликабельные кнопки в чате поверх bungee chat API (net.md_5.bungee.api.chat),
 * который идёт в комплекте со Spigot API - никакой отдельной зависимости не нужно.
 */
public class ChatButtons {

    /**
     * Шлёт игроку строку с кликабельной кнопкой в конце, запускающей команду.
     * @param player   кому отправить
     * @param prefix   обычный текст перед кнопкой (может быть пустым)
     * @param buttonText текст самой кнопки, например "[Включить]"
     * @param command  команда БЕЗ слэша, которая выполнится по клику (от имени игрока)
     * @param hover    текст подсказки при наведении (может быть null)
     */
    public static void sendRunCommandButton(Player player, String prefix, String buttonText, String command, String hover) {
        TextComponent line = new TextComponent(ChatColor.translateAlternateColorCodes('&', prefix));

        TextComponent button = new TextComponent(ChatColor.translateAlternateColorCodes('&', buttonText));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command));
        if (hover != null) {
            button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(ChatColor.translateAlternateColorCodes('&', hover))));
        }

        line.addExtra(button);
        player.spigot().sendMessage(line);
    }
}

// by t.me/NanoDev_mc
