package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Мост к WorldGuard 7.x для проверки "стоит ли игрок в указанном регионе".
 * Как и VaultBridge - работает через рефлексию, БЕЗ compile-time зависимости
 * от WorldGuard-jar'а, чтобы плагин собирался и работал без WorldGuard тоже.
 *
 * ВАЖНО: API WorldGuard между версиями (6.x/7.x, разные билды Sponge/Bukkit)
 * отличается сильнее, чем у Vault. Реализация ниже нацелена на WorldGuard 7.x
 * (com.sk89q.worldguard.WorldGuard / RegionContainer). Если на сервере другая
 * версия и рефлексия не срабатывает - метод просто вернёт false и в консоль
 * уйдёт предупреждение с точной причиной, ничего не сломается.
 */
public class WorldGuardBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static Boolean available = null;
    private static boolean warnedMissing = false;

    private static boolean pluginPresent() {
        if (available == null) {
            available = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
            if (!available && !warnedMissing) {
                warnedMissing = true;
                LOG.warning("[NanoForge] WorldGuard не найден - условие if: region работать не будет "
                        + "(проверка региона всегда будет считаться непройденной).");
            }
        }
        return available;
    }

    /** @return true если игрок сейчас физически находится в регионе с указанным именем (регистронезависимо). */
    public static boolean isInRegion(Player player, String regionName) {
        if (!pluginPresent()) return false;
        try {
            // WorldGuard.getInstance()
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = wgClass.getMethod("getInstance");
            Object wg = getInstance.invoke(null);

            // wg.getPlatform().getRegionContainer()
            Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            // адаптер мира: com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getWorld())
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = adapterClass.getMethod("adapt", org.bukkit.World.class).invoke(null, player.getWorld());

            // regionContainer.get(weWorld) -> RegionManager
            Object regionManager = regionContainer.getClass()
                    .getMethod("get", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(regionContainer, weWorld);
            if (regionManager == null) return false;

            // BukkitAdapter.adapt(player.getLocation()) -> com.sk89q.worldedit.util.Location
            Object weLocation = adapterClass.getMethod("adapt", org.bukkit.Location.class)
                    .invoke(null, player.getLocation());
            // .toVector().toBlockPoint() -> BlockVector3
            Object vector = weLocation.getClass().getMethod("toVector").invoke(weLocation);
            Object blockVector = vector.getClass().getMethod("toBlockPoint").invoke(vector);

            // regionManager.getApplicableRegions(blockVector) -> ApplicableRegionSet
            Object applicable = regionManager.getClass()
                    .getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.math.BlockVector3"))
                    .invoke(regionManager, blockVector);

            // проверяем через getRegions() -> Set<ProtectedRegion>, у каждого getId()
            Object regionsSet = applicable.getClass().getMethod("getRegions").invoke(applicable);
            for (Object region : (Iterable<?>) regionsSet) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (id.equalsIgnoreCase(regionName)) return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Проверка региона WorldGuard не удалась (возможно другая версия API): " + t);
            return false;
        }
    }
}

// by t.me/NanoDev_mc
