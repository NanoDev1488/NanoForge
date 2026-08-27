package ru.nanodev.nanoforge.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Разбирает одну строку, введённую игроком в чат при редактировании пункта меню
 * (см. MenuManager - режим "правки логики" по shift-клику), в один action-объект
 * того же формата, что используется в addon.yml.
 *
 * Синтаксис (первое слово - тип action, дальше - его параметры через пробел):
 *   message <текст>
 *   broadcast <текст>
 *   console <команда>
 *   call <плагин> <метод> [арг1 арг2 ...]
 *   openmenu <меню> [аддон]
 *   closemenu
 *   setvar <ключ> <значение> [global]
 *   addvar <ключ> <число> [global]
 *   eco_give <число>
 *   eco_take <число>
 */
public class ActionLineParser {

    /** @return готовый action (Map) или null, если строка не распознана. errorOut[0] заполняется описанием ошибки. */
    public static Map<String, Object> parse(String line, String[] errorOut) {
        if (line == null || line.trim().isEmpty()) {
            errorOut[0] = "Пустая строка.";
            return null;
        }
        String trimmed = line.trim();
        String[] parts = trimmed.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1] : "";

        Map<String, Object> action = new LinkedHashMap<>();

        switch (cmd) {
            case "message":
            case "broadcast":
                if (rest.isEmpty()) { errorOut[0] = cmd + " требует текст"; return null; }
                action.put("type", cmd);
                action.put("text", rest);
                return action;

            case "console":
                if (rest.isEmpty()) { errorOut[0] = "console требует команду"; return null; }
                action.put("type", "console");
                action.put("command", rest);
                return action;

            case "call": {
                String[] a = rest.split(" ");
                if (a.length < 2) { errorOut[0] = "call требует: <плагин> <метод> [аргументы]"; return null; }
                action.put("type", "call");
                action.put("plugin", a[0]);
                action.put("method", a[1]);
                java.util.List<String> args = new java.util.ArrayList<>();
                for (int i = 2; i < a.length; i++) args.add(a[i]);
                action.put("args", args);
                return action;
            }

            case "openmenu": {
                String[] a = rest.split(" ");
                if (a.length < 1 || a[0].isEmpty()) { errorOut[0] = "openmenu требует: <меню> [аддон]"; return null; }
                action.put("type", "openmenu");
                action.put("menu", a[0]);
                if (a.length > 1) action.put("addon", a[1]);
                return action;
            }

            case "closemenu":
                action.put("type", "closemenu");
                return action;

            case "setvar": {
                String[] a = rest.split(" ", 3);
                if (a.length < 2) { errorOut[0] = "setvar требует: <ключ> <значение> [global]"; return null; }
                action.put("type", "setvar");
                action.put("key", a[0]);
                action.put("value", a[1]);
                if (a.length > 2 && a[2].equalsIgnoreCase("global")) action.put("scope", "global");
                return action;
            }

            case "addvar": {
                String[] a = rest.split(" ");
                if (a.length < 2) { errorOut[0] = "addvar требует: <ключ> <число> [global]"; return null; }
                action.put("type", "addvar");
                action.put("key", a[0]);
                action.put("amount", a[1]);
                if (a.length > 2 && a[2].equalsIgnoreCase("global")) action.put("scope", "global");
                return action;
            }

            case "eco_give":
            case "eco_take":
                if (rest.isEmpty()) { errorOut[0] = cmd + " требует число"; return null; }
                action.put("type", cmd);
                action.put("amount", rest.trim());
                return action;

            default:
                errorOut[0] = "Неизвестный тип действия: " + cmd
                        + " (доступно: message, broadcast, console, call, openmenu, closemenu, setvar, addvar, eco_give, eco_take)";
                return null;
        }
    }
}

// by t.me/NanoDev_mc
