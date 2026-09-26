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
import static org.mockito.Mockito.mock;
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

    @Test
    void globalCooldownIsSharedAcrossDifferentPlayers() {
        Player otherPlayer = org.mockito.Mockito.mock(Player.class);
        lenient().when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        Map<String, Object> cooldownBlock = new LinkedHashMap<>();
        cooldownBlock.put("seconds", 30);
        cooldownBlock.put("key", "server_event");
        cooldownBlock.put("scope", "global");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("cooldown", cooldownBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
        // ДРУГОЙ игрок - но кулдаун global, поэтому тоже должен быть заблокирован
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), otherPlayer, addon)).isFalse();
    }

    @Test
    void perPlayerCooldownDoesNotAffectOtherPlayers() {
        Player otherPlayer = org.mockito.Mockito.mock(Player.class);
        lenient().when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        Map<String, Object> cooldownBlock = new LinkedHashMap<>();
        cooldownBlock.put("seconds", 30);
        cooldownBlock.put("key", "heal2");
        // scope не указан - по умолчанию "player"
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("cooldown", cooldownBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), otherPlayer, addon)).isTrue();
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

    @Test
    void globalConfirmCanBeConsumedByADifferentPlayer() {
        Player otherPlayer = org.mockito.Mockito.mock(Player.class);
        lenient().when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        Map<String, Object> confirmBlock = new LinkedHashMap<>();
        confirmBlock.put("seconds", 10);
        confirmBlock.put("key", "wipe_server");
        confirmBlock.put("scope", "global");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("confirm", confirmBlock);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();       // 1-й клик, игрок A
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), otherPlayer, addon)).isTrue();   // 2-й клик, игрок B - подтверждает
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

    // ---------- any_of / all_of / not ----------

    @Test
    void anyOfPassesWhenAtLeastOneNestedConditionPasses() {
        when(player.hasPermission("a")).thenReturn(false);
        when(player.hasPermission("b")).thenReturn(true);

        Map<String, Object> condA = new LinkedHashMap<>();
        condA.put("permission", "a");
        Map<String, Object> condB = new LinkedHashMap<>();
        condB.put("permission", "b");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("any_of", java.util.Arrays.asList(condA, condB));

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void anyOfFailsWhenNoneOfTheNestedConditionsPass() {
        when(player.hasPermission("a")).thenReturn(false);
        when(player.hasPermission("b")).thenReturn(false);

        Map<String, Object> condA = new LinkedHashMap<>();
        condA.put("permission", "a");
        Map<String, Object> condB = new LinkedHashMap<>();
        condB.put("permission", "b");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("any_of", java.util.Arrays.asList(condA, condB));

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void allOfFailsWhenAnySingleNestedConditionFails() {
        when(player.hasPermission("a")).thenReturn(true);
        when(player.hasPermission("b")).thenReturn(false);

        Map<String, Object> condA = new LinkedHashMap<>();
        condA.put("permission", "a");
        Map<String, Object> condB = new LinkedHashMap<>();
        condB.put("permission", "b");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("all_of", java.util.Arrays.asList(condA, condB));

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void allOfPassesWhenEveryNestedConditionPasses() {
        when(player.hasPermission("a")).thenReturn(true);
        when(player.hasPermission("b")).thenReturn(true);

        Map<String, Object> condA = new LinkedHashMap<>();
        condA.put("permission", "a");
        Map<String, Object> condB = new LinkedHashMap<>();
        condB.put("permission", "b");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("all_of", java.util.Arrays.asList(condA, condB));

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void notInvertsNestedCondition() {
        when(player.hasPermission("banned")).thenReturn(true);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("permission", "banned");
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("not", inner);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();

        when(player.hasPermission("banned")).thenReturn(false);
        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void notCanWrapAnOrGroupForXorLikeLogic() {
        // not(any_of[a,b]) - проходит только если НИ a, НИ b нет
        when(player.hasPermission("a")).thenReturn(false);
        when(player.hasPermission("b")).thenReturn(false);

        Map<String, Object> condA = new LinkedHashMap<>();
        condA.put("permission", "a");
        Map<String, Object> condB = new LinkedHashMap<>();
        condB.put("permission", "b");
        Map<String, Object> anyOf = new LinkedHashMap<>();
        anyOf.put("any_of", java.util.Arrays.asList(condA, condB));
        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("not", anyOf);

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    // ---------- biome_equals / weather_equals ----------

    @Test
    void biomeEqualsMatchesBlockBiomeAtPlayerLocation() {
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        when(loc.getBlock()).thenReturn(block);
        when(block.getBiome()).thenReturn(org.bukkit.block.Biome.PLAINS);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("biome_equals", "plains");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void biomeEqualsFailsForDifferentBiome() {
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        when(loc.getBlock()).thenReturn(block);
        when(block.getBiome()).thenReturn(org.bukkit.block.Biome.DESERT);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("biome_equals", "plains");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }

    @Test
    void weatherEqualsClearRequiresNoRainAndNoThunder() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.hasStorm()).thenReturn(false);
        when(world.isThundering()).thenReturn(false);

        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("weather_equals", "clear");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void weatherEqualsThunderMatchesThunderstorm() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.hasStorm()).thenReturn(true);
        when(world.isThundering()).thenReturn(true);

        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("weather_equals", "thunder");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isTrue();
    }

    @Test
    void weatherEqualsRainDoesNotMatchThunderstorm() {
        lenient().when(player.getWorld()).thenReturn(world);
        when(world.hasStorm()).thenReturn(true);
        when(world.isThundering()).thenReturn(true);

        Map<String, Object> ifBlock = new LinkedHashMap<>();
        ifBlock.put("weather_equals", "rain");

        assertThat(ConditionChecker.check(actionWithIf(ifBlock), player, addon)).isFalse();
    }
}

// by t.me/NanoDev_mc
