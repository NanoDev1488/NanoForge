package ru.nanodev.nanoforge.compiler.runtime;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Standalone entrypoint for plugins compiled by NanoForge AddonCompiler.
 */
public class StandaloneBootstrap extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("[Standalone] Starting plugin compiled with NanoForge...");
        try (InputStream in = getResource("addon.yml")) {
            if (in == null) {
                getLogger().severe("[Standalone] No embedded addon.yml found inside jar!");
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            getLogger().info("[Standalone] Loaded configuration for: " + yaml.getString("name", getName()));
        } catch (Throwable t) {
            getLogger().warning("[Standalone] Error initializing addon runtime: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("[Standalone] Plugin disabled.");
    }
}
