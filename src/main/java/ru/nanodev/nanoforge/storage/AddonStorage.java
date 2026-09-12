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
        return parseDouble(yaml.get(playerPath(player, key)), def);
    }

    public double getGlobalVarNumber(String key, double def) {
        return parseDouble(yaml.get("global." + key), def);
    }

    /** Глобальные переменные аддона как есть (ключ -> строковое значение) - для отладки (/nano vars). */
    public java.util.Map<String, Object> getAllGlobalVars() {
        if (!yaml.isConfigurationSection("global")) return java.util.Collections.emptyMap();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String key : yaml.getConfigurationSection("global").getKeys(false)) {
            result.put(key, yaml.get("global." + key));
        }
        return result;
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
        if (remainingMs <= 0) return 0;
        // округление ВВЕРХ (потолок), а не "целые секунды + 1" - иначе остаток, который
        // ровно кратен 1000мс (например, сразу после markCooldown), завышался на целую
        // секунду (30000мс -> 30/1 -> 31 вместо ожидаемых 30).
        return (remainingMs + 999) / 1000L;
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

    /**
     * setVar всегда пишет значение как СТРОКУ (это action-level API из YAML,
     * там всё строки), поэтому обычный YamlConfiguration.getDouble() тут не
     * годится - он читает только реально числовые узлы конфига и молча
     * возвращает default для строкового "150". Разбираем вручную.
     */
    private double parseDouble(Object raw, double def) {
        if (raw == null) return def;
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return def;
        }
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
