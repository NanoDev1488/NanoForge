package ru.nanodev.nanoforge.compiler;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AddonCompilerTest {

    @Test
    void generatesValidPluginYmlForAddon(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "addon.yml");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("name: EconomyAddon\n");
            fw.write("type: addon\n");
            fw.write("target: Vault\n");
            fw.write("libraries:\n");
            fw.write("  - \"org.xerial:sqlite-jdbc:3.45.1.0\"\n");
            fw.write("commands:\n");
            fw.write("  eco:\n");
            fw.write("    description: \"Economy command\"\n");
            fw.write("    permission: \"eco.use\"\n");
            fw.write("    aliases: [\"money\", \"bal\"]\n");
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Addon addon = new Addon(file, yaml);

        String pluginYml = AddonCompiler.generatePluginYml(addon, "1.2.3");
        assertThat(pluginYml).contains("name: EconomyAddon");
        assertThat(pluginYml).contains("version: 1.2.3");
        assertThat(pluginYml).contains("depend: [Vault]");
        assertThat(pluginYml).contains("eco:");
        assertThat(pluginYml).contains("description: \"Economy command\"");
        assertThat(pluginYml).contains("aliases: [money, bal]");
        assertThat(pluginYml).contains("org.xerial:sqlite-jdbc:3.45.1.0");
    }

    @Test
    void compilesToJarFile(@TempDir File tempDir) throws IOException {
        File addonFolder = new File(tempDir, "TestAddon");
        addonFolder.mkdirs();
        File file = new File(addonFolder, "addon.yml");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("name: StandaloneTest\n");
            fw.write("type: new\n");
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Addon addon = new Addon(file, yaml);

        File outDir = new File(tempDir, "dist");
        File jar = AddonCompiler.compileToJar(addon, outDir, "1.0.0");

        assertThat(jar).exists();
        assertThat(jar.length()).isGreaterThan(0);
        assertThat(jar.getName()).isEqualTo("StandaloneTest-1.0.0.jar");
    }
}
