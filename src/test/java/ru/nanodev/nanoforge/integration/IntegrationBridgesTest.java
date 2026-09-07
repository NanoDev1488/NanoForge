package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Проверяет, что мосты к Vault/WorldGuard/PlugMan/произвольному плагину
 * ведут себя БЕЗОПАСНО, когда соответствующего плагина на сервере нет:
 * возвращают false/null и не бросают исключений - именно это и требовалось
 * ("если плагина нет - пишет и отключается, а не падает с ошибкой").
 *
 * Обычный Bukkit API мокается статически через Mockito (mockStatic,
 * встроено в mockito-core с версии 5.x, отдельного mockito-inline не нужно) -
 * никакого MockBukkit/PaperMC тут нет и не требуется.
 */
class IntegrationBridgesTest {

    private MockedStatic<Bukkit> bukkitMock;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        // сбрасываем кеш "плагин найден/не найден" внутри мостов - иначе результат
        // первого же вызова в рамках JVM "залипнет" на все остальные тесты подряд
        VaultBridge.resetForTests();
        WorldGuardBridge.resetForTests();
        PlaceholderAPIBridge.resetForTests();

        pluginManager = mock(PluginManager.class);
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void reflectionBridgeReturnsNullWhenPluginNotInstalled() {
        when(pluginManager.getPlugin("SomePluginThatDoesNotExist")).thenReturn(null);

        Object result = ReflectionBridge.call("SomePluginThatDoesNotExist", "someMethod", new String[0]);
        assertThat(result).isNull();
    }

    @Test
    void plugManBridgeIsUnavailableWhenNotInstalled() {
        when(pluginManager.getPlugin("PlugMan")).thenReturn(null);
        when(pluginManager.getPlugin("PlugManX")).thenReturn(null);

        assertThat(PlugManBridge.isAvailable()).isFalse();
    }

    @Test
    void plugManEnableCommandFormatIsCorrect() {
        // чистая строковая логика - не зависит от того, установлен ли PlugMan
        assertThat(PlugManBridge.enableCommand("EssentialsX")).isEqualTo("plugman enable EssentialsX");
    }

    @Test
    void vaultBridgeHasReturnsFalseWhenVaultNotInstalled() {
        // Vault не подключен как зависимость вообще, поэтому Class.forName внутри
        // VaultBridge падает ещё до обращения к Bukkit - и это тоже безопасный путь.
        assertThat(VaultBridge.has(null, 10)).isFalse();
    }

    @Test
    void vaultBridgeGetBalanceReturnsMinusOneWhenUnavailable() {
        assertThat(VaultBridge.getBalance(null)).isEqualTo(-1);
    }

    @Test
    void worldGuardBridgeReturnsFalseWhenNotInstalled() {
        when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
        assertThat(WorldGuardBridge.isInRegion(null, "spawn")).isFalse();
    }

    @Test
    void placeholderApiBridgeReturnsTextUnchangedWhenNotInstalled() {
        when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(null);
        String text = "Привет, {player}!";
        assertThat(PlaceholderAPIBridge.apply(text, null)).isEqualTo(text);
    }

    @Test
    void placeholderApiBridgeReturnsNullTextAsNull() {
        assertThat(PlaceholderAPIBridge.apply(null, null)).isNull();
    }
}

// by t.me/NanoDev_mc
