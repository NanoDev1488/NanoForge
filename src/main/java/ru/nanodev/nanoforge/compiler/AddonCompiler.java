package ru.nanodev.nanoforge.compiler;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.nanodev.nanoforge.model.Addon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Compiles an {@link Addon} into a standalone, executable Bukkit/Spigot/Paper {@code .jar} plugin.
 * The resulting jar can run on servers without requiring NanoForge to be installed.
 */
public class AddonCompiler {

    public static File compileToJar(Addon addon, File destinationDirectory, String pluginVersion) throws IOException {
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs();
        }

        String safeName = addon.getName();
        File jarFile = new File(destinationDirectory, safeName + "-" + pluginVersion + ".jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Created-By"), "NanoForge Compiler");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            // 1. Pack plugin.yml
            String pluginYmlContent = generatePluginYml(addon, pluginVersion);
            writeEntry(jos, "plugin.yml", pluginYmlContent.getBytes(StandardCharsets.UTF_8));

            // 2. Pack addon.yml
            byte[] addonYmlBytes = Files.readAllBytes(addon.getFile().toPath());
            writeEntry(jos, "addon.yml", addonYmlBytes);

            // 3. Pack runtime bootstrap classes from current NanoForge jar
            embedRuntimeClasses(jos);
        }

        return jarFile;
    }

    public static String generatePluginYml(Addon addon, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(addon.getName()).append("\n");
        sb.append("version: ").append(version).append("\n");
        sb.append("main: ru.nanodev.nanoforge.compiler.runtime.StandaloneBootstrap\n");
        sb.append("api-version: 1.16\n");
        sb.append("author: NanoForge\n");

        if (addon.getType() == Addon.Type.ADDON && addon.getTargetPlugin() != null) {
            sb.append("depend: [").append(addon.getTargetPlugin()).append("]\n");
        }

        List<String> commands = addon.getCommandKeys();
        if (!commands.isEmpty()) {
            sb.append("commands:\n");
            for (String cmd : commands) {
                sb.append("  ").append(cmd).append(":\n");
                String desc = addon.getCommandDescription(cmd);
                if (desc != null && !desc.isEmpty()) {
                    sb.append("    description: \"").append(desc.replace("\"", "\\\"")).append("\"\n");
                }
                String perm = addon.getCommandPermission(cmd);
                if (perm != null && !perm.isEmpty()) {
                    sb.append("    permission: \"").append(perm).append("\"\n");
                }
                List<String> aliases = addon.getCommandAliases(cmd);
                if (!aliases.isEmpty()) {
                    sb.append("    aliases: [");
                    for (int i = 0; i < aliases.size(); i++) {
                        sb.append(aliases.get(i));
                        if (i < aliases.size() - 1) sb.append(", ");
                    }
                    sb.append("]\n");
                }
            }
        }

        List<String> libs = addon.getLibraries();
        if (!libs.isEmpty()) {
            sb.append("libraries:\n");
            for (String lib : libs) {
                sb.append("  - \"").append(lib).append("\"\n");
            }
        }

        return sb.toString();
    }

    private static void embedRuntimeClasses(JarOutputStream jos) {
        Set<String> added = new HashSet<>();
        added.add("META-INF/MANIFEST.MF");
        added.add("plugin.yml");
        added.add("addon.yml");

        // Пытаемся взять классы напрямую из текущего выполняющегося JAR плагина (NanoForge.jar)
        try {
            java.net.URL location = AddonCompiler.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && location.getPath().endsWith(".jar")) {
                File currentJar = new File(java.net.URLDecoder.decode(location.getPath(), "UTF-8"));
                if (currentJar.exists()) {
                    try (java.util.jar.JarFile jf = new java.util.jar.JarFile(currentJar)) {
                        Enumeration<JarEntry> entries = jf.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.endsWith(".class") && !added.contains(name)) {
                                added.add(name);
                                try (InputStream in = jf.getInputStream(entry)) {
                                    writeEntry(jos, name, toByteArray(in));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Если мы не в JAR (например, при тестах в IDE/Maven), берём критические классы через ClassLoader
        }

        // Гарантированный fallback для ключевых классов рантайма (StandaloneBootstrap, модели, утилиты)
        String[] fallbackClasses = {
                "ru/nanodev/nanoforge/compiler/runtime/StandaloneBootstrap.class",
                "ru/nanodev/nanoforge/model/Addon.class",
                "ru/nanodev/nanoforge/model/Addon$Type.class",
                "ru/nanodev/nanoforge/engine/ActionRunner.class",
                "ru/nanodev/nanoforge/engine/ConditionChecker.class",
                "ru/nanodev/nanoforge/engine/PlaceholderUtil.class",
                "ru/nanodev/nanoforge/engine/FancyFont.class",
                "ru/nanodev/nanoforge/database/DatabaseManager.class",
                "ru/nanodev/nanoforge/library/LibraryManager.class"
        };
        for (String className : fallbackClasses) {
            if (!added.contains(className)) {
                try (InputStream in = AddonCompiler.class.getClassLoader().getResourceAsStream(className)) {
                    if (in != null) {
                        added.add(className);
                        writeEntry(jos, className, toByteArray(in));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static void writeEntry(JarOutputStream jos, String name, byte[] data) throws IOException {
        JarEntry entry = new JarEntry(name);
        jos.putNextEntry(entry);
        jos.write(data);
        jos.closeEntry();
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
