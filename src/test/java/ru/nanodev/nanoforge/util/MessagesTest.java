package ru.nanodev.nanoforge.util;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты на {@link Messages} - единую точку доступа к текстам плагина (messages.yml).
 *
 * Plugin - интерфейс, поэтому мокается напрямую через Mockito (как в остальных тестах
 * этого пакета/проекта, см. например NanoForgeExpansionTest). {@code saveResource(...)}
 * на моке ничего не делает (не кладёт файл на диск) - для теста загрузки/reload файл
 * messages.yml в {@code @TempDir} создаётся вручную там, где это нужно для сценария.
 *
 * {@link Messages#resetForTests()} сбрасывает internal-кэш между тестами, чтобы они
 * не зависели от порядка запуска друг друга (кэш статический).
 */
class MessagesTest {

    @TempDir
    File tempDir;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        Messages.resetForTests();
        plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
    }

    @AfterEach
    void tearDown() {
        Messages.resetForTests();
    }

    // ---------- загрузка ключа ----------

    @Test
    void getLoadsKeyFromBundledDefaultWhenNotInitialized() {
        // Messages.init() ещё не вызывался (как в юнит-тестах, которые создают классы
        // напрямую, минуя NanoForgePlugin#onEnable) - get() всё равно должен вернуть
        // реальный текст из дефолтного messages.yml, зашитого в classpath/jar.
        String text = Messages.get("command.list.header");
        assertThat(text).isEqualTo("Аддоны NanoForge");
    }

    @Test
    void getTranslatesKnownNestedKey() {
        String text = Messages.get("manager.enabled-log", "addon", "TestAddon");
        assertThat(text).isEqualTo("[NanoForge] Включён: TestAddon");
    }

    // ---------- {placeholder} подстановка ----------

    @Test
    void placeholdersAreSubstituted() {
        String text = Messages.get("manager.load-failed", "folder", "myfolder", "error", "boom");
        assertThat(text)
                .contains("myfolder")
                .contains("boom")
                .doesNotContain("{folder}")
                .doesNotContain("{error}");
    }

    @Test
    void multiplePlaceholderPairsAllApply() {
        String text = Messages.get("engine.console.rate-limit-exceeded", "max", "5", "command", "say hi");
        assertThat(text).contains("5").contains("say hi");
    }

    @Test
    void missingPlaceholderPairLeavesPlaceholderTokenUntouched() {
        // плейсхолдер, для которого НЕ передали пару имя/значение, остаётся как есть -
        // substitution делает только явно переданные пары, а не "всё, что найдёт".
        String text = Messages.get("manager.load-failed", "folder", "myfolder");
        assertThat(text).contains("myfolder").contains("{error}");
    }

    // ---------- fallback при отсутствии ключа ----------

    @Test
    void missingKeyReturnsPlaceholderMarkerInsteadOfThrowing() {
        // get() на отсутствующем ключе логирует warning через Bukkit.getLogger() -
        // мокаем Bukkit статически, иначе в юнит-тесте (без реального сервера) это упадёт NPE.
        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            bukkitMock.when(org.bukkit.Bukkit::getLogger).thenReturn(java.util.logging.Logger.getLogger("test"));

            String text = Messages.get("this.key.does.not.exist");
            assertThat(text).isEqualTo("§c[missing message: this.key.does.not.exist]");
        }
    }

    @Test
    void missingKeyDoesNotThrow() {
        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            bukkitMock.when(org.bukkit.Bukkit::getLogger).thenReturn(java.util.logging.Logger.getLogger("test"));

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> Messages.get("totally.bogus.path"));
        }
    }

    // ---------- & -> § перевод цветовых кодов ----------

    @Test
    void colorCodesAreTranslated() {
        String text = Messages.get("wizard.cancel.title");
        assertThat(text).contains("§f").contains("§c").doesNotContain("&f").doesNotContain("&c");
    }

    @Test
    void placeholderSubstitutionHappensBeforeColorTranslation() {
        // {target} в значении с &-кодами - плейсхолдер должен подставиться и остаться
        // частью итоговой (уже раскрашенной) строки, а не сломать перевод цветов.
        String text = Messages.get("wizard.prompt.title-addon", "target", "MyPlugin");
        assertThat(text).contains("MyPlugin").contains("§d").doesNotContain("{target}");
    }

    // ---------- reload() подхватывает изменённый файл ----------

    @Test
    void reloadPicksUpChangedFileFromDisk() throws IOException {
        File messagesFile = new File(tempDir, "messages.yml");
        writeYaml(messagesFile, "command:\n  list:\n    header: \"Original Header\"\n");

        Messages.reload(plugin);
        assertThat(Messages.get("command.list.header")).isEqualTo("Original Header");

        writeYaml(messagesFile, "command:\n  list:\n    header: \"Changed Header\"\n");
        Messages.reload(plugin);

        assertThat(Messages.get("command.list.header")).isEqualTo("Changed Header");
    }

    @Test
    void reloadFallsBackToBundledDefaultWhenNoFileOnDisk() {
        // файла messages.yml ещё нет на диске (plugin.saveResource() на моке - no-op) -
        // reload() должен тихо подставить дефолт из classpath, а не упасть/оставить пусто.
        Messages.reload(plugin);
        assertThat(Messages.get("command.list.header")).isEqualTo("Аддоны NanoForge");
    }

    @Test
    void initSavesDefaultResourceThenLoadsFromDisk() throws IOException {
        // saveResource() на моке Plugin ничего реально не копирует на диск - имитируем
        // то, что НАСТОЯЩИЙ JavaPlugin.saveResource() сделал бы: кладём файл сами, ДО init(),
        // чтобы убедиться, что init() -> reload() действительно читает именно его с диска.
        File messagesFile = new File(tempDir, "messages.yml");
        writeYaml(messagesFile, "command:\n  list:\n    header: \"From Disk\"\n");

        Messages.init(plugin);

        verify(plugin).saveResource("messages.yml", false);
        assertThat(Messages.get("command.list.header")).isEqualTo("From Disk");
    }

    private static void writeYaml(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}

// by t.me/NanoDev_mc
