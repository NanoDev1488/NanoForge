package ru.nanodev.nanoforge.engine;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionCheckerTest {

    @Mock
    private Player player;
    @Mock
    private World world;

    @TempDir
    File tempDir;

    private Addon addon;

    @BeforeEach
    void setUp() {
        // Addon не трогает реальный Bukkit-сервер - только YamlConfiguration, поэтому
        // его можно строить напрямую во временной папке без MockBukkit.
        File addonYml = new File(tempDir, "addon.yml");
        addon = new Addon(addonYml, new YamlConfiguration());

        lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private Map<String, Object> actionWithIf(Map<String, Object> ifBlock) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "message");
        action.put("text", "irrelevant");
        action.put("if", ifBlock);
        return action;
    }

    // ---------- permission / not_permission ----------

    @Test
    void permissionPassesWhenPlayerHasIt() {
        when(player.hasPermission("nano.vip")).thenReturn(true);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("permission", "nano.vip");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void permissionFailsWhenPlayerLacksIt() {
        when(player.hasPermission("nano.vip")).thenReturn(false);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("permission", "nano.vip");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void notPermissionPassesWhenPlayerLacksIt() {
        when(player.hasPermission("banned.flag")).thenReturn(false);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("not_permission", "banned.flag");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void notPermissionFailsWhenPlayerHasIt() {
        when(player.hasPermission("banned.flag")).thenReturn(true);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("not_permission", "banned.flag");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void nullPlayerFailsPermissionCheck() {
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("permission", "nano.vip");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), null, addon)).isFalse();
    }

    // ---------- world ----------

    @Test
    void worldMatches() {
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world_nether");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("world", "world_nether");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void worldMismatches() {
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("world", "world_nether");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    // ---------- var_equals / var_at_least (реальное хранилище на диске) ----------

    @Test
    void varEqualsPassesWhenValueMatches() {
        addon.getStorage().setVar(player, "quest", "2");
        Map<String, Object> varBlock = new LinkedHashMap<>();
        varBlock.put("key", "quest");
        varBlock.put("value", "2");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("var_equals", varBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void varEqualsFailsWhenValueDiffers() {
        addon.getStorage().setVar(player, "quest", "1");
        Map<String, Object> varBlock = new LinkedHashMap<>();
        varBlock.put("key", "quest");
        varBlock.put("value", "2");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("var_equals", varBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void varAtLeastPasses() {
        addon.getStorage().setVar(player, "coins", "150");
        Map<String, Object> varBlock = new LinkedHashMap<>();
        varBlock.put("key", "coins");
        varBlock.put("value", "100");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("var_at_least", varBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void varAtLeastFailsWhenBelowThreshold() {
        addon.getStorage().setVar(player, "coins", "50");
        Map<String, Object> varBlock = new LinkedHashMap<>();
        varBlock.put("key", "coins");
        varBlock.put("value", "100");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("var_at_least", varBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    // ---------- cooldown ----------

    @Test
    void cooldownPassesFirstTimeAndBlocksSecondTime() {
        Map<String, Object> cooldownBlock = new LinkedHashMap<>();
        cooldownBlock.put("seconds", 30);
        cooldownBlock.put("key", "heal");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("cooldown", cooldownBlock);

        // первый вызов - кулдауна ещё не было, проходит и сразу отмечает использование
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
        // второй вызов сразу после - кулдаун 30 секунд ещё не истёк
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void cooldownZeroSecondsAlwaysPasses() {
        Map<String, Object> cooldownBlock = new LinkedHashMap<>();
        cooldownBlock.put("seconds", 0);
        cooldownBlock.put("key", "instant");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("cooldown", cooldownBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    // ---------- отсутствие if вообще ----------

    @Test
    void actionWithoutIfAlwaysPasses() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "message");
        action.put("text", "no condition here");

        assertThat(ConditionChecker.check(action, player, addon)).isTrue();
    }

    // ---------- time ----------

    @Test
    void timeWithinSimpleRangePasses() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(15000L);
        Map<String, Object> timeBlock = new LinkedHashMap<>();
        timeBlock.put("min", 13000);
        timeBlock.put("max", 23000);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("time", timeBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void timeOutsideSimpleRangeFails() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(5000L);
        Map<String, Object> timeBlock = new LinkedHashMap<>();
        timeBlock.put("min", 13000);
        timeBlock.put("max", 23000);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("time", timeBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void timeRangeWrappingPastMidnightPasses() {
        // min > max означает диапазон "через полночь" (например ночь: 22000 -> 2000)
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(23500L);
        Map<String, Object> timeBlock = new LinkedHashMap<>();
        timeBlock.put("min", 22000);
        timeBlock.put("max", 2000);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("time", timeBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void timeRangeWrappingPastMidnightFailsInDaytime() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(8000L);
        Map<String, Object> timeBlock = new LinkedHashMap<>();
        timeBlock.put("min", 22000);
        timeBlock.put("max", 2000);
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("time", timeBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }
}

// by t.me/NanoDev_mc
