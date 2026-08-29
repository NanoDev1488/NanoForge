package ru.nanodev.nanoforge;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты, которые грузят НАСТОЯЩИЙ NanoForgePlugin через MockBukkit
 * (полноценная имитация Bukkit-сервера в памяти: игроки, инвентари, команды,
 * plugin.yml, права по default:op и т.д.) - в отличие от остальных тестов в
 * проекте, которые проверяют изолированные куски логики (ActionRunner,
 * ConditionChecker, PlaceholderUtil...) через точечные моки Mockito.
 *
 * Это и есть ответ на вопрос "можно ли проверить сами плагины через тесты" -
 * да, именно так: не мокаем Bukkit по кусочкам, а поднимаем целый (виртуальный)
 * сервер в памяти и гоняем по нему реальные команды/события/клики так, как если
 * бы это был настоящий Minecraft-сервер.
 *
 * ВНИМАНИЕ: точные названия методов MockBukkit (PlayerMock.nextMessage(),
 * getOpenInventory() и т.п.) могут отличаться между версиями библиотеки -
 * файл собирался без доступа в сеть для проверки. Если что-то из этого не
 * скомпилируется/не пройдёт - смотри актуальный API на
 * https://github.com/MockBukkit/MockBukkit и точечно поправь этот файл,
 * остальные тесты в проекте от MockBukkit не зависят и не пострадают.
 */
class PluginIntegrationTest {

    private ServerMock server;
    private NanoForgePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NanoForgePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnablesSuccessfully() {
        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    void opPlayerCanRunNanoList() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(true);

        player.performCommand("nano list");

        // сама команда не должна падать с исключением, и должно прийти хоть какое-то сообщение
        String message = player.nextMessage();
        assertThat(message).isNotNull();
    }

    @Test
    void nonOpPlayerGetsPermissionDenied() {
        PlayerMock player = server.addPlayer("Regular");
        // не оп - права nano.admin по умолчанию только у op (см. plugin.yml)

        player.performCommand("nano list");

        String message = player.nextMessage();
        assertThat(message).isNotNull().contains("прав"); // "Нет прав." из NanoCommand.onCommand
    }

    @Test
    void createNewAddonPersistsAddonYmlToDisk() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);

        admin.performCommand("nano create new TestAddon");

        File addonFile = new File(plugin.getDataFolder(), "addons/TestAddon/addon.yml");
        assertThat(addonFile).exists();
    }

    @Test
    void enableAddonThenOpenDefaultMenu() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);

        admin.performCommand("nano create new TestMenuAddon");
        admin.performCommand("nano enable TestMenuAddon");
        admin.performCommand("nano menu TestMenuAddon main");

        // шаблон, генерируемый при создании аддона, всегда включает меню "main" с rows: 3
        // (см. AddonManager.addExampleMenus) - значит размер инвентаря должен быть 3*9=27
        assertThat(admin.getOpenInventory()).isNotNull();
        assertThat(admin.getOpenInventory().getTopInventory().getSize()).isEqualTo(27);
    }

    @Test
    void disabledAddonCommandDoesNothingHarmful() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);

        admin.performCommand("nano create new NeverEnabled");
        // НЕ включаем аддон - его команда не должна быть даже зарегистрирована,
        // выполнение несуществующей команды не должно ронять сервер/тест
        admin.performCommand("neverenabled");

        // просто убеждаемся, что дошли досюда без исключения - основная проверка теста
        assertThat(plugin.isEnabled()).isTrue();
    }
}

// by t.me/NanoDev_mc
