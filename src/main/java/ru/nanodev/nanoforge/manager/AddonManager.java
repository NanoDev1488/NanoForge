package ru.nanodev.nanoforge.manager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.dynamic.DynamicCommand;
import ru.nanodev.nanoforge.dynamic.DynamicListener;
import ru.nanodev.nanoforge.inspect.TargetInspector;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class AddonManager {

    private final NanoForgePlugin plugin;
    private final File addonsFolder;
    private final Map<String, Addon> addons = new LinkedHashMap<>();
    private final Map<String, DynamicListener> listeners = new HashMap<>();
    private final Map<String, List<Command>> registeredCommands = new HashMap<>();
    private SimpleCommandMap commandMap;

    public AddonManager(NanoForgePlugin plugin, File addonsFolder) {
        this.plugin = plugin;
        this.addonsFolder = addonsFolder;
        this.commandMap = resolveCommandMap();
    }

    // ---------- загрузка ----------

    public void loadAll() {
        // теперь каждый аддон - это ПАПКА addons/<имя>/ с файлом addon.yml внутри
        File[] dirs = addonsFolder.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            try {
                File yamlFile = new File(dir, "addon.yml");
                if (!yamlFile.exists()) continue;
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
                Addon addon = new Addon(yamlFile, yaml);
                addons.put(addon.getName().toLowerCase(), addon);
                if (addon.isEnabled()) {
                    // На старте сервера НЕ блокируем включение из-за отсутствия целевого плагина -
                    // порядок загрузки плагинов не гарантирован, и он может появиться чуть позже.
                    // Строгая проверка (с отказом) применяется только к ручному /nano enable,
                    // когда сервер уже полностью поднят и все плагины точно загружены.
                    warnIfTargetMissing(addon);
                    enableInternal(addon);
                }
            } catch (Throwable t) {
                // один битый/кривой addon.yml не должен ронять загрузку остальных аддонов
                // (и уж тем более старт всего сервера) - просто пропускаем и пишем в консоль.
                plugin.getLogger().warning("[NanoForge] Не удалось загрузить аддон из папки '"
                        + dir.getName() + "': " + t + " (проверь синтаксис addon.yml)");
            }
        }
    }

    private void warnIfTargetMissing(Addon addon) {
        if (addon.getType() != Addon.Type.ADDON || addon.getTargetPlugin() == null) return;
        org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(addon.getTargetPlugin());
        if (target == null) {
            plugin.getLogger().warning("[NanoForge] Аддон '" + addon.getName() + "': плагин '"
                    + addon.getTargetPlugin() + "' пока не найден на сервере (возможно, ещё не загрузился). "
                    + "Если он реально не установлен - action'ы 'call' у этого аддона просто ничего не будут делать.");
        } else if (!target.isEnabled()) {
            plugin.getLogger().warning("[NanoForge] Аддон '" + addon.getName() + "': плагин '"
                    + addon.getTargetPlugin() + "' установлен, но выключен. Включи его (например, через PlugMan) "
                    + "и перезапусти аддон через /nano reload или /nano enable " + addon.getName());
        }
    }

    /** Полная перезагрузка: выключает всё загруженное и читает addons/ с диска заново. */
    public void reloadAll() {
        disableAll();
        addons.clear();
        loadAll();
    }

    public void disableAll() {
        // ВАЖНО: здесь НЕ вызываем disableInternal() (он персистит enabled=false на диск) -
        // disableAll() используется только внутри reloadAll(), и её задача - снять текущие
        // Bukkit-регистрации перед перечитыванием конфигов, а НЕ погасить админский флаг
        // "включён" на диске. Раньше это было багом: reloadAll() = disableAll() + loadAll(),
        // и loadAll() читал addon.yml, который disableAll() только что переписал в
        // enabled=false - в итоге /nano reload необратимо выключал вообще все аддоны.
        for (Addon a : new ArrayList<>(addons.values())) {
            if (a.isEnabled()) {
                cleanupRegistrations(a.getName());
            }
        }
    }

    /** Клонирует существующий аддон под новым именем. Копия всегда стартует выключенной. */
    public Addon duplicate(String sourceName, String newName) {
        Addon source = get(sourceName);
        if (source == null || get(newName) != null) return null;

        File newFolder = new File(addonsFolder, newName);
        newFolder.mkdirs();
        File newFile = new File(newFolder, "addon.yml");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.getFile());
        yaml.set("name", newName);
        yaml.set("enabled", false);

        Addon addon = new Addon(newFile, yaml);
        addon.save();
        addons.put(newName.toLowerCase(), addon);

        // storage.yml (переменные/кулдауны) копии НЕ переносим - у копии своя история с нуля.
        // target-api.txt перенести можно, он не завязан на конкретный экземпляр аддона.
        if (source.getTargetApiFile().exists()) {
            try {
                java.nio.file.Files.copy(source.getTargetApiFile().toPath(), addon.getTargetApiFile().toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException ignored) {
            }
        }
        return addon;
    }

    /** Пакует папку аддона в один .zip для переноса на другой сервер. Возвращает файл архива. */
    public File exportAddon(String name) throws java.io.IOException {
        Addon addon = get(name);
        if (addon == null) return null;

        File exportsDir = new File(addonsFolder.getParentFile(), "exports");
        exportsDir.mkdirs();
        File zipFile = new File(exportsDir, name + ".nanoaddon.zip");

        File[] files = addon.getFolder().listFiles(File::isFile);
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
            if (files != null) {
                for (File f : files) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                    java.nio.file.Files.copy(f.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }
        return zipFile;
    }

    /**
     * Импортирует аддон из .zip (созданного exportAddon), ищет файл в
     * plugins/NanoForge/imports/<имя_файла>. Импортированный аддон всегда
     * стартует выключенным - его нужно явно включить после проверки.
     */
    public Addon importAddon(String zipFileName, String newName) throws java.io.IOException {
        if (get(newName) != null) return null;

        File importsDir = new File(addonsFolder.getParentFile(), "imports");
        File zipFile = new File(importsDir, zipFileName);
        if (!zipFile.exists()) {
            throw new java.io.IOException("файл не найден: plugins/NanoForge/imports/" + zipFileName);
        }

        File folder = new File(addonsFolder, newName);
        folder.mkdirs();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File out = new File(folder, entry.getName());
                java.nio.file.Files.copy(zis, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        File yamlFile = new File(folder, "addon.yml");
        if (!yamlFile.exists()) {
            throw new java.io.IOException("в архиве нет addon.yml - это не экспорт NanoForge?");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        yaml.set("name", newName);
        yaml.set("enabled", false);

        Addon addon = new Addon(yamlFile, yaml);
        addon.save();
        addons.put(newName.toLowerCase(), addon);
        return addon;
    }

    // ---------- создание ----------

    /** /nano create addon <targetPlugin> <name>
     *  Дополнительно кладёт рядом target-api.txt - дамп классов/методов/полей targetPlugin. */
    public Addon createAddon(String targetPlugin, String name) {
        File folder = new File(addonsFolder, name);
        folder.mkdirs();
        File file = new File(folder, "addon.yml");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("type", "addon");
        yaml.set("target", targetPlugin);
        yaml.set("enabled", false);
        yaml.set("commands." + name.toLowerCase() + ".description", "Команда аддона " + name);
        yaml.set("commands." + name.toLowerCase() + ".actions", exampleOpenMenuAction(name));
        yaml.set("events.PlayerJoinEvent.actions", exampleJoinActions());
        addExampleMenus(yaml, targetPlugin);

        Addon addon = new Addon(file, yaml);
        addon.save();
        addons.put(name.toLowerCase(), addon);

        org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(targetPlugin);
        if (target != null) {
            boolean ok = TargetInspector.dump(target, addon.getTargetApiFile());
            if (ok) {
                plugin.getLogger().info("[NanoForge] Дамп API '" + targetPlugin + "' сохранён в "
                        + addon.getTargetApiFile().getPath());
            }
        } else {
            plugin.getLogger().warning("[NanoForge] Плагин '" + targetPlugin
                    + "' не активен - дамп API не создан (можно сделать позже, пере создав аддон).");
        }

        return addon;
    }

    /** /nano create new <name> */
    public Addon createNew(String name) {
        File folder = new File(addonsFolder, name);
        folder.mkdirs();
        File file = new File(folder, "addon.yml");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("type", "new");
        yaml.set("enabled", false);
        yaml.set("commands." + name.toLowerCase() + ".description", "Команда плагина " + name);
        yaml.set("commands." + name.toLowerCase() + ".actions", exampleOpenMenuAction(name));
        yaml.set("events.PlayerJoinEvent.actions", exampleJoinActions());
        addExampleMenus(yaml, null);

        Addon addon = new Addon(file, yaml);
        addon.save();
        addons.put(name.toLowerCase(), addon);
        return addon;
    }

    private List<Map<String, Object>> exampleOpenMenuAction(String name) {
        // команда аддона по умолчанию сразу открывает пример GUI-меню
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("type", "openmenu");
        open.put("menu", "main");
        list.add(open);
        return list;
    }

    /** Кладёт в yaml пример меню "main" с подменю "sub" - демонстрирует message/call/openmenu/closemenu. */
    private void addExampleMenus(YamlConfiguration yaml, String targetPlugin) {
        yaml.set("menus.main.title", "&8★ Меню аддона ★");
        yaml.set("menus.main.rows", 3);

        Map<String, Object> infoItem = new LinkedHashMap<>();
        infoItem.put("material", "PAPER");
        infoItem.put("name", "&f✔ Простое действие");
        infoItem.put("lore", Arrays.asList("&7Клик - сообщение в чат", "&7(есть плейсхолдер {player})"));
        infoItem.put("actions", singleAction(msgAction("&aТы нажал на кнопку, {player}!")));
        yaml.set("menus.main.items.11", infoItem);

        // пример кнопки с условием доступа по праву
        Map<String, Object> adminItem = new LinkedHashMap<>();
        adminItem.put("material", "GOLD_INGOT");
        adminItem.put("name", "&6⚠ Только для админов");
        adminItem.put("lore", Arrays.asList("&7Пример if: permission"));
        Map<String, Object> adminAction = msgAction("&aДоступ разрешён - у тебя есть право.");
        Map<String, Object> ifCond = new LinkedHashMap<>();
        ifCond.put("permission", "nano.admin");
        ifCond.put("deny_message", "&cНет прав nano.admin");
        adminAction.put("if", ifCond);
        adminItem.put("actions", singleAction(adminAction));
        yaml.set("menus.main.items.12", adminItem);

        Map<String, Object> subItem = new LinkedHashMap<>();
        subItem.put("material", "CHEST");
        subItem.put("name", "&e➤ Подменю");
        subItem.put("lore", Arrays.asList("&7Открывает вложенное меню"));
        Map<String, Object> openSub = new LinkedHashMap<>();
        openSub.put("type", "openmenu");
        openSub.put("menu", "sub");
        subItem.put("actions", java.util.Collections.singletonList(openSub));
        yaml.set("menus.main.items.13", subItem);

        Map<String, Object> closeItem = new LinkedHashMap<>();
        closeItem.put("material", "BARRIER");
        closeItem.put("name", "&c✖ Закрыть");
        Map<String, Object> close = new LinkedHashMap<>();
        close.put("type", "closemenu");
        closeItem.put("actions", java.util.Collections.singletonList(close));
        yaml.set("menus.main.items.15", closeItem);

        // подменю "sub" - тут же пример вызова метода целевого плагина через GUI
        yaml.set("menus.sub.title", "&8➤ Подменю - действия с плагином");
        yaml.set("menus.sub.rows", 3);

        Map<String, Object> callItem = new LinkedHashMap<>();
        callItem.put("material", "COMMAND_BLOCK");
        callItem.put("name", "&b✔ Вызвать метод плагина");
        List<String> lore = new ArrayList<>();
        lore.add("&7type: call - дёргает метод");
        lore.add("&7целевого плагина через рефлексию");
        callItem.put("lore", lore);
        List<Map<String, Object>> callActions = new ArrayList<>();
        if (targetPlugin != null) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("type", "call");
            call.put("plugin", targetPlugin);
            call.put("method", "someMethod");
            call.put("args", java.util.Collections.emptyList());
            callActions.add(call);
        }
        callActions.add(msgAction("&aГотово (посмотри target-api.txt чтобы взять реальный метод)"));
        callItem.put("actions", callActions);
        yaml.set("menus.sub.items.11", callItem);

        Map<String, Object> backItem = new LinkedHashMap<>();
        backItem.put("material", "ARROW");
        backItem.put("name", "&7➤ Назад");
        Map<String, Object> back = new LinkedHashMap<>();
        back.put("type", "openmenu");
        back.put("menu", "main");
        backItem.put("actions", java.util.Collections.singletonList(back));
        yaml.set("menus.sub.items.15", backItem);
    }

    private Map<String, Object> msgAction(String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "message");
        msg.put("text", text);
        return msg;
    }

    private List<Map<String, Object>> singleAction(Map<String, Object> action) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(action);
        return list;
    }

    private List<Map<String, Object>> exampleJoinActions() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "broadcast");
        msg.put("text", "&e➤ {player} зашёл на сервер (пример события из аддона)");
        list.add(msg);
        return list;
    }

    // ---------- включение / выключение ----------

    public boolean enable(String name) {
        Addon addon = addons.get(name.toLowerCase());
        if (addon == null) return false;

        // Если это аддон К ДРУГОМУ ПЛАГИНУ, а плагина-цели сейчас нет на сервере ИЛИ он
        // есть, но выключен - не включаем вслепую (иначе первый же вызов call/меню
        // упадёт с мусорным исключением где-то в недрах Bukkit). Вместо этого - одна
        // понятная строка в консоль, и аддон остаётся выключенным (см. NanoCommand -
        // там же для выключенного-но-установленного плагина предлагается кнопка PlugMan).
        if (addon.getType() == Addon.Type.ADDON && addon.getTargetPlugin() != null) {
            org.bukkit.plugin.Plugin target = Bukkit.getPluginManager().getPlugin(addon.getTargetPlugin());
            if (target == null || !target.isEnabled()) {
                plugin.getLogger().warning("[NanoForge] Аддон '" + addon.getName() + "' НЕ включён: плагин '"
                        + addon.getTargetPlugin() + "' " + (target == null ? "не найден" : "выключен") + " на сервере.");
                addon.setEnabled(false);
                addon.save();
                return false;
            }
        }

        return enableInternal(addon);
    }

    public boolean disable(String name) {
        Addon addon = addons.get(name.toLowerCase());
        if (addon == null) return false;
        return disableInternal(addon);
    }

    private boolean enableInternal(Addon addon) {
        // защита от повторного /nano enable уже включённого аддона: если по какой-то
        // причине для этого имени уже что-то зарегистрировано (листенер/команды),
        // сначала аккуратно снимаем старое, чтобы не задвоить регистрацию в Bukkit.
        cleanupRegistrations(addon.getName());

        // события
        DynamicListener listener = new DynamicListener(plugin);
        for (String eventKey : addon.getEventKeys()) {
            listener.register(addon, eventKey);
        }
        listeners.put(addon.getName().toLowerCase(), listener);

        // команды
        List<Command> cmds = new ArrayList<>();
        if (commandMap != null) {
            for (String cmdKey : addon.getCommandKeys()) {
                DynamicCommand cmd = new DynamicCommand(addon, cmdKey);
                commandMap.register(plugin.getName().toLowerCase(), cmd);
                cmds.add(cmd);
            }
        }
        registeredCommands.put(addon.getName().toLowerCase(), cmds);

        addon.setEnabled(true);
        addon.save();
        plugin.getLogger().info("[NanoForge] Включён: " + addon.getName());
        return true;
    }

    private boolean disableInternal(Addon addon) {
        cleanupRegistrations(addon.getName());
        addon.setEnabled(false);
        addon.save();
        plugin.getLogger().info("[NanoForge] Выключен: " + addon.getName());
        return true;
    }

    /** Снимает все текущие Bukkit-регистрации (команды + слушатель события) для этого имени аддона, если они есть. */
    private void cleanupRegistrations(String addonName) {
        String key = addonName.toLowerCase();
        List<Command> cmds = registeredCommands.remove(key);
        if (cmds != null) {
            for (Command c : cmds) {
                c.unregister(commandMap);
            }
        }
        DynamicListener listener = listeners.remove(key);
        if (listener != null) {
            // Полный unregister: снимает этот Listener-объект со ВСЕХ HandlerList
            // Bukkit'а разом, независимо от того, на какие события он был подписан.
            org.bukkit.event.HandlerList.unregisterAll(listener);
        }
        // если у кого-то прямо сейчас открыто GUI-меню этого аддона - закрываем,
        // чтобы клик по кнопке не обращался к логике, которой только что не стало
        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().closeMenusForAddon(addonName);
        }
    }

    // ---------- прочее ----------

    private SimpleCommandMap resolveCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (SimpleCommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("[NanoForge] Не удалось получить CommandMap: " + e.getMessage());
            return null;
        }
    }

    public Collection<Addon> getAddons() {
        return addons.values();
    }

    public Addon get(String name) {
        return addons.get(name.toLowerCase());
    }

    public List<String> getAddonNames() {
        return new ArrayList<>(addons.keySet());
    }
}

// by t.me/NanoDev_mc
