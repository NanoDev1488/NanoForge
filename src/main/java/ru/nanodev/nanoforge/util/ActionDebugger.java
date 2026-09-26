package ru.nanodev.nanoforge.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /nano debug <аддон> включает/выключает подробное логирование - каждый
 * выполненный (или пропущенный по условию) action этого аддона пишется в
 * консоль с типом и итогом. Нужен для отладки длинных цепочек actions
 * (delay/random/call), где по обычным логам не всегда понятно, какая именно
 * ветка сработала. Состояние специально хранится в памяти (не в addon.yml) -
 * это отладочный режим для текущей сессии сервера, включать его "навсегда"
 * никому не нужно, а при рестарте сервера он и так должен сбрасываться.
 */
public final class ActionDebugger {

    private static final Set<String> ENABLED_ADDONS = ConcurrentHashMap.newKeySet();

    private ActionDebugger() {
    }

    public static boolean isEnabled(String addonName) {
        return addonName != null && ENABLED_ADDONS.contains(addonName.toLowerCase());
    }

    /** Переключает и возвращает НОВОЕ состояние (true - только что включили). */
    public static boolean toggle(String addonName) {
        String key = addonName.toLowerCase();
        if (ENABLED_ADDONS.remove(key)) {
            return false;
        }
        ENABLED_ADDONS.add(key);
        return true;
    }

    /** Только для тестов - не должно переживать между тестовыми классами. */
    public static void resetForTests() {
        ENABLED_ADDONS.clear();
    }
}

// by t.me/NanoDev_mc
