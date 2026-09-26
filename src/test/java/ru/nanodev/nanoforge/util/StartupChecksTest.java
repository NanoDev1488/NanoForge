package ru.nanodev.nanoforge.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.nanodev.nanoforge.util.StartupChecks.VersionStatus.*;

class StartupChecksTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // граничные значения диапазона
            "1.16-R0.1-SNAPSHOT,          SUPPORTED",
            "1.16.0-R0.1-SNAPSHOT,        SUPPORTED",
            "1.21.11-R0.1-SNAPSHOT,       SUPPORTED",
            "1.20.4-R0.1-SNAPSHOT,        SUPPORTED",

            // ниже минимума
            "1.15.2-R0.1-SNAPSHOT,        BELOW_MIN",
            "1.12.2-R0.1-SNAPSHOT,        BELOW_MIN",
            "1.8.8-R0.1-SNAPSHOT,         BELOW_MIN",

            // выше максимума
            "1.21.12-R0.1-SNAPSHOT,       ABOVE_MAX",
            "1.22.0-R0.1-SNAPSHOT,        ABOVE_MAX",
            "1.30.0-R0.1-SNAPSHOT,        ABOVE_MAX",
            "2.0.0-R0.1-SNAPSHOT,         ABOVE_MAX",

            // нераспознаваемое
            "garbage,                     UNKNOWN",
            "'',                          UNKNOWN",
    })
    void classifiesVersionsCorrectly(String rawVersion, String expected) {
        StartupChecks.VersionStatus expectedStatus = StartupChecks.VersionStatus.valueOf(expected.trim());
        assertThat(StartupChecks.classify(rawVersion)).isEqualTo(expectedStatus);
    }

    @org.junit.jupiter.api.Test
    void nullVersionIsUnknown() {
        assertThat(StartupChecks.classify(null)).isEqualTo(UNKNOWN);
    }

    @org.junit.jupiter.api.Test
    void versionWithoutDashSuffixStillWorks() {
        // не все сборки следуют формату "X.Y.Z-R...SNAPSHOT" - голая версия тоже должна парситься
        assertThat(StartupChecks.classify("1.20.4")).isEqualTo(SUPPORTED);
    }

    // ---------- isVersionBelow ----------

    @org.junit.jupiter.api.Test
    void isVersionBelowTrueWhenActualIsOlder() {
        assertThat(StartupChecks.isVersionBelow("1.6", "1.7")).isTrue();
    }

    @org.junit.jupiter.api.Test
    void isVersionBelowFalseWhenActualIsNewer() {
        assertThat(StartupChecks.isVersionBelow("1.8", "1.7")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void isVersionBelowFalseWhenEqual() {
        assertThat(StartupChecks.isVersionBelow("1.7.0", "1.7")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void isVersionBelowHandlesSnapshotSuffixes() {
        assertThat(StartupChecks.isVersionBelow("6.9.0-SNAPSHOT", "7.0.0")).isTrue();
    }

    @org.junit.jupiter.api.Test
    void isVersionBelowFalseWhenUnparseable() {
        // не пугаем ложным warning'ом, если версию стороннего плагина вообще не разобрать
        assertThat(StartupChecks.isVersionBelow("не-версия", "1.7")).isFalse();
    }

    // ---------- checkOptionalDependencies ----------

    @org.junit.jupiter.api.Test
    void checkOptionalDependenciesDoesNotThrowWithNothingInstalled() {
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));

        org.bukkit.plugin.PluginManager pm = org.mockito.Mockito.mock(org.bukkit.plugin.PluginManager.class);
        org.bukkit.Server server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(server.getPluginManager()).thenReturn(pm);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkitMock.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            StartupChecks.checkOptionalDependencies(plugin); // не должно бросить исключение
        }
    }

    @org.junit.jupiter.api.Test
    void checkOptionalDependenciesWarnsAboutOutdatedVersion() {
        org.bukkit.plugin.Plugin nanoForge = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        java.util.logging.Logger logger = org.mockito.Mockito.mock(java.util.logging.Logger.class);
        org.mockito.Mockito.when(nanoForge.getLogger()).thenReturn(logger);

        org.bukkit.plugin.Plugin oldVault = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        org.bukkit.plugin.PluginDescriptionFile desc = org.mockito.Mockito.mock(org.bukkit.plugin.PluginDescriptionFile.class);
        org.mockito.Mockito.when(desc.getVersion()).thenReturn("1.0");
        org.mockito.Mockito.when(oldVault.getDescription()).thenReturn(desc);

        org.bukkit.plugin.PluginManager pm = org.mockito.Mockito.mock(org.bukkit.plugin.PluginManager.class);
        org.mockito.Mockito.when(pm.getPlugin("Vault")).thenReturn(oldVault);
        org.bukkit.Server server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(server.getPluginManager()).thenReturn(pm);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkitMock.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pm);

            StartupChecks.checkOptionalDependencies(nanoForge);

            org.mockito.Mockito.verify(logger).warning(org.mockito.ArgumentMatchers.contains("Vault"));
        }
    }
}

// by t.me/NanoDev_mc
