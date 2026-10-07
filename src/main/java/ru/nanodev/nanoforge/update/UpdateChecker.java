package ru.nanodev.nanoforge.update;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.util.StartupChecks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверяет GitHub Releases репозитория NanoDev1488/NanoForge и, если там
 * версия новее текущей, скачивает jar в {@code plugins/update/NanoForge.jar} -
 * это ШТАТНЫЙ механизм Bukkit/Spigot/Paper (см. {@link Bukkit#getUpdateFolderFile()}):
 * при СЛЕДУЮЩЕМ старте сервера, ДО того как плагины вообще загружаются, файл
 * из update-папки сам заменяет старый jar в plugins/. Именно поэтому этот способ
 * одинаково безопасен и на Windows (где нельзя перезаписать/удалить jar, пока
 * его классы загружены JVM), и на Linux - замена происходит, когда старый jar
 * ещё НЕ открыт вообще, а не "на лету" во время работы сервера.
 *
 * Никакого JSON-парсера как зависимости не подключено - ответ GitHub API
 * достаточно стабилен по структуре, чтобы вытащить два нужных поля
 * (tag_name и browser_download_url первого .jar-ассета) простыми regex,
 * не подключая ради этого библиотеку.
 */
public class UpdateChecker {

    private static final String API_URL = "https://api.github.com/repos/NanoDev1488/NanoForge/releases/latest";
    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAR_ASSET_URL = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");

    private UpdateChecker() {
    }

    /** Асинхронная проверка - НИКОГДА не блокирует поток сервера сетевым запросом к GitHub. */
    public static void checkAsync(NanoForgePlugin plugin, Consumer<String> resultMessage) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = performCheck(plugin);
            if (resultMessage != null) {
                resultMessage.accept(result);
            }
        });
    }

    /** Выполняется УЖЕ в асинхронном потоке. Возвращает человекочитаемый результат для лога/команды. */
    static String performCheck(NanoForgePlugin plugin) {
        Logger log = plugin.getLogger();
        String json;
        try {
            json = httpGet(API_URL);
        } catch (IOException e) {
            log.warning("[NanoForge] Проверка обновлений не удалась (сеть/GitHub недоступны): " + e);
            return "Проверка обновлений не удалась: " + e;
        }

        String latestVersion = extractLatestVersion(json);
        if (latestVersion == null) {
            log.warning("[NanoForge] Не удалось разобрать ответ GitHub Releases API - возможно, релизов ещё нет.");
            return "Не удалось разобрать ответ GitHub (нет релизов?).";
        }

        String currentVersion = plugin.getDescription().getVersion();
        if (!StartupChecks.isVersionBelow(currentVersion, latestVersion)) {
            log.info("[NanoForge] Установлена актуальная версия (" + currentVersion + ").");
            return "Установлена актуальная версия (" + currentVersion + ").";
        }

        String jarUrl = extractJarAssetUrl(json);
        if (jarUrl == null) {
            log.warning("[NanoForge] Доступна версия " + latestVersion + ", но в релизе нет .jar-файла для скачивания. "
                    + "Скачай вручную: https://github.com/NanoDev1488/NanoForge/releases/latest");
            return "Доступна версия " + latestVersion + ", но jar в релизе не найден - скачай вручную с GitHub.";
        }

        File dest = updateFolderJarFile(plugin);
        try {
            downloadTo(jarUrl, dest);
        } catch (IOException e) {
            log.warning("[NanoForge] Нашёл версию " + latestVersion + ", но скачать jar не удалось: " + e);
            return "Нашёл версию " + latestVersion + ", но скачать jar не удалось: " + e;
        }

        log.info("[NanoForge] Скачана версия " + latestVersion + " в " + dest.getPath()
                + " - применится САМА при следующем перезапуске сервера (безопасно и на Windows, и на Linux).");
        return "Скачана версия " + latestVersion + " - обновится сама при следующем перезапуске сервера.";
    }

    static String extractLatestVersion(String json) {
        if (json == null) return null;
        Matcher m = TAG_NAME.matcher(json);
        if (!m.find()) return null;
        String tag = m.group(1);
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    static String extractJarAssetUrl(String json) {
        if (json == null) return null;
        Matcher m = JAR_ASSET_URL.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** plugins/update/NanoForge.jar - имя файла ДОЛЖНО совпадать с именем уже загруженного jar'а в plugins/. */
    static File updateFolderJarFile(Plugin plugin) {
        File updateFolder = Bukkit.getUpdateFolderFile();
        if (!updateFolder.exists()) updateFolder.mkdirs();
        return new File(updateFolder, plugin.getName() + ".jar");
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "NanoForge-UpdateChecker");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            return new String(readAllBytesJava8(in), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    /** InputStream.readAllBytes() появился только в Java 9 - нашему release=8 байткоду нужна своя копия. */
    private static byte[] readAllBytesJava8(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static void downloadTo(String url, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "NanoForge-UpdateChecker");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }
}

// by t.me/NanoDev_mc
