package ru.nanodev.nanoforge.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AddonBackupTest {

    @TempDir
    File tempDir;

    private Addon buildAddon() throws IOException {
        File file = new File(tempDir, "addon.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "BackupTest");
        yaml.set("type", "new");
        Addon addon = new Addon(file, yaml);
        yaml.save(file); // на диске должно реально что-то лежать, иначе backup() пропустит
        return addon;
    }

    @Test
    void backupCopiesCurrentFileContentIntoBackupsFolder() throws IOException {
        Addon addon = buildAddon();

        AddonBackup.backup(addon, Logger.getLogger("test"));

        File backupsDir = new File(tempDir, "backups");
        assertThat(backupsDir).exists().isDirectory();
        File[] backups = backupsDir.listFiles();
        assertThat(backups).hasSize(1);
        assertThat(Files.readString(backups[0].toPath())).contains("BackupTest");
    }

    @Test
    void backupDoesNothingSilentlyWhenFileDoesNotExistYet() {
        File file = new File(tempDir, "does-not-exist.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "Ghost");
        Addon addon = new Addon(file, yaml);

        AddonBackup.backup(addon, Logger.getLogger("test")); // не должно бросить исключение

        assertThat(new File(tempDir, "backups")).doesNotExist();
    }

    @Test
    void multipleBackupsAccumulateAsSeparateFiles() throws IOException, InterruptedException {
        Addon addon = buildAddon();

        AddonBackup.backup(addon, Logger.getLogger("test"));
        Thread.sleep(5); // гарантируем разные таймстемпы в имени файла
        AddonBackup.backup(addon, Logger.getLogger("test"));

        File backupsDir = new File(tempDir, "backups");
        assertThat(backupsDir.listFiles()).hasSize(2);
    }

    @Test
    void oldBackupsArePrunedBeyondTheLimit() throws IOException {
        Addon addon = buildAddon();
        File backupsDir = new File(tempDir, "backups");
        backupsDir.mkdirs();

        // создаём заведомо больше файлов, чем лимит, с разным временем модификации
        for (int i = 0; i < AddonBackup.MAX_BACKUPS_PER_ADDON + 5; i++) {
            File f = new File(backupsDir, "addon-fake-" + i + ".yml");
            Files.writeString(f.toPath(), "fake");
            f.setLastModified(System.currentTimeMillis() + i * 1000L);
        }

        AddonBackup.backup(addon, Logger.getLogger("test")); // триггерит pruneOldBackups

        assertThat(backupsDir.listFiles()).hasSizeLessThanOrEqualTo(AddonBackup.MAX_BACKUPS_PER_ADDON);
    }
}

// by t.me/NanoDev_mc
