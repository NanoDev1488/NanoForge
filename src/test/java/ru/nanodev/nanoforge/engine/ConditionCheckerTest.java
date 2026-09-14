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

    // ---------- confirm ----------

    @Test
    void confirmBlocksFirstClickAndPassesOnSecondClickWithinWindow() {
        Map<String, Object> confirmBlock = new LinkedHashMap<>();
        confirmBlock.put("seconds", 10);
        confirmBlock.put("key", "delete_base");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("confirm", confirmBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void confirmRequiresTwoClicksAgainAfterBeingConsumed() {
        Map<String, Object> confirmBlock = new LinkedHashMap<>();
        confirmBlock.put("seconds", 10);
        confirmBlock.put("key", "delete_base_2");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("confirm", confirmBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse(); // 1-й клик
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();  // 2-й клик - подтверждено
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse(); // снова 1-й клик нового цикла
    }

    @Test
    void confirmWithDifferentKeysAreIndependent() {
        Map<String, Object> confirmA = new LinkedHashMap<>();
        confirmA.put("seconds", 10);
        confirmA.put("key", "action_a");
        Map<String, Object> ifA = new LinkedHashMap<>();
        ifA.put("confirm", confirmA);

        Map<String, Object> confirmB = new LinkedHashMap<>();
        confirmB.put("seconds", 10);
        confirmB.put("key", "action_b");
        Map<String, Object> ifB = new LinkedHashMap<>();
        ifB.put("confirm", confirmB);

        assertThat(ConditionChecker.check(actionWithIf(ifA), player, addon)).isFalse(); // 1-й клик по A
        assertThat(ConditionChecker.check(actionWithIf(ifB), player, addon)).isFalse(); // 1-й клик по B - не путается с A
        assertThat(ConditionChecker.check(actionWithIf(ifA), player, addon)).isTrue();  // 2-й клик по A - подтверждено
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
