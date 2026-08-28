package ru.nanodev.nanoforge.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Мост к экономике Vault (плагин Vault + провайдер экономики типа EssentialsX Economy)
 * БЕЗ жёсткой compile-time зависимости от Vault-jar'а - всё через рефлексию,
 * поэтому NanoForge собирается и работает даже если Vault не установлен
 * (просто eco-действия/условия будут молча недоступны).
 *
 * Интерфейс экономики Vault: net.milkbowl.vault.economy.Economy
 */
public class VaultBridge {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static Class<?> economyClass;
    private static boolean checked = false;
    private static Object economyProvider; // инстанс Economy, полученный через ServicesManager

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean isAvailable() {
        if (!checked) {
            checked = true;
            try {
                economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                ServicesManager sm = Bukkit.getServicesManager();
                RegisteredServiceProvider<?> rsp = sm.getRegistration((Class) economyClass);
                if (rsp != null) {
                    economyProvider = rsp.getProvider();
                }
            } catch (Throwable t) {
                economyClass = null;
                economyProvider = null;
            }
            if (economyProvider == null) {
                // одно понятное сообщение при первом же реальном использовании eco_*,
                // а не тишина и не стектрейс - дальше просто ничего не будет происходить.
                LOG.warning("[NanoForge] Vault не найден (или нет плагина-провайдера экономики) - "
                        + "действия eco_give/eco_take и условие eco_at_least работать не будут.");
            }
        }
        return economyProvider != null;
    }

    /** Только для тестов - сбрасывает закешированное состояние проверки Vault, чтобы каждый тест начинал с чистого листа. */
    static void resetForTests() {
        checked = false;
        economyClass = null;
        economyProvider = null;
    }

    public static double getBalance(Player player) {
        if (!isAvailable()) return -1;
        try {
            Method m = economyClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            return (double) m.invoke(economyProvider, player);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Vault getBalance ошибка: " + t);
            return -1;
        }
    }

    public static boolean has(Player player, double amount) {
        if (!isAvailable()) return false;
        try {
            Method m = economyClass.getMethod("has", org.bukkit.OfflinePlayer.class, double.class);
            return (boolean) m.invoke(economyProvider, player, amount);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Vault has() ошибка: " + t);
            return false;
        }
    }

    /** @return true если списание прошло успешно */
    public static boolean withdraw(Player player, double amount) {
        if (!isAvailable()) return false;
        try {
            Method m = economyClass.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = m.invoke(economyProvider, player, amount);
            return readTransactionSuccess(response);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Vault withdraw ошибка: " + t);
            return false;
        }
    }

    /** @return true если начисление прошло успешно */
    public static boolean deposit(Player player, double amount) {
        if (!isAvailable()) return false;
        try {
            Method m = economyClass.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = m.invoke(economyProvider, player, amount);
            return readTransactionSuccess(response);
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Vault deposit ошибка: " + t);
            return false;
        }
    }

    private static boolean readTransactionSuccess(Object econResponse) {
        try {
            // EconomyResponse.transactionSuccess() - публичный метод в API Vault
            Method success = econResponse.getClass().getMethod("transactionSuccess");
            return (boolean) success.invoke(econResponse);
        } catch (Throwable t) {
            return true; // если формат ответа неожиданный - считаем успешным по факту вызова без исключений
        }
    }
}

// by t.me/NanoDev_mc
