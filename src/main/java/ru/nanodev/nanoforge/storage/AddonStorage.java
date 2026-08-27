package ru.nanodev.nanoforge.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

/**
 * Хранилище переменных/кулдаунов конкретного аддона.
 * Файл: plugins/NanoForge/addons/<имя>/storage.yml
 *
 * players.<uuid>.<key>  - переменная привязанная к игроку
 * global.<key>           - глобальная переменная (одна на весь сервер)
 * cooldowns.<key>.<uuid> - таймстемп (millis) последнего успешного прохождения кулдауна
 */
public class AddonStorage {

    private final File file;
    private YamlConfiguration yaml;

    public AddonStorage(File addonFolder) {
        this.file = new File(addonFolder, "storage.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    // ---------- переменные ----------

    public String getVar(Player player, String key, String def) {
        return yaml.getString(playerPath(player, key), def);
    }

    public String getGlobalVar(String key, String def) {
        return yaml.getString("global." + key, def);
    }

    public double getVarNumber(Player player, String key, double def) {
        return yaml.getDouble(playerPath(player, key), def);
    }

    public double getGlobalVarNumber(String key, double def) {
        return yaml.getDouble("global." + key, def);
    }

    public void setVar(Player player, String key, String value) {
        yaml.set(playerPath(player, key), value);
        save();
    }

    public void setGlobalVar(String key, String value) {
        yaml.set("global." + key, value);
        save();
    }

    public double addVar(Player player, String key, double amount) {
        double newValue = getVarNumber(player, key, 0) + amount;
        yaml.set(playerPath(player, key), newValue);
        save();
        return newValue;
    }

    public double addGlobalVar(String key, double amount) {
        double newValue = getGlobalVarNumber(key, 0) + amount;
        yaml.set("global." + key, newValue);
        save();
        return newValue;
    }

    // ---------- кулдауны ----------

    /**
     * @return оставшееся время кулдауна в секундах (0 если кулдаун прошёл / не установлен)
     */
    public long remainingCooldownSeconds(Player player, String cooldownKey, long cooldownSeconds) {
        long last = yaml.getLong("cooldowns." + cooldownKey + "." + player.getUniqueId(), 0);
        long elapsedMs = System.currentTimeMillis() - last;
        long remainingMs = (cooldownSeconds * 1000L) - elapsedMs;
        return remainingMs > 0 ? (remainingMs / 1000L) + 1 : 0;
    }

    public void markCooldown(Player player, String cooldownKey) {
        yaml.set("cooldowns." + cooldownKey + "." + player.getUniqueId(), System.currentTimeMillis());
        save();
    }

    // ---------- утиль ----------

    private String playerPath(Player player, String key) {
        UUID id = player.getUniqueId();
        return "players." + id + "." + key;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning("[NanoForge] Не удалось сохранить storage.yml: " + e.getMessage());
        }
    }
}

// by t.me/NanoDev_mc
