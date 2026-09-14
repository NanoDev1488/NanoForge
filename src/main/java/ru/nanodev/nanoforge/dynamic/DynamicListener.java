package ru.nanodev.nanoforge.dynamic;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.model.Addon;

import java.util.ArrayList;
import java.util.List;

/**
 * Регистрирует события Bukkit "вручную" (без аннотаций @EventHandler),
 * потому что список событий берётся динамически из YAML аддона.
 * В названии события в yml можно писать короткое имя (PlayerJoinEvent)
 * или полный путь класса (org.bukkit.event.player.PlayerJoinEvent).
 */
public class DynamicListener implements Listener {

    // Пакеты, где ищем событие по короткому имени, если полный путь не указан
    private static final String[] PACKAGES = {
            "org.bukkit.event.player.",
            "org.bukkit.event.block.",
            "org.bukkit.event.entity.",
            "org.bukkit.event.inventory.",
            "org.bukkit.event.world.",
            "org.bukkit.event.server.",
            "org.bukkit.event.weather.",
            "org.bukkit.event.vehicle.",
            "org.bukkit.event.hanging."
    };

    private final NanoForgePlugin plugin;
    private final List<String> registeredWith = new ArrayList<>();

    public DynamicListener(NanoForgePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean register(Addon addon, String eventKey) {
        Class<? extends Event> eventClass = resolveEventClass(eventKey);
        if (eventClass == null) {
            plugin.getLogger().warning("[NanoForge] Не найден класс события: " + eventKey + " (аддон " + addon.getName() + ")");
            return false;
        }

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            if (!addon.isEnabled()) return; // аддон выключен - просто игнорируем событие
            try {
                ActionRunner.run(addon.getEventActions(eventKey), null, event,
                        plugin.getMenuManager(), addon);
            } catch (Throwable t) {
                // ActionRunner уже ловит ошибки внутри отдельных actions, но это - последний
                // рубеж: если что-то всё же прорвалось сюда, Bukkit НЕ должен печатать
                // "Could not pass event ... to NanoForge" с полным стектрейсом на весь чат/консоль.
                plugin.getLogger().warning("[NanoForge] Аддон '" + addon.getName()
                        + "': ошибка при обработке события " + eventKey + ": " + t);
            }
        };

        Bukkit.getPluginManager().registerEvent(
                eventClass, this, EventPriority.NORMAL, executor, plugin, true
        );
        registeredWith.add(eventKey);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Event> resolveEventClass(String name) {
        if (name.contains(".")) {
            try {
                return (Class<? extends Event>) Class.forName(name);
            } catch (Exception ignored) {
                return null;
            }
        }
        String shortName = name.endsWith("Event") ? name : name + "Event";
        for (String pkg : PACKAGES) {
            try {
                return (Class<? extends Event>) Class.forName(pkg + shortName);
            } catch (ClassNotFoundException ignored) {
                // пробуем следующий пакет
            }
        }
        return null;
    }
}

// by t.me/NanoDev_mc
