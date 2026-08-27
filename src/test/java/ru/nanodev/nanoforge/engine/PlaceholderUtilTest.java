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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceholderUtilTest {

    @Mock
    private Player player;
    @Mock
    private World world;

    private final UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        when(player.getName()).thenReturn("Steve");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world_nether");
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, -20));
        when(player.getHealth()).thenReturn(15.0);
        when(player.getLevel()).thenReturn(30);
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
}

// by t.me/NanoDev_mc
