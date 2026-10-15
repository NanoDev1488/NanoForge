package ru.nanodev.nanoforge.model;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.nanodev.nanoforge.storage.AddonStorage;

import java.io.File;
import java.util.List;

/**
 * Одна единица - либо "addon" (дополнение к другому плагину),
 * либо "new" (самостоятельный мини-плагин), описанная в .yml файле.
 */
public class Addon {

    public enum Type { ADDON, NEW }

    private final File file;       // <folder>/addon.yml
    private final File folder;     // plugins/NanoForge/addons/<имя>/
    private final YamlConfiguration yaml;

    private String name;
    private Type type;
    private String targetPlugin; // только для ADDON
    private boolean enabled;

    public Addon(File file, YamlConfiguration yaml) {
        this.file = file;
        this.folder = file.getParentFile();
        this.yaml = yaml;
        reload();
    }

    /** Файл с дампом API целевого плагина: <folder>/target-api.txt (только для type: addon) */
    public File getTargetApiFile() {
        return new File(folder, "target-api.txt");
    }

    public File getFolder() {
        return folder;
    }

    private AddonStorage storage;

    /** Хранилище переменных/кулдаунов этого аддона (создаётся лениво). */
    public AddonStorage getStorage() {
        if (storage == null) {
            storage = new AddonStorage(folder);
        }
        return storage;
    }

    public void reload() {
        this.name = yaml.getString("name", folder.getName());
        this.type = "new".equalsIgnoreCase(yaml.getString("type", "addon")) ? Type.NEW : Type.ADDON;
        this.targetPlugin = yaml.getString("target", null);
        this.enabled = yaml.getBoolean("enabled", false);
    }

    public void save() {
        yaml.set("name", name);
        yaml.set("type", type == Type.NEW ? "new" : "addon");
        if (targetPlugin != null) yaml.set("target", targetPlugin);
        yaml.set("enabled", enabled);
        try {
            yaml.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getRequiredAddons() {
        return yaml.getStringList("requires");
    }

    public List<String> getCommandKeys() {
        if (!yaml.isConfigurationSection("commands")) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(yaml.getConfigurationSection("commands").getKeys(false));
    }

    public List<String> getEventKeys() {
        if (!yaml.isConfigurationSection("events")) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(yaml.getConfigurationSection("events").getKeys(false));
    }

    public List<?> getCommandActions(String cmdKey) {
        return yaml.getList("commands." + cmdKey + ".actions", java.util.Collections.emptyList());
    }

    public List<?> getEventActions(String eventKey) {
        return yaml.getList("events." + eventKey + ".actions", java.util.Collections.emptyList());
    }

    public String getCommandDescription(String cmdKey) {
        return yaml.getString("commands." + cmdKey + ".description", "");
    }

    public List<String> getCommandAliases(String cmdKey) {
        return yaml.getStringList("commands." + cmdKey + ".aliases");
    }

    public List<String> getCommandTabComplete(String cmdKey) {
        return yaml.getStringList("commands." + cmdKey + ".tab_complete");
    }

    // ---------- фоновые задачи (tasks) ----------

    public List<String> getTaskKeys() {
        if (!yaml.isConfigurationSection("tasks")) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(yaml.getConfigurationSection("tasks").getKeys(false));
    }

    public long getTaskInterval(String taskKey) {
        // default interval: 20 ticks (1 sec)
        return yaml.getLong("tasks." + taskKey + ".interval", 20L);
    }

    public long getTaskDelay(String taskKey) {
        return yaml.getLong("tasks." + taskKey + ".delay", 0L);
    }

    public List<?> getTaskActions(String taskKey) {
        return yaml.getList("tasks." + taskKey + ".actions", java.util.Collections.emptyList());
    }

    // ---------- меню (GUI) ----------

    public List<String> getMenuKeys() {
        if (!yaml.isConfigurationSection("menus")) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(yaml.getConfigurationSection("menus").getKeys(false));
    }

    public String getMenuTitle(String menuKey) {
        return yaml.getString("menus." + menuKey + ".title", menuKey);
    }

    public int getMenuRows(String menuKey) {
        int rows = yaml.getInt("menus." + menuKey + ".rows", 3);
        return Math.max(1, Math.min(6, rows));
    }

    /** Слоты меню как заданы в YAML: ключ - номер слота (строкой), значение - его конфиг. */
    public org.bukkit.configuration.ConfigurationSection getMenuItemsSection(String menuKey) {
        return yaml.getConfigurationSection("menus." + menuKey + ".items");
    }

    public YamlConfiguration getYaml() {
        return yaml;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public String getTargetPlugin() {
        return targetPlugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

// by t.me/NanoDev_mc
