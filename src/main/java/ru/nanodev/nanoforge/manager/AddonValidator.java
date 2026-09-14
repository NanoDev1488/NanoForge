package ru.nanodev.nanoforge.manager;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import ru.nanodev.nanoforge.dynamic.DynamicListener;
import ru.nanodev.nanoforge.model.Addon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Сухая" проверка уже распарсенного Addon (см. /nano validate) - НЕ регистрирует
 * ни команды, ни события, ни меню, только читает YAML и ищет типичные опечатки:
 * ссылки на несуществующие меню, неизвестные материалы, неизвестные типы action,
 * события, которые не резолвятся ни в одном известном пакете org.bukkit.event.*.
 *
 * Плейсхолдеры ({arg1}, {player} и т.д.) внутри значений НЕ разворачиваются -
 * поля, содержащие "{", статической проверке не подвергаются (их нельзя
 * проверить не выполнив), это единственное намеренное упрощение.
 */
public final class AddonValidator {

    private static final Set<String> KNOWN_ACTION_TYPES = new HashSet<>(java.util.Arrays.asList(
            "message", "broadcast", "console", "call", "openmenu", "closemenu",
            "setvar", "addvar", "eco_give", "eco_take", "give_item",
            "play_sound", "teleport", "title", "delay"
    ));

    private AddonValidator() {
    }

    public static List<String> validate(Addon addon) {
        List<String> issues = new ArrayList<>();

        if (addon.getName() == null || addon.getName().trim().isEmpty()) {
            issues.add("ERROR: 'name' не задан");
        }
        if (addon.getType() == Addon.Type.ADDON
                && (addon.getTargetPlugin() == null || addon.getTargetPlugin().trim().isEmpty())) {
            issues.add("ERROR: type: addon требует поле 'target'");
        }

        Set<String> menuKeys = new HashSet<>(addon.getMenuKeys());
        if (menuKeys.isEmpty() && addon.getCommandKeys().isEmpty() && addon.getEventKeys().isEmpty()) {
            issues.add("WARN: у аддона нет ни команд, ни событий, ни меню - он ничего не делает");
        }

        for (String cmdKey : addon.getCommandKeys()) {
            checkActions(addon.getCommandActions(cmdKey), menuKeys, issues, "commands." + cmdKey);
        }

        for (String eventKey : addon.getEventKeys()) {
            if (DynamicListener.resolveEventClass(eventKey) == null) {
                issues.add("WARN: events." + eventKey + " - класс события не найден ни в одном из "
                        + "стандартных пакетов org.bukkit.event.* (если это событие стороннего "
                        + "плагина, укажи полный путь класса)");
            }
            checkActions(addon.getEventActions(eventKey), menuKeys, issues, "events." + eventKey);
        }

        for (String menuKey : menuKeys) {
            ConfigurationSection items = addon.getMenuItemsSection(menuKey);
            if (items == null) continue;
            int maxSlot = addon.getMenuRows(menuKey) * 9 - 1;
            for (String slotKey : items.getKeys(false)) {
                String path = "menus." + menuKey + ".items." + slotKey;
                int slot;
                try {
                    slot = Integer.parseInt(slotKey);
                } catch (NumberFormatException e) {
                    issues.add("ERROR: " + path + " - ключ слота должен быть числом, а не '" + slotKey + "'");
                    continue;
                }
                if (slot < 0 || slot > maxSlot) {
                    issues.add("ERROR: " + path + " - слот " + slot + " вне диапазона меню (0-" + maxSlot
                            + " при rows: " + addon.getMenuRows(menuKey) + ")");
                }
                ConfigurationSection item = items.getConfigurationSection(slotKey);
                if (item == null) continue;
                checkMaterial(item.getString("material"), path + ".material", issues);
                checkActions(item.getList("actions"), menuKeys, issues, path);
            }
        }

        if (issues.isEmpty()) {
            issues.add("OK: проблем не найдено (" + menuKeys.size() + " меню, "
                    + addon.getCommandKeys().size() + " команд, " + addon.getEventKeys().size() + " событий)");
        }
        return issues;
    }

    @SuppressWarnings("unchecked")
    private static void checkActions(List<?> actions, Set<String> menuKeys, List<String> issues, String path) {
        if (actions == null) return;
        int i = 0;
        for (Object raw : actions) {
            String actionPath = path + ".actions[" + i + "]";
            i++;
            if (!(raw instanceof Map)) {
                issues.add("ERROR: " + actionPath + " - каждый action должен быть map с ключом 'type'");
                continue;
            }
            Map<String, Object> action = (Map<String, Object>) raw;
            String type = String.valueOf(action.getOrDefault("type", "")).toLowerCase();
            if (type.isEmpty()) {
                issues.add("ERROR: " + actionPath + " - не указан 'type'");
                continue;
            }
            if (!KNOWN_ACTION_TYPES.contains(type)) {
                issues.add("WARN: " + actionPath + " - неизвестный type '" + type + "' (опечатка?)");
            }
            if ("openmenu".equals(type) && !action.containsKey("addon")) {
                String menu = String.valueOf(action.get("menu"));
                if (!menu.contains("{") && !menuKeys.contains(menu)) {
                    issues.add("ERROR: " + actionPath + " - openmenu ссылается на несуществующее меню '" + menu + "'"
                            + " (доступные: " + menuKeys + ")");
                }
            }
            if ("give_item".equals(type) && action.containsKey("material")) {
                checkMaterial(String.valueOf(action.get("material")), actionPath + ".material", issues);
            }
            if ("delay".equals(type)) {
                Object nested = action.get("actions");
                if (!(nested instanceof List)) {
                    issues.add("ERROR: " + actionPath + " - delay требует вложенный список 'actions'");
                } else {
                    checkActions((List<?>) nested, menuKeys, issues, actionPath + ".delay");
                }
            }
        }
    }

    private static void checkMaterial(String material, String path, List<String> issues) {
        if (material == null || material.contains("{")) return; // плейсхолдер - не проверяем статически
        try {
            Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            issues.add("ERROR: " + path + " - неизвестный материал '" + material + "'");
        }
    }
}

// by t.me/NanoDev_mc
