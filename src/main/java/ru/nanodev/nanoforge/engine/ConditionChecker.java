package ru.nanodev.nanoforge.engine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.nanodev.nanoforge.integration.LuckPermsBridge;
import ru.nanodev.nanoforge.integration.VaultBridge;
import ru.nanodev.nanoforge.integration.WorldGuardBridge;
import ru.nanodev.nanoforge.model.Addon;

import java.util.Map;

/**
 * Необязательный блок "if:" внутри action - если условие не выполняется,
 * action пропускается (и, если задано deny_message, игроку шлётся сообщение).
 *
 * Поддерживаемые ключи внутри if:
 *  - permission: "some.node"          -> у игрока должно быть право
 *  - not_permission: "some.node"      -> у игрока НЕ должно быть права
 *  - world: "world_nether"            -> игрок должен быть в этом мире
 *  - region: "spawn"                  -> игрок должен стоять в регионе WorldGuard с этим именем
 *  - eco_at_least: 100                -> у игрока должно быть на балансе Vault-экономики не меньше суммы
 *  - var_equals: { key: "quest", value: "2" }      -> переменная аддона (своя у игрока) равна значению
 *  - var_at_least: { key: "coins", value: "10" }   -> числовая переменная аддона >= значения
 *  - cooldown: { seconds: 30, key: "heal", scope: player|global }  -> не чаще раза в N секунд;
 *                                                      "player" (по умолчанию) - свой у каждого игрока,
 *                                                      "global" - один общий на весь сервер (например,
 *                                                      "эту кнопку может нажать вся команда раз в час").
 *                                                      При успешном прохождении кулдаун сразу обновляется
 *  - confirm: { seconds: 10, key: "delete", scope: player|global } -> требует ДВА клика подряд (в
 *                                                      течение seconds); "global" - подтвердить может
 *                                                      ЛЮБОЙ игрок, а не обязательно тот же самый.
 *                                                      Первый клик всегда проваливает условие и
 *                                                      показывает deny_message ("нажми ещё раз"),
 *                                                      второй - проходит. Удобно для необратимых кнопок.
 *  - time: { min: 13000, max: 23000 }              -> игровое время мира игрока (тики 0-24000,
 *                                                      диапазон может "переходить через полночь": min > max)
 *  - biome_equals: "PLAINS"                         -> биом блока под игроком (org.bukkit.block.Biome)
 *  - weather_equals: "clear"|"rain"|"thunder"       -> текущая погода в мире игрока
 *  - luckperms_group: "vip"                         -> primary-группа игрока в LuckPerms (если он установлен)
 *  - any_of: [ {if-блок}, {if-блок}, ... ]          -> проходит, если ХОТЯ БЫ ОДИН вложенный блок проходит
 *  - all_of: [ {if-блок}, {if-блок}, ... ]          -> проходит, только если ВСЕ вложенные блоки проходят
 *                                                      (эквивалент обычного перечисления через запятую,
 *                                                      но полезен для группировки внутри not/any_of)
 *  - not: { if-блок }                               -> инвертирует вложенный блок
 *  - deny_message: "&cНет доступа"    -> сообщение при провале любого из условий выше (необязательно;
 *                                        для cooldown можно использовать {cooldown} - секунды до конца)
 */
@SuppressWarnings("unchecked")
public class ConditionChecker {

    public static boolean check(Map<String, Object> action, Player player, Addon addon) {
        Object rawIf = action.get("if");
        if (!(rawIf instanceof Map)) return true; // условий нет - action выполняется всегда
        Map<String, Object> cond = (Map<String, Object>) rawIf;
        return evaluate(cond, player, addon, true);
    }

    /** То же самое, но для секции ConfigurationSection (используется для видимости пунктов меню). */
    public static boolean checkVisibility(ConfigurationSection itemSection, Player player) {
        if (itemSection == null) return true;
        ConfigurationSection cond = itemSection.getConfigurationSection("if");
        if (cond == null) return true;

        boolean passed = true;
        if (cond.contains("permission")) {
            passed = player != null && player.hasPermission(cond.getString("permission"));
        }
        if (passed && cond.contains("not_permission")) {
            passed = player == null || !player.hasPermission(cond.getString("not_permission"));
        }
        if (passed && cond.contains("world")) {
            passed = player != null && player.getWorld().getName().equalsIgnoreCase(cond.getString("world"));
        }
        if (passed && cond.contains("region")) {
            passed = player != null && WorldGuardBridge.isInRegion(player, cond.getString("region"));
        }
        if (passed && cond.contains("eco_at_least")) {
            passed = player != null && VaultBridge.has(player, cond.getDouble("eco_at_least"));
        }
        if (passed && cond.contains("time")) {
            ConfigurationSection tc = cond.getConfigurationSection("time");
            passed = player != null && tc != null
                    && withinTimeRange(player.getWorld().getTime(), tc.getLong("min", 0), tc.getLong("max", 24000));
        }
        // var_equals/var_at_least/cooldown для видимости пунктов не поддерживаются намеренно -
        // видимость должна быть дешёвой проверкой без побочных эффектов (кулдаун их имеет).
        return passed;
    }

    private static boolean evaluate(Map<String, Object> cond, Player player, Addon addon, boolean sendDenyMessage) {
        boolean passed = true;
        String cooldownRemainingForMessage = null;

        if (cond.containsKey("permission")) {
            String perm = String.valueOf(cond.get("permission"));
            passed = player != null && player.hasPermission(perm);
        }
        if (passed && cond.containsKey("not_permission")) {
            String perm = String.valueOf(cond.get("not_permission"));
            passed = player == null || !player.hasPermission(perm);
        }
        if (passed && cond.containsKey("world")) {
            String world = String.valueOf(cond.get("world"));
            passed = player != null && player.getWorld().getName().equalsIgnoreCase(world);
        }
        if (passed && cond.containsKey("region")) {
            String region = String.valueOf(cond.get("region"));
            passed = player != null && WorldGuardBridge.isInRegion(player, region);
        }
        if (passed && cond.containsKey("eco_at_least")) {
            double amount = ru.nanodev.nanoforge.util.NumberUtil.toDouble(cond.get("eco_at_least"), 0);
            passed = player != null && VaultBridge.has(player, amount);
        }
        if (passed && cond.containsKey("var_equals") && addon != null && player != null) {
            Map<String, Object> vc = (Map<String, Object>) cond.get("var_equals");
            String key = String.valueOf(vc.get("key"));
            String expected = String.valueOf(vc.get("value"));
            String actual = addon.getStorage().getVar(player, key, "");
            passed = expected.equals(actual);
        }
        if (passed && cond.containsKey("var_at_least") && addon != null && player != null) {
            Map<String, Object> vc = (Map<String, Object>) cond.get("var_at_least");
            String key = String.valueOf(vc.get("key"));
            double expected = ru.nanodev.nanoforge.util.NumberUtil.toDouble(vc.get("value"), 0);
            double actual = addon.getStorage().getVarNumber(player, key, 0);
            passed = actual >= expected;
        }
        if (passed && cond.containsKey("cooldown") && addon != null && player != null) {
            Map<String, Object> cc = (Map<String, Object>) cond.get("cooldown");
            long seconds = (long) ru.nanodev.nanoforge.util.NumberUtil.toDouble(cc.get("seconds"), 0);
            String key = cc.containsKey("key") ? String.valueOf(cc.get("key")) : "default";
            boolean global = "global".equalsIgnoreCase(String.valueOf(cc.getOrDefault("scope", "player")));
            long remaining = global
                    ? addon.getStorage().remainingGlobalCooldownSeconds(key, seconds)
                    : addon.getStorage().remainingCooldownSeconds(player, key, seconds);
            if (remaining > 0) {
                passed = false;
                cooldownRemainingForMessage = String.valueOf(remaining);
            } else if (global) {
                addon.getStorage().markGlobalCooldown(key);
            } else {
                addon.getStorage().markCooldown(player, key); // кулдаун проходит - сразу отмечаем использование
            }
        }
        if (passed && cond.containsKey("confirm") && addon != null && player != null) {
            Map<String, Object> cc = (Map<String, Object>) cond.get("confirm");
            long seconds = (long) ru.nanodev.nanoforge.util.NumberUtil.toDouble(cc.get("seconds"), 10);
            String key = cc.containsKey("key") ? String.valueOf(cc.get("key")) : "default";
            boolean global = "global".equalsIgnoreCase(String.valueOf(cc.getOrDefault("scope", "player")));
            passed = global
                    ? addon.getStorage().checkAndConsumeGlobalConfirm(key, seconds)
                    : addon.getStorage().checkAndConsumeConfirm(player, key, seconds);
        }
        if (passed && cond.containsKey("time")) {
            Map<String, Object> tc = (Map<String, Object>) cond.get("time");
            long min = (long) ru.nanodev.nanoforge.util.NumberUtil.toDouble(tc.get("min"), 0);
            long max = (long) ru.nanodev.nanoforge.util.NumberUtil.toDouble(tc.get("max"), 24000);
            passed = player != null && withinTimeRange(player.getWorld().getTime(), min, max);
        }
        if (passed && cond.containsKey("online_players_at_least")) {
            int need = (int) ru.nanodev.nanoforge.util.NumberUtil.toDouble(cond.get("online_players_at_least"), 0);
            passed = org.bukkit.Bukkit.getOnlinePlayers().size() >= need;
        }
        if (passed && cond.containsKey("biome_equals")) {
            String biome = String.valueOf(cond.get("biome_equals"));
            passed = player != null && player.getLocation().getBlock().getBiome().name().equalsIgnoreCase(biome);
        }
        if (passed && cond.containsKey("weather_equals")) {
            String weather = String.valueOf(cond.get("weather_equals")).toLowerCase();
            passed = player != null && matchesWeather(player, weather);
        }
        if (passed && cond.containsKey("luckperms_group")) {
            String group = String.valueOf(cond.get("luckperms_group"));
            passed = player != null && LuckPermsBridge.isInGroup(player, group);
        }

        // Композиция: any_of/all_of/not - каждый элемент это ПОЛНОЦЕННЫЙ if-блок (может сам
        // содержать любые из условий выше, включая ещё одну вложенную композицию). deny_message
        // вложенных условий намеренно игнорируется (sendDenyMessage=false) - иначе при any_of
        // из трёх непройденных условий игрок получил бы три сообщения разом; deny_message
        // самого внешнего if - единственный, который реально показывается.
        if (passed && cond.containsKey("any_of")) {
            passed = evaluateList(cond.get("any_of"), player, addon, false);
        }
        if (passed && cond.containsKey("all_of")) {
            passed = evaluateList(cond.get("all_of"), player, addon, true);
        }
        if (passed && cond.containsKey("not")) {
            Object rawSub = cond.get("not");
            passed = !(rawSub instanceof Map) || !evaluate((Map<String, Object>) rawSub, player, addon, false);
        }

        if (!passed && sendDenyMessage && player != null && cond.containsKey("deny_message")) {
            String raw = String.valueOf(cond.get("deny_message"));
            if (cooldownRemainingForMessage != null) {
                raw = raw.replace("{cooldown}", cooldownRemainingForMessage);
            }
            String msg = PlaceholderUtil.apply(raw, player);
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        }

        return passed;
    }

    /** requireAll=true -> all_of (все элементы должны пройти), false -> any_of (хотя бы один). */
    private static boolean evaluateList(Object rawList, Player player, Addon addon, boolean requireAll) {
        if (!(rawList instanceof java.util.List)) return false;
        java.util.List<?> list = (java.util.List<?>) rawList;
        if (list.isEmpty()) return requireAll; // пустой all_of вырожденно true, пустой any_of - false
        for (Object item : list) {
            boolean itemPassed = item instanceof Map && evaluate((Map<String, Object>) item, player, addon, false);
            if (requireAll && !itemPassed) return false;
            if (!requireAll && itemPassed) return true;
        }
        return requireAll;
    }

    private static boolean matchesWeather(Player player, String weather) {
        boolean thundering = player.getWorld().isThundering();
        boolean raining = player.getWorld().hasStorm();
        switch (weather) {
            case "clear":
                return !raining && !thundering;
            case "rain":
                return raining && !thundering;
            case "thunder":
                return thundering;
            default:
                return false;
        }
    }

    /** Проверяет тики [0-24000) на попадание в диапазон [min, max]. Если min > max - диапазон "через полночь". */
    private static boolean withinTimeRange(long time, long min, long max) {
        time = ((time % 24000) + 24000) % 24000;
        if (min <= max) {
            return time >= min && time <= max;
        }
        return time >= min || time <= max;
    }
}

// by t.me/NanoDev_mc
