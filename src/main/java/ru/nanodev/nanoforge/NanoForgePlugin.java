package ru.nanodev.nanoforge;

import org.bukkit.plugin.java.JavaPlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.command.NanoCommand;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.util.StartupChecks;

import java.io.File;

public class NanoForgePlugin extends JavaPlugin {

    private static NanoForgePlugin instance;
    private AddonManager addonManager;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        instance = this;

        StartupChecks.printBanner(this);
        StartupChecks.checkServerVersion(this);

        // Папка plugins/NanoForge/addons/ - тут лежат папки-аддоны (addon.yml + target-api.txt)
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

        getLogger().info("NanoForge запущен. Загружено аддонов: " + addonManager.getAddons().size());
    }

    @Override
    public void onDisable() {
        if (addonManager != null) {
            addonManager.disableAll();
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
