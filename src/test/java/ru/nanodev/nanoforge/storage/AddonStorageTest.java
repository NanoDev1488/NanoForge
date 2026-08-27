package ru.nanodev.nanoforge.storage;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AddonStorageTest {

    @Mock
    private Player playerA;
    @Mock
    private Player playerB;

    @TempDir
    File tempDir;

    private AddonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new AddonStorage(tempDir);
        lenient().when(playerA.getUniqueId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        lenient().when(playerB.getUniqueId()).thenReturn(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    }

    @Test
    void getVarReturnsDefaultWhenNotSet() {
        assertThat(storage.getVar(playerA, "unset", "default")).isEqualTo("default");
    }

    @Test
    void setAndGetVarRoundTrips() {
        storage.setVar(playerA, "rank", "vip");
        assertThat(storage.getVar(playerA, "rank", "member")).isEqualTo("vip");
    }

    @Test
    void variablesAreIsolatedPerPlayer() {
        storage.setVar(playerA, "rank", "vip");
        storage.setVar(playerB, "rank", "member");

        assertThat(storage.getVar(playerA, "rank", "?")).isEqualTo("vip");
        assertThat(storage.getVar(playerB, "rank", "?")).isEqualTo("member");
    }

    @Test
    void globalVarIsSharedRegardlessOfPlayer() {
        storage.setGlobalVar("event", "started");
        assertThat(storage.getGlobalVar("event", "?")).isEqualTo("started");
    }

    @Test
    void addVarAccumulatesFromZero() {
        double result1 = storage.addVar(playerA, "coins", 10);
        double result2 = storage.addVar(playerA, "coins", 5);

        assertThat(result1).isEqualTo(10);
        assertThat(result2).isEqualTo(15);
        assertThat(storage.getVarNumber(playerA, "coins", 0)).isEqualTo(15);
    }

    @Test
    void addGlobalVarAccumulates() {
        storage.addGlobalVar("totalKills", 3);
        storage.addGlobalVar("totalKills", 2);

        assertThat(storage.getGlobalVarNumber("totalKills", 0)).isEqualTo(5);
    }

    @Test
    void addVarSupportsNegativeAmounts() {
        storage.addVar(playerA, "coins", 100);
        storage.addVar(playerA, "coins", -30);

        assertThat(storage.getVarNumber(playerA, "coins", 0)).isEqualTo(70);
    }

    @Test
    void cooldownIsZeroWhenNeverUsed() {
        assertThat(storage.remainingCooldownSeconds(playerA, "heal", 30)).isEqualTo(0);
    }

    @Test
    void cooldownIsPositiveRightAfterMarking() {
        storage.markCooldown(playerA, "heal");
        long remaining = storage.remainingCooldownSeconds(playerA, "heal", 30);

        assertThat(remaining).isGreaterThan(0).isLessThanOrEqualTo(30);
    }

    @Test
    void cooldownIsIsolatedPerPlayer() {
        storage.markCooldown(playerA, "heal");

        assertThat(storage.remainingCooldownSeconds(playerA, "heal", 30)).isGreaterThan(0);
        assertThat(storage.remainingCooldownSeconds(playerB, "heal", 30)).isEqualTo(0);
    }

    @Test
    void cooldownIsIsolatedPerKey() {
        storage.markCooldown(playerA, "heal");

        assertThat(storage.remainingCooldownSeconds(playerA, "heal", 30)).isGreaterThan(0);
        assertThat(storage.remainingCooldownSeconds(playerA, "otherAction", 30)).isEqualTo(0);
    }

    @Test
    void storagePersistsAcrossNewInstanceOnSameFolder() {
        storage.setVar(playerA, "rank", "vip");

        // имитируем перезагрузку сервера - создаём НОВЫЙ AddonStorage над той же папкой
        AddonStorage reloaded = new AddonStorage(tempDir);
        assertThat(reloaded.getVar(playerA, "rank", "?")).isEqualTo("vip");
    }
}

// by t.me/NanoDev_mc
