package ru.nanodev.nanoforge.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.model.Addon;

/**
 * В отличие от {@link PlaceholderAPIBridge} (который дёргает ЧУЖИЕ %плейсхолдеры%
 * рефлексией и работает вообще без PlaceholderAPI на classpath), эта экспансия -
 * обратное направление: NanoForge регистрируется В PlaceholderAPI и отдаёт СВОИ
 * переменные аддонов другим плагинам/меню/чату сервера.
 *
 * Формат: %nanoforge_<имяАддона>_<ключ>%
 *   - <имяАддона> - всё до ПЕРВОГО подчёркивания (поэтому имена аддонов с "_"
 *     внутри для этой фичи не подходят - используй CamelCase, как в примерах)
 *   - <ключ> = "enabled" -> "true"/"false", включён ли аддон прямо сейчас
 *   - любой другой <ключ> -> сначала переменная ЭТОГО игрока (setvar в actions
 *     без явного addon: - именно она), если у него нет такой - глобальная
 *     переменная аддона (addvar/setvar с save_as в глобальный неймспейс),
 *     если нет и её - пустая строка (не null - PlaceholderAPI сам пустые
 *     строки не подставляет "как есть", но так безопаснее для конкатенации)
 *
 * В отличие от остальных интеграций этого пакета, для КОМПИЛЯЦИИ этого одного
 * класса нужен настоящий jar PlaceholderAPI на classpath (scope: provided в
 * pom.xml) - расширяемый тут класс абстрактный, а не интерфейс, через чистую
 * рефлексию/dynamic proxy это не обойти. На сервере БЕЗ PlaceholderAPI сама
 * регистрация просто не происходит (см. {@link #tryRegister(NanoForgePlugin)}) -
 * plugin.yml объявляет PlaceholderAPI как softdepend, а не depend.
 */
public class NanoForgeExpansion extends PlaceholderExpansion {

    private final NanoForgePlugin plugin;

    public NanoForgeExpansion(NanoForgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Регистрирует экспансию, только если PlaceholderAPI реально установлен -
     * вызывать безопасно даже когда его нет (тогда просто ничего не произойдёт).
     */
    public static void tryRegister(NanoForgePlugin plugin) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new NanoForgeExpansion(plugin).register();
            plugin.getLogger().info("[NanoForge] Экспансия PlaceholderAPI зарегистрирована: "
                    + "%nanoforge_<аддон>_<ключ>%");
        } catch (Throwable t) {
            plugin.getLogger().warning("[NanoForge] Не удалось зарегистрировать экспансию "
                    + "PlaceholderAPI: " + t);
        }
    }

    @Override
    public String getIdentifier() {
        return "nanoforge";
    }

    @Override
    public String getAuthor() {
        return "t.me/NanoDev_mc";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // не отваливается при /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        int sep = params.indexOf('_');
        if (sep < 0 || sep == params.length() - 1) {
            return null; // не наш формат вообще, либо нет ключа после имени аддона
        }
        String addonName = params.substring(0, sep);
        String key = params.substring(sep + 1);

        Addon addon = plugin.getAddonManager().get(addonName);
        if (addon == null) {
            return null; // PlaceholderAPI на null просто ничего не подставит
        }

        if ("enabled".equals(key)) {
            return addon.isEnabled() ? "true" : "false";
        }

        if (offlinePlayer != null && offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
            Player online = offlinePlayer.getPlayer();
            String perPlayer = addon.getStorage().getVar(online, key, null);
            if (perPlayer != null) {
                return perPlayer;
            }
        }
        return addon.getStorage().getGlobalVar(key, "");
    }
}

// by t.me/NanoDev_mc
