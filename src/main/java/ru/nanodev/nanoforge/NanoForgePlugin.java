package ru.nanodev.nanoforge;

import org.bukkit.plugin.java.JavaPlugin;
import ru.nanodev.nanoforge.manager.AddonManager;
import ru.nanodev.nanoforge.command.NanoCommand;
import ru.nanodev.nanoforge.gui.MenuManager;
import ru.nanodev.nanoforge.metrics.Metrics;
import ru.nanodev.nanoforge.update.UpdateChecker;
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

        saveDefaultConfig();
        ru.nanodev.nanoforge.util.Messages.init(this);

        StartupChecks.printBanner(this);
        StartupChecks.checkServerVersion(this);
        StartupChecks.checkOptionalDependencies(this);

        File addonsFolder = new File(getDataFolder(), "addons");
        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }

        this.addonManager = new AddonManager(this, addonsFolder);
        this.menuManager = new MenuManager(this, addonManager);
        this.addonManager.loadAll();

        getServer().getPluginManager().registerEvents(
                new ru.nanodev.nanoforge.dynamic.NpcTriggerListener(addonManager), this);

        ru.nanodev.nanoforge.wizard.AddonWizard addonWizard = new ru.nanodev.nanoforge.wizard.AddonWizard(this, addonManager);
        getServer().getPluginManager().registerEvents(addonWizard, this);

        NanoCommand nanoCommand = new NanoCommand(this, addonManager, menuManager, addonWizard);
        getCommand("nano").setExecutor(nanoCommand);
        getCommand("nano").setTabCompleter(nanoCommand);

        setupMetrics();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // Проверка ЗДЕСЬ, а не внутри NanoForgeExpansion.tryRegister() - это принципиально:
            // NanoForgeExpansion extends PlaceholderExpansion, поэтому сама попытка загрузить
            // класс NanoForgeExpansion (даже просто чтобы вызвать статический метод) заставляет
            // JVM тут же грузить и его родителя PlaceholderExpansion. Без PlaceholderAPI на
            // сервере это падает с NoClassDefFoundError ещё ДО того, как успеет отработать любая
            // проверка внутри самого tryRegister() - поэтому проверка на PlaceholderAPI обязана
            // стоять СНАРУЖИ, до первого упоминания класса NanoForgeExpansion где бы то ни было.
            ru.nanodev.nanoforge.integration.NanoForgeExpansion.tryRegister(this);
        }

        getLogger().info(ru.nanodev.nanoforge.util.Messages.get("plugin.enabled-log", "count", addonManager.getAddons().size()));

        if (getConfig().getBoolean("update-checker.enabled", true)) {
            boolean notifyConsole = getConfig().getBoolean("update-checker.notify-console", true);
            UpdateChecker.checkAsync(this, result -> {
                // "Установлена актуальная версия" не является предупреждением - печатаем
                // его в консоль только если notify-console явно включён (по умолчанию да);
                // находка реальной новой версии (или сетевая ошибка) печатается внутри
                // UpdateChecker.performCheck() через log.warning/log.info независимо от этого флага.
                if (notifyConsole) {
                    getLogger().info(ru.nanodev.nanoforge.util.Messages.get("plugin.autoupdate-log", "result", result));
                }
            });
        }
    }

    /** Ручной запуск той же проверки - используется командой /nano update. */
    public void checkForUpdates(java.util.function.Consumer<String> resultMessage) {
        UpdateChecker.checkAsync(this, resultMessage);
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
