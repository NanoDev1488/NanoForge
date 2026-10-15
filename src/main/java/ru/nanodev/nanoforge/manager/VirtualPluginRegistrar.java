package ru.nanodev.nanoforge.manager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Injects and removes {@link VirtualAddonPlugin} instances into Bukkit's {@link SimplePluginManager}
 * so that registered addons appear in {@code /plugins}, {@code Bukkit.getPluginManager().getPlugins()},
 * and can be found via {@code Bukkit.getPluginManager().getPlugin("NanoForge-AddonName")}.
 */
public final class VirtualPluginRegistrar {

    private static final Logger LOGGER = Logger.getLogger("NanoForge-Registrar");

    private VirtualPluginRegistrar() {}

    @SuppressWarnings("unchecked")
    public static boolean register(VirtualAddonPlugin plugin) {
        if (plugin == null) return false;
        PluginManager pm = Bukkit.getPluginManager();
        if (!(pm instanceof SimplePluginManager)) {
            LOGGER.warning("[NanoForge] PluginManager is not an instance of SimplePluginManager; cannot register virtual plugin: " + plugin.getName());
            return false;
        }

        try {
            Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            List<Plugin> plugins = (List<Plugin>) pluginsField.get(pm);

            Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pm);

            synchronized (pm) {
                // Remove any previous instance with same name just in case
                lookupNames.remove(plugin.getName().toLowerCase());
                lookupNames.remove(plugin.getName());
                for (Iterator<Plugin> it = plugins.iterator(); it.hasNext(); ) {
                    Plugin p = it.next();
                    if (p.getName().equalsIgnoreCase(plugin.getName())) {
                        it.remove();
                    }
                }

                plugins.add(plugin);
                lookupNames.put(plugin.getName().toLowerCase(), plugin);
                lookupNames.put(plugin.getName(), plugin);
            }

            plugin.setEnabled(true);
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[NanoForge] Failed to register virtual plugin: " + plugin.getName(), t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean unregister(String pluginName) {
        if (pluginName == null) return false;
        PluginManager pm = Bukkit.getPluginManager();
        if (!(pm instanceof SimplePluginManager)) {
            return false;
        }

        try {
            Field pluginsField = SimplePluginManager.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            List<Plugin> plugins = (List<Plugin>) pluginsField.get(pm);

            Field lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupNamesField.get(pm);

            synchronized (pm) {
                Plugin removed = lookupNames.remove(pluginName.toLowerCase());
                lookupNames.remove(pluginName);

                if (removed instanceof VirtualAddonPlugin) {
                    ((VirtualAddonPlugin) removed).setEnabled(false);
                }

                for (Iterator<Plugin> it = plugins.iterator(); it.hasNext(); ) {
                    Plugin p = it.next();
                    if (p.getName().equalsIgnoreCase(pluginName)) {
                        if (p instanceof VirtualAddonPlugin) {
                            ((VirtualAddonPlugin) p).setEnabled(false);
                        }
                        it.remove();
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[NanoForge] Failed to unregister virtual plugin: " + pluginName, t);
            return false;
        }
    }
}
