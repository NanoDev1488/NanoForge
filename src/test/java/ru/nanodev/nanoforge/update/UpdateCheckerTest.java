package ru.nanodev.nanoforge.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты на чистую regex-логику разбора ответа GitHub Releases API
 * (сетевые вызовы здесь не тестируются - только extractLatestVersion/extractJarAssetUrl,
 * которые package-visible static именно для этой тестируемости).
 */
class UpdateCheckerTest {

    private static final String REAL_LIKE_RESPONSE = "{\n"
            + "  \"tag_name\": \"v1.6.1\",\n"
            + "  \"name\": \"NanoForge 1.6.1\",\n"
            + "  \"assets\": [\n"
            + "    {\n"
            + "      \"name\": \"NanoForge-1.6.1.jar\",\n"
            + "      \"browser_download_url\": \"https://github.com/NanoDev1488/NanoForge/releases/download/v1.6.1/NanoForge-1.6.1.jar\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    @Test
    void extractsVersionWithoutLeadingV() {
        assertThat(UpdateChecker.extractLatestVersion(REAL_LIKE_RESPONSE)).isEqualTo("1.6.1");
    }

    @Test
    void extractsVersionWithoutVPrefixUnchanged() {
        String json = "{\"tag_name\": \"1.9.9\"}";
        assertThat(UpdateChecker.extractLatestVersion(json)).isEqualTo("1.9.9");
    }

    @Test
    void extractsJarAssetUrl() {
        assertThat(UpdateChecker.extractJarAssetUrl(REAL_LIKE_RESPONSE))
                .isEqualTo("https://github.com/NanoDev1488/NanoForge/releases/download/v1.6.1/NanoForge-1.6.1.jar");
    }

    @Test
    void returnsNullWhenNoTagNamePresent() {
        assertThat(UpdateChecker.extractLatestVersion("{}")).isNull();
    }

    @Test
    void returnsNullWhenNoJarAssetPresent() {
        String json = "{\"tag_name\": \"v1.6.1\", \"assets\": [{\"browser_download_url\": \"https://x/y.zip\"}]}";
        assertThat(UpdateChecker.extractJarAssetUrl(json)).isNull();
    }

    @Test
    void handlesNullJsonGracefully() {
        assertThat(UpdateChecker.extractLatestVersion(null)).isNull();
        assertThat(UpdateChecker.extractJarAssetUrl(null)).isNull();
    }

    @Test
    void updateFolderJarFileUsesPluginNameWithJarExtension() {
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        org.mockito.Mockito.when(plugin.getName()).thenReturn("NanoForge");

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            java.io.File updateFolder = new java.io.File(System.getProperty("java.io.tmpdir"),
                    "nanoforge-update-test-" + System.nanoTime());
            bukkitMock.when(org.bukkit.Bukkit::getUpdateFolderFile).thenReturn(updateFolder);

            java.io.File dest = UpdateChecker.updateFolderJarFile(plugin);

            assertThat(dest.getName()).isEqualTo("NanoForge.jar");
            assertThat(dest.getParentFile()).isEqualTo(updateFolder);
            assertThat(updateFolder).exists();

            updateFolder.delete();
        }
    }
}

// by t.me/NanoDev_mc
