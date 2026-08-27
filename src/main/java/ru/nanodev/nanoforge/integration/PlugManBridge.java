package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;

/**
 * Определяет, установлен ли PlugMan или PlugManX (совместимый форк с тем же
 * синтаксисом команды), чтобы можно было предложить игроку/админу включить
 * выключенный целевой плагин аддона одной кнопкой в чате.
 *
 * NanoForge НЕ включает плагины напрямую сам (это зона ответственности
 * PlugMan'а - перезагрузка классов плагина "на лету" штука тонкая и рискованная,
 * лучше не изобретать это заново) - просто предлагает готовую команду.
 */
public class PlugManBridge {

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlugMan") != null
                || Bukkit.getPluginManager().getPlugin("PlugManX") != null;
    }

    /** Команда (без слэша) для включения плагина - синтаксис общий у PlugMan и PlugManX. */
    public static String enableCommand(String pluginName) {
        return "plugman enable " + pluginName;
    }
}

// by t.me/NanoDev_mc
