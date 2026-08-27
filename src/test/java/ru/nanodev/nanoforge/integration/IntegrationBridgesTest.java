package ru.nanodev.nanoforge.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что мосты к Vault/WorldGuard/PlugMan/произвольному плагину
 * ведут себя БЕЗОПАСНО, когда соответствующего плагина на сервере нет:
 * возвращают false/null и не бросают исключений - именно это и требовалось
 * ("если плагина нет - пишет и отключается, а не падает с ошибкой").
 *
 * MockBukkit поднимает in-memory реализацию Bukkit API (ServerMock), поэтому
 * Bukkit.getPluginManager() и т.д. внутри этих классов реально на что-то
 * отвечают, а не падают с NullPointerException из-за отсутствия сервера.
 *
 * ВНИМАНИЕ: пакет/версия MockBukkit (be.seeseemelk.mockbukkit, координаты
 * com.github.seeseemelk:MockBukkit-v1.20 в pom.xml) стоит свериться с
 * актуальной на https://github.com/MockBukkit/MockBukkit перед первым
 * запуском - тестовая инфраструктура собиралась без доступа в сеть.
 */
class IntegrationBridgesTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reflectionBridgeReturnsNullWhenPluginNotInstalled() {
        Object result = ReflectionBridge.call("SomePluginThatDoesNotExist", "someMethod", new String[0]);
        assertThat(result).isNull();
    }

    @Test
    void plugManBridgeIsUnavailableWhenNotInstalled() {
        assertThat(PlugManBridge.isAvailable()).isFalse();
    }

    @Test
    void plugManEnableCommandFormatIsCorrect() {
        // это чистая строковая логика - не зависит от того, установлен ли PlugMan
        assertThat(PlugManBridge.enableCommand("EssentialsX")).isEqualTo("plugman enable EssentialsX");
    }

    @Test
    void vaultBridgeHasReturnsFalseWhenVaultNotInstalled() {
        // VaultBridge не трогает player до проверки наличия Vault - null безопасен
        assertThat(VaultBridge.has(null, 10)).isFalse();
    }

    @Test
    void vaultBridgeGetBalanceReturnsMinusOneWhenUnavailable() {
        assertThat(VaultBridge.getBalance(null)).isEqualTo(-1);
    }

    @Test
    void worldGuardBridgeReturnsFalseWhenNotInstalled() {
        assertThat(WorldGuardBridge.isInRegion(null, "spawn")).isFalse();
    }
}

// by t.me/NanoDev_mc
