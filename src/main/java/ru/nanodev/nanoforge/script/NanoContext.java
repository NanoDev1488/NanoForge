package ru.nanodev.nanoforge.script;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.nanodev.nanoforge.model.Addon;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Контекст выполнения для NanoScript.
 * Предоставляет быстрый доступ к объектам игрока, атакующего, жертвы, события и переменных.
 */
public final class NanoContext {

    private final Player player;
    private final Entity attacker;
    private final Entity victim;
    private final Entity target;
    private final Event event;
    private final Addon addon;
    private final Map<String, Object> variables;

    public NanoContext(Player player, Entity attacker, Entity victim, Entity target,
                       Event event, Addon addon, Map<String, Object> variables) {
        this.player = player;
        this.attacker = attacker;
        this.victim = victim;
        this.target = target;
        this.event = event;
        this.addon = addon;
        this.variables = variables != null ? variables : Collections.emptyMap();
    }

    public static NanoContext of(Player player, Event event, Addon addon) {
        Entity attacker = null;
        Entity victim = null;

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            victim = damageEvent.getEntity();
            Entity damager = damageEvent.getDamager();
            if (damager instanceof Projectile) {
                ProjectileSource shooter = ((Projectile) damager).getShooter();
                if (shooter instanceof Entity) {
                    attacker = (Entity) shooter;
                } else {
                    attacker = damager;
                }
            } else {
                attacker = damager;
            }
        } else if (event instanceof EntityDamageEvent) {
            victim = ((EntityDamageEvent) event).getEntity();
        }

        if (player == null) {
            if (attacker instanceof Player) {
                player = (Player) attacker;
            } else if (victim instanceof Player) {
                player = (Player) victim;
            }
        }

        return new NanoContext(player, attacker, victim, null, event, addon, Collections.emptyMap());
    }

    public static NanoContext ofPlayer(Player player) {
        return new NanoContext(player, null, null, null, null, null, Collections.emptyMap());
    }

    public NanoContext withVariable(String key, Object val) {
        Map<String, Object> map = new HashMap<>(variables);
        map.put(key, val);
        return new NanoContext(player, attacker, victim, target, event, addon, map);
    }

    public Player getPlayer() { return player; }
    public Entity getAttacker() { return attacker; }
    public Entity getVictim() { return victim; }
    public Entity getTarget() { return target; }
    public Event getEvent() { return event; }
    public Addon getAddon() { return addon; }
    public Map<String, Object> getVariables() { return variables; }

    public Object resolveVar(String name) {
        if (name == null || name.isEmpty()) return null;
        String lower = name.toLowerCase();

        switch (lower) {
            case "player": return player;
            case "attacker": return attacker;
            case "victim": return victim;
            case "target": return target;
            case "event": return event;
            default: break;
        }

        if (variables.containsKey(name)) {
            return variables.get(name);
        }

        // Если переменная не найдена, пробуем получить свойство у дефолтного игрока
        if (player != null) {
            Object prop = BukkitBinding.getProperty(player, name);
            if (prop != null) return prop;
        }

        // Проверяем переменные аддона
        if (addon != null && addon.getStorage() != null) {
            if (player != null) {
                String varVal = addon.getStorage().getVar(player, name, null);
                if (varVal != null) return varVal;
            }
            String globalVal = addon.getStorage().getGlobalVar(name, null);
            if (globalVal != null) return globalVal;
        }

        return null;
    }
}
