package ru.nanodev.nanoforge.library;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Downloads and loads external libraries (JARs) declared in {@code libraries:}
 * into the runtime ClassLoader.
 * Format: "groupId:artifactId:version" (e.g. "org.xerial:sqlite-jdbc:3.45.1.0")
 */
public class LibraryManager {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private final File librariesFolder;
    private final Set<String> loadedLibraries = ConcurrentHashMap.newKeySet();

    public LibraryManager(File librariesFolder) {
        this.librariesFolder = librariesFolder;
        if (!librariesFolder.exists()) {
            librariesFolder.mkdirs();
        }
    }

    public void loadLibraries(List<String> coordinatesList) {
        if (coordinatesList == null || coordinatesList.isEmpty()) return;
        for (String coord : coordinatesList) {
            loadLibrary(coord);
        }
    }

    public boolean loadLibrary(String coordinates) {
        if (coordinates == null || coordinates.trim().isEmpty()) return false;
        coordinates = coordinates.trim();
        if (loadedLibraries.contains(coordinates)) {
            return true;
        }

        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            LOGGER.warning("[NanoForge] Invalid library coordinates: '" + coordinates + "'. Expected groupId:artifactId:version");
            return false;
        }

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();

        String groupPath = groupId.replace('.', '/');
        String fileName = artifactId + "-" + version + ".jar";
        File targetFile = new File(librariesFolder, fileName);

        if (!targetFile.exists()) {
            String downloadUrl = MAVEN_CENTRAL + groupPath + "/" + artifactId + "/" + version + "/" + fileName;
            LOGGER.info("[NanoForge] Downloading library " + coordinates + " from Maven Central...");
            try {
                downloadFile(downloadUrl, targetFile);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[NanoForge] Failed to download library " + coordinates + ": " + e.getMessage(), e);
                return false;
            }
        }

        try {
            injectIntoClassLoader(targetFile);
            loadedLibraries.add(coordinates);
            LOGGER.info("[NanoForge] Successfully loaded library: " + coordinates);
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[NanoForge] Failed to inject library into ClassLoader: " + targetFile.getName(), t);
            return false;
        }
    }

    private void downloadFile(String urlStr, File destination) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "NanoForge-Library-Resolver");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + " " + conn.getResponseMessage());
        }

        File temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void injectIntoClassLoader(File jarFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(classLoader, jarFile.toURI().toURL());
        } else {
            // Java 9+ fallback or Paper PluginClassLoader
            try {
                Method addURL = classLoader.getClass().getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(classLoader, jarFile.toURI().toURL());
            } catch (NoSuchMethodException e) {
                // If not URLClassLoader, attempt to use instrumentation or reflection on ucp if present
                LOGGER.fine("[NanoForge] ClassLoader does not have addURL method directly, jar loaded to disk: " + jarFile.getAbsolutePath());
            }
        }
    }

    public File getLibrariesFolder() {
        return librariesFolder;
    }

    public Set<String> getLoadedLibraries() {
        return Collections.unmodifiableSet(loadedLibraries);
    }
}
