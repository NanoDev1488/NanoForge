package ru.nanodev.nanoforge.manager;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Регрессионный тест на реальный баг, найденный на живом Paper-сервере (не юнит-моками):
 * {@code /nano reload} гасил "enabled: true" в addon.yml всех аддонов, потому что
 * {@code disableAll()} (вызываемая из {@code reloadAll()}) раньше персистила
 * {@code enabled=false} на диск перед тем, как {@code loadAll()} перечитывал этот же файл -
 * то есть reload читал уже испорченное собственной рукой состояние.
 *
 * NanoForgePlugin - конкретный класс (не интерфейс), поэтому мокается через Mockito
 * (Objenesis создаёт инстанс в обход реального конструктора JavaPlugin). Bukkit
 * мокается статически, чтобы AddonManager.resolveCommandMap()/warnIfTargetMissing()
 * не падали без реального сервера.
 */
class AddonManagerTest {

    @TempDir
    File tempDir;

    private MockedStatic<Bukkit> bukkitMock;
    private NanoForgePlugin plugin;
    private AddonManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(NanoForgePlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getName()).thenReturn("NanoForge");

        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        // у мок-Server нет реального поля "commandMap" - resolveCommandMap() поймает
        // исключение рефлексии и вернёт null, дальше AddonManager просто не регистрирует
        // команды (проверка "if (commandMap != null)"), это нормально для этого теста

        manager = new AddonManager(plugin, tempDir);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void reloadPreservesEnabledStateOnDisk() {
        manager.createNew("TestAddon");
        assertThat(manager.enable("TestAddon")).isTrue();

        File addonYml = new File(tempDir, "TestAddon/addon.yml");
        assertThat(YamlConfiguration.loadConfiguration(addonYml).getBoolean("enabled")).isTrue();

        manager.reloadAll();

        // главная проверка бага: после reload флаг НЕ должен погаснуть ни в памяти, ни на диске
        assertThat(manager.get("TestAddon").isEnabled()).isTrue();
        assertThat(YamlConfiguration.loadConfiguration(addonYml).getBoolean("enabled")).isTrue();
    }

    @Test
    void explicitDisableStillPersistsDisabledState() {
        // это НЕ должно сломаться встречным фиксом - обычный /nano disable по-прежнему
        // обязан гасить и сохранять enabled=false, это разные сценарии
        manager.createNew("TestAddon2");
        manager.enable("TestAddon2");

        assertThat(manager.disable("TestAddon2")).isTrue();

        File addonYml = new File(tempDir, "TestAddon2/addon.yml");
        assertThat(YamlConfiguration.loadConfiguration(addonYml).getBoolean("enabled")).isFalse();
        assertThat(manager.get("TestAddon2").isEnabled()).isFalse();
    }

    @Test
    void reloadPicksUpDisabledAddonAsStillDisabled() {
        // симметричная проверка: если аддон был выключен, reload не должен его случайно включить
        manager.createNew("TestAddon3");
        // не включаем

        manager.reloadAll();

        assertThat(manager.get("TestAddon3").isEnabled()).isFalse();
    }

    // ---------- duplicate ----------

    @Test
    void duplicateCopiesYamlContentUnderNewName() {
        manager.createNew("Original");
        manager.enable("Original");

        Addon copy = manager.duplicate("Original", "Copy");

        assertThat(copy).isNotNull();
        assertThat(copy.getName()).isEqualTo("Copy");
        // копия всегда стартует выключенной, даже если оригинал был включён
        assertThat(copy.isEnabled()).isFalse();
        assertThat(new File(tempDir, "Copy/addon.yml")).exists();
    }

    @Test
    void duplicateFailsWhenSourceMissing() {
        assertThat(manager.duplicate("NoSuchAddon", "Copy")).isNull();
    }

    @Test
    void duplicateFailsWhenTargetNameAlreadyExists() {
        manager.createNew("A");
        manager.createNew("B");
        assertThat(manager.duplicate("A", "B")).isNull();
    }

    // ---------- export / import ----------

    @Test
    void exportCreatesZipContainingAddonYml() throws java.io.IOException {
        manager.createNew("ExportMe");

        File zip = manager.exportAddon("ExportMe");

        assertThat(zip).exists();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            assertThat(zf.getEntry("addon.yml")).isNotNull();
        }
    }

    @Test
    void exportReturnsNullForMissingAddon() throws java.io.IOException {
        assertThat(manager.exportAddon("NoSuchAddon")).isNull();
    }

    @Test
    void importRoundTripRestoresAddon() throws java.io.IOException {
        manager.createNew("RoundTrip");
        File zip = manager.exportAddon("RoundTrip");

        // AddonManager ищет imports/ рядом с addons/ (родитель addonsFolder), поэтому
        // используем отдельный менеджер с чистыми путями addons2/ + imports/ рядом
        File addonsSubfolder = new File(tempDir, "addons2");
        addonsSubfolder.mkdirs();
        AddonManager importManager = new AddonManager(plugin, addonsSubfolder);

        File importsDir = new File(tempDir, "imports");
        importsDir.mkdirs();
        java.nio.file.Files.copy(zip.toPath(), new File(importsDir, "roundtrip.zip").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Addon imported = importManager.importAddon("roundtrip.zip", "Imported");

        assertThat(imported).isNotNull();
        assertThat(imported.getName()).isEqualTo("Imported");
        assertThat(imported.isEnabled()).isFalse();
    }

    @Test
    void importThrowsWhenFileMissing() {
        assertThatThrownBy(() -> manager.importAddon("does-not-exist.zip", "Whatever"))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void importFailsWhenNameAlreadyTaken() throws java.io.IOException {
        manager.createNew("Existing");
        assertThat(manager.importAddon("anything.zip", "Existing")).isNull();
    }
}

// by t.me/NanoDev_mc
