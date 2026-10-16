package ru.nanodev.nanoforge.script;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.nanodev.nanoforge.integration.LuckPermsBridge;
import ru.nanodev.nanoforge.integration.VaultBridge;
import ru.nanodev.nanoforge.integration.WorldGuardBridge;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast-path binding к Bukkit/Spigot объектам и математическим функциям.
 * Прямой доступ без медленной обобщённой рефлексии.
 */
public final class BukkitBinding {

    private BukkitBinding() {}

    public static Object getProperty(Object target, String prop) {
        if (target == null || prop == null) return null;
        String p = prop.toLowerCase();

        // Свойства Entity / Damageable
        if (target instanceof Entity) {
            Entity entity = (Entity) target;
            switch (p) {
                case "name": return entity.getName();
                case "uuid": return entity.getUniqueId().toString();
                case "type": return entity.getType().name();
                case "world": return entity.getWorld().getName();
                case "x": return entity.getLocation().getX();
                case "y": return entity.getLocation().getY();
                case "z": return entity.getLocation().getZ();
                case "yaw": return (double) entity.getLocation().getYaw();
                case "pitch": return (double) entity.getLocation().getPitch();
                case "is_dead": return entity.isDead();
                case "is_valid": return entity.isValid();
                default: break;
            }

            if (target instanceof Damageable) {
                Damageable d = (Damageable) target;
                if ("health".equals(p)) {
                    return d.getHealth();
                } else if ("max_health".equals(p)) {
                    try {
                        org.bukkit.attribute.AttributeInstance attr = d.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (attr != null) return attr.getValue();
                    } catch (Throwable ignored) {}
                    return d.getMaxHealth();
                }
            }

            if (target instanceof Player) {
                Player player = (Player) target;
                switch (p) {
                    case "food": return (double) player.getFoodLevel();
                    case "level": return (double) player.getLevel();
                    case "exp": return (double) player.getExp();
                    case "balance": return VaultBridge.getBalance(player);
                    case "is_sneaking": return player.isSneaking();
                    case "is_flying": return player.isFlying();
                    case "is_sprinting": return player.isSprinting();
                    case "is_gliding": return player.isGliding();
                    case "is_swimming": return player.isSwimming();
                    case "is_op": return player.isOp();
                    case "gamemode": return player.getGameMode().name();
                    default: break;
                }
            }
        }

        // Свойства Event
        if (target instanceof Event) {
            Event ev = (Event) target;
            if ("name".equals(p)) return ev.getEventName();
            if ("cancelled".equals(p) || "is_cancelled".equals(p)) {
                return ev instanceof Cancellable && ((Cancellable) ev).isCancelled();
            }
            if (target instanceof EntityDamageEvent) {
                EntityDamageEvent ede = (EntityDamageEvent) target;
                if ("damage".equals(p)) return ede.getDamage();
                if ("final_damage".equals(p)) return ede.getFinalDamage();
                if ("cause".equals(p)) return ede.getCause().name();
            }
        }

        return null;
    }

    public static Object invokeMethod(Object target, String method, Object[] args, NanoContext ctx) {
        if (target == null || method == null) return null;
        String m = method.toLowerCase();

        if (target instanceof Player) {
            Player p = (Player) target;
            if (("has_perm".equals(m) || "has_permission".equals(m)) && args.length >= 1) {
                return p.hasPermission(String.valueOf(args[0]));
            }
            if ("has_tag".equals(m) && args.length >= 1) {
                return p.getScoreboardTags().contains(String.valueOf(args[0]));
            }
            if ("in_region".equals(m) && args.length >= 1) {
                return WorldGuardBridge.isInRegion(p, String.valueOf(args[0]));
            }
            if ("in_group".equals(m) && args.length >= 1) {
                return LuckPermsBridge.inGroup(p, String.valueOf(args[0]));
            }
        }

        if (target instanceof Entity) {
            Entity e = (Entity) target;
            if ("distance_to".equals(m) && args.length >= 1 && args[0] instanceof Entity) {
                return e.getLocation().distance(((Entity) args[0]).getLocation());
            }
        }

        return null;
    }

    public static Object invokeGlobal(String funcName, Object[] args, NanoContext ctx) {
        if (funcName == null) return null;
        String fn = funcName.toLowerCase();

        switch (fn) {
            case "chance": {
                if (args.length == 0) return false;
                double prob = toDouble(args[0]);
                if (prob > 1.0) prob = prob / 100.0;
                return ThreadLocalRandom.current().nextDouble() < prob;
            }
            case "has_perm":
            case "has_permission": {
                if (args.length >= 1 && ctx != null && ctx.getPlayer() != null) {
                    return ctx.getPlayer().hasPermission(String.valueOf(args[0]));
                }
                return false;
            }
            case "balance": {
                if (ctx != null && ctx.getPlayer() != null) {
                    return VaultBridge.getBalance(ctx.getPlayer());
                }
                return 0.0;
            }
            case "min": {
                if (args.length >= 2) return Math.min(toDouble(args[0]), toDouble(args[1]));
                return args.length == 1 ? toDouble(args[0]) : 0.0;
            }
            case "max": {
                if (args.length >= 2) return Math.max(toDouble(args[0]), toDouble(args[1]));
                return args.length == 1 ? toDouble(args[0]) : 0.0;
            }
            case "abs": {
                if (args.length >= 1) return Math.abs(toDouble(args[0]));
                return 0.0;
            }
            case "round": {
                if (args.length >= 1) return (double) Math.round(toDouble(args[0]));
                return 0.0;
            }
            case "floor": {
                if (args.length >= 1) return Math.floor(toDouble(args[0]));
                return 0.0;
            }
            case "ceil": {
                if (args.length >= 1) return Math.ceil(toDouble(args[0]));
                return 0.0;
            }
            case "sqrt": {
                if (args.length >= 1) return Math.sqrt(toDouble(args[0]));
                return 0.0;
            }
            case "clamp": {
                if (args.length >= 3) {
                    double v = toDouble(args[0]);
                    double min = toDouble(args[1]);
                    double max = toDouble(args[2]);
                    return Math.max(min, Math.min(max, v));
                }
                return 0.0;
            }
            case "random": {
                double min = args.length >= 1 ? toDouble(args[0]) : 0.0;
                double max = args.length >= 2 ? toDouble(args[1]) : 1.0;
                if (min >= max) return min;
                return ThreadLocalRandom.current().nextDouble(min, max);
            }
            case "var": {
                if (args.length >= 1 && ctx != null && ctx.getAddon() != null && ctx.getAddon().getStorage() != null) {
                    String def = args.length >= 2 ? String.valueOf(args[1]) : "0";
                    return ctx.getAddon().getStorage().getVar(ctx.getPlayer(), String.valueOf(args[0]), def);
                }
                return "0";
            }
            case "global_var": {
                if (args.length >= 1 && ctx != null && ctx.getAddon() != null && ctx.getAddon().getStorage() != null) {
                    String def = args.length >= 2 ? String.valueOf(args[1]) : "0";
                    return ctx.getAddon().getStorage().getGlobalVar(String.valueOf(args[0]), def);
                }
                return "0";
            }
            default:
                return null;
        }
    }

    public static double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof Boolean) return ((Boolean) obj) ? 1.0 : 0.0;
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return 0.0;
        }
    }
}
