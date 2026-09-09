package ru.nanodev.nanoforge;

import org.bukkit.plugin.java.JavaPlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.command.NanoCommand;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.metrics.Metrics;
import ru.nanodev.nanoforge.util.StartupChecks;

import java.io.File;

public class NanoForgePlugin extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 33903;

    private static NanoForgePlugin instance;
    private AddonManager addonManager;
    private MenuManager menuManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        instance = this;

        StartupChecks.printBanner(this);
        StartupChecks.checkServerVersion(this);

        File addonsFolder = new File(getDataFolder(), "addons");
        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }

        this.addonManager = new AddonManager(this, addonsFolder);
        this.menuManager = new MenuManager(this, addonManager);
        this.addonManager.loadAll();

        NanoCommand nanoCommand = new NanoCommand(this, addonManager, menuManager);
        getCommand("nano").setExecutor(nanoCommand);
        getCommand("nano").setTabCompleter(nanoCommand);

        setupMetrics();

        getLogger().info("NanoForge запущен. Загружено аддонов: " + addonManager.getAddons().size());
    }

    private void setupMetrics() {
        metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        metrics.addCustomChart(new Metrics.SimplePie("addon_count_bucket", () -> {
            int count = addonManager.getAddons().size();
            if (count == 0) return "0";
            if (count <= 5) return "1-5";
            if (count <= 20) return "6-20";
            return "21+";
        }));

        metrics.addCustomChart(new Metrics.AdvancedPie("addon_types", () -> {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (ru.nanodev.nanoforge.model.Addon addon : addonManager.getAddons()) {
                counts.merge(addon.getType().name(), 1, Integer::sum);
            }
            return counts;
        }));
    }

    @Override
    public void onDisable() {
        if (addonManager != null) {
            addonManager.disableAll();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
    }

    public static NanoForgePlugin get() {
        return instance;
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }
}

// by t.me/NanoDev_mc
