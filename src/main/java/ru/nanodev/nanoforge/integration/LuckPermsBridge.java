package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Мост к LuckPerms для проверки "состоит ли основная (primary) группа игрока
 * в LuckPerms равна <X>" - condition {@code luckperms_group}. Как и
 * VaultBridge/WorldGuardBridge - чистая рефлексия, БЕЗ compile-time
 * зависимости от LuckPerms-jar'а, поэтому плагин собирается и работает без
 * LuckPerms тоже (условие просто всегда false).
 */
public class LuckPermsBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static Boolean available = null;
    private static boolean warnedMissing = false;

    private static boolean pluginPresent() {
        if (available == null) {
            try {
                available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
            } catch (Throwable t) {
                available = false;
            }
            if (!available && !warnedMissing) {
                warnedMissing = true;
                LOG.warning("[NanoForge] LuckPerms не найден - условие if: luckperms_group работать не будет "
                        + "(проверка группы всегда будет считаться непройденной).");
            }
        }
        return available;
    }

    /** Только для тестов - сбрасывает закешированное состояние проверки LuckPerms. */
    static void resetForTests() {
        available = null;
        warnedMissing = false;
    }

    /** @return true если у игрока прямо сейчас есть указанная группа LuckPerms (регистронезависимо). */
    public static boolean isInGroup(Player player, String groupName) {
        if (!pluginPresent()) return false;
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);

            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                    .invoke(userManager, player.getUniqueId());
            if (user == null) {
                // игрок online, но LuckPerms почему-то ещё не закешировал его User - крайне
                // маловероятно на практике (LuckPerms кеширует при джойне), но на всякий случай
                return false;
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            String primaryGroup = (String) metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            // Проверяем primary-группу через MetaData (всегда доступна без дополнительных
            // QueryOptions) - этого достаточно для типового сценария "у VIP свой primary group".
            // Полный список унаследованных групп (не только primary) API отдаёт только через
            // Node/InheritanceNode с QueryOptions - реализовывать это чистой рефлексией уже
            // избыточно хрупко, поэтому сознательно ограничиваемся primary group.
            return primaryGroup != null && primaryGroup.equalsIgnoreCase(groupName);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Проверка группы LuckPerms не удалась (возможно другая версия API): " + t);
            return false;
        }
    }
}

// by t.me/NanoDev_mc
