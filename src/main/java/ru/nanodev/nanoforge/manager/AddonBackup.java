package ru.nanodev.nanoforge.manager;

import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Перед КАЖДОЙ правкой addon.yml из игры/консоли (/nano set, чат-DSL редактор
 * меню) сохраняет копию файла КАК ОН БЫЛ до правки - в подпапку "backups/"
 * рядом с самим addon.yml. Простой rollback без git: если правка оказалась
 * неудачной, копию можно вручную вернуть на место. Хранится последние
 * {@link #MAX_BACKUPS_PER_ADDON} версий на аддон, более старые удаляются
 * автоматически - иначе за месяцы активного редактирования папка распухнет.
 */
public final class AddonBackup {

    public static final int MAX_BACKUPS_PER_ADDON = 20;
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");

    private AddonBackup() {
    }

    /** Копирует ТЕКУЩЕЕ содержимое файла на диске в backups/ перед тем, как его перезапишут. */
    public static void backup(Addon addon, Logger logger) {
        File file = addon.getFile();
        if (file == null || !file.exists()) return; // нечего бэкапить - файл ещё не создан

        try {
            File backupsDir = new File(file.getParentFile(), "backups");
            if (!backupsDir.exists()) backupsDir.mkdirs();

            String timestamp;
            synchronized (TIMESTAMP_FORMAT) {
                timestamp = TIMESTAMP_FORMAT.format(new Date());
            }
            File backupFile = new File(backupsDir, "addon-" + timestamp + ".yml");
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            pruneOldBackups(backupsDir, logger);
        } catch (IOException e) {
            // бэкап - это подстраховка, а не критичная часть операции - не блокируем саму правку,
            // просто предупреждаем в консоль, что подстраховки в этот раз не будет
            if (logger != null) {
                logger.warning("[NanoForge] Не удалось создать бэкап addon.yml для '" + addon.getName() + "': " + e);
            }
        }
    }

    private static void pruneOldBackups(File backupsDir, Logger logger) {
        File[] files = backupsDir.listFiles((dir, name) -> name.startsWith("addon-") && name.endsWith(".yml"));
        if (files == null || files.length <= MAX_BACKUPS_PER_ADDON) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - MAX_BACKUPS_PER_ADDON;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }
}

// by t.me/NanoDev_mc
