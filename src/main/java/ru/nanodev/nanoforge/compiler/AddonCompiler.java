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
        // Embed compiled runtime classes for standalone execution
        String[] runtimeClassNames = {
                "ru/nanodev/nanoforge/compiler/runtime/StandaloneBootstrap.class"
        };
        for (String className : runtimeClassNames) {
            try (InputStream in = AddonCompiler.class.getClassLoader().getResourceAsStream(className)) {
                if (in != null) {
                    writeEntry(jos, className, toByteArray(in));
                }
            } catch (Exception ignored) {}
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
