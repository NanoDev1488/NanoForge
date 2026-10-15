package ru.nanodev.nanoforge.manager;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Виртуальный экземпляр плагина Bukkit для каждого включенного аддона NanoForge.
 * Отображается в /plugins как полноценный плагин с префиксом NanoForge-<ИмяАддона>.
 */
public class VirtualAddonPlugin extends PluginBase {

    private final Addon addon;
    private final PluginDescriptionFile description;
    private final Logger logger;
    private boolean enabled;

    public VirtualAddonPlugin(Addon addon) {
        this.addon = addon;
        String pluginName = "NanoForge-" + addon.getName();
        this.description = new PluginDescriptionFile(
                pluginName,
                "1.0.0",
                "ru.nanodev.nanoforge.addon." + addon.getName()
        );
        this.logger = Logger.getLogger(pluginName);
        this.enabled = addon.isEnabled();
    }

    public VirtualAddonPlugin(String pluginName, String version, String descriptionText) {
        this.addon = null;
        this.description = new PluginDescriptionFile(
                pluginName,
                version != null ? version : "1.0.0",
                "ru.nanodev.nanoforge.virtual." + pluginName
        );
        this.logger = Logger.getLogger(pluginName);
        this.enabled = true;
    }

    @Override
    public File getDataFolder() {
        return addon != null ? addon.getFolder() : new File(NanoForgePlugin.get().getDataFolder(), "virtual_" + getName());
    }

    @Override
    public PluginDescriptionFile getDescription() {
        return description;
    }

    @Override
    public FileConfiguration getConfig() {
        return addon != null ? addon.getYaml() : null;
    }

    @Override
    public InputStream getResource(String filename) {
        return null;
    }

    @Override
    public void saveConfig() {
        addon.save();
    }

    @Override
    public void saveDefaultConfig() {
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
    }

    @Override
    public void reloadConfig() {
        addon.reload();
    }

    @Override
    public PluginLoader getPluginLoader() {
        try {
            return NanoForgePlugin.get().getPluginLoader();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public boolean isNaggable() {
        return false;
    }

    @Override
    public void setNaggable(boolean canNag) {
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return null;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return null;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    public Addon getAddon() {
        return addon;
    }
}

// by t.me/NanoDev_mc
