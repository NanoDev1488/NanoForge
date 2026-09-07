package ru.nanodev.nanoforge.engine;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PlaceholderUtilTest {

    @Mock
    private Player player;
    @Mock
    private World world;

    private final UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        // lenient() - потому что nullTextReturnsNull/nullPlayerLeavesTextUnchanged
        // вообще не трогают мок player, и строгий MockitoExtension иначе ругается
        // на "неиспользуемые" стабы (UnnecessaryStubbingException).
        lenient().when(player.getName()).thenReturn("Steve");
        lenient().when(player.getUniqueId()).thenReturn(uuid);
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(world.getName()).thenReturn("world_nether");
        lenient().when(player.getLocation()).thenReturn(new Location(world, 10, 64, -20));
        lenient().when(player.getHealth()).thenReturn(15.0);
        lenient().when(player.getLevel()).thenReturn(30);
    }

    @Test
    void nullTextReturnsNull() {
        assertThat(PlaceholderUtil.apply(null, player)).isNull();
    }

    @Test
    void nullPlayerLeavesTextUnchanged() {
        String text = "Привет, {player}!";
        assertThat(PlaceholderUtil.apply(text, null)).isEqualTo(text);
    }

    @Test
    void substitutesPlayerName() {
        assertThat(PlaceholderUtil.apply("Привет, {player}!", player)).isEqualTo("Привет, Steve!");
    }

    @Test
    void substitutesUuid() {
        assertThat(PlaceholderUtil.apply("{uuid}", player)).isEqualTo(uuid.toString());
    }

    @Test
    void substitutesWorld() {
        assertThat(PlaceholderUtil.apply("Мир: {world}", player)).isEqualTo("Мир: world_nether");
    }

    @Test
    void substitutesCoordinates() {
        assertThat(PlaceholderUtil.apply("{x} {y} {z}", player)).isEqualTo("10 64 -20");
    }

    @Test
    void substitutesHealthAsInt() {
        assertThat(PlaceholderUtil.apply("{health}", player)).isEqualTo("15");
    }

    @Test
    void substitutesLevel() {
        assertThat(PlaceholderUtil.apply("{level}", player)).isEqualTo("30");
    }

    @Test
    void substitutesEverythingAtOnce() {
        String result = PlaceholderUtil.apply(
                "{player} в {world} на {x} {y} {z}, хп {health}, левел {level}", player);
        assertThat(result).isEqualTo("Steve в world_nether на 10 64 -20, хп 15, левел 30");
    }

    @Test
    void textWithoutPlaceholdersIsUnchanged() {
        String text = "Просто обычный текст без плейсхолдеров";
        assertThat(PlaceholderUtil.apply(text, player)).isEqualTo(text);
    }

    @Test
    void substitutesArgsJoined() {
        assertThat(PlaceholderUtil.apply("аргументы: {args}", player, new String[]{"foo", "bar", "baz"}))
                .isEqualTo("аргументы: foo bar baz");
    }

    @Test
    void substitutesIndividualArgs() {
        assertThat(PlaceholderUtil.apply("{arg1}-{arg2}", player, new String[]{"a", "b"})).isEqualTo("a-b");
    }

    @Test
    void missingArgPlaceholderStaysLiteral() {
        // {arg3} не существует в массиве из 2 элементов - должен остаться как есть, не падать
        assertThat(PlaceholderUtil.apply("{arg1}-{arg3}", player, new String[]{"a", "b"})).isEqualTo("a-{arg3}");
    }

    @Test
    void nullArgsArrayLeavesArgPlaceholdersUntouched() {
        String text = "{args} {arg1}";
        assertThat(PlaceholderUtil.apply(text, player, null)).isEqualTo(text);
    }
}

// by t.me/NanoDev_mc
