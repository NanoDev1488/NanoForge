package ru.nanodev.nanoforge.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PluginPresenceCacheTest {

    private MockedStatic<Bukkit> bukkitMock;

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) bukkitMock.close();
    }

    private PluginManager mockPluginManager() {
        PluginManager pm = mock(PluginManager.class);
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);
        return pm;
    }

    @Test
    void isPresentTrueWhenPluginFound() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(mock(Plugin.class));

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", "не найден", Logger.getLogger("test"));

        assertThat(cache.isPresent()).isTrue();
    }

    @Test
    void isPresentFalseWhenPluginMissing() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(null);

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", "не найден", Logger.getLogger("test"));

        assertThat(cache.isPresent()).isFalse();
    }

    @Test
    void logsInfoMessageOnlyOnceWhenMissing() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(null);
        Logger logger = mock(Logger.class);

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", "не найден - блаблабла", logger);

        cache.isPresent();
        cache.isPresent();
        cache.isPresent();

        verify(logger, times(1)).info("не найден - блаблабла");
    }

    @Test
    void resultIsCachedAndPluginManagerQueriedOnlyOnce() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(mock(Plugin.class));

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", null, Logger.getLogger("test"));
        cache.isPresent();
        cache.isPresent();
        cache.isPresent();

        verify(pm, times(1)).getPlugin("SomePlugin");
    }

    @Test
    void resetForTestsForcesRecheck() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(null);

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", null, Logger.getLogger("test"));
        assertThat(cache.isPresent()).isFalse();

        when(pm.getPlugin("SomePlugin")).thenReturn(mock(Plugin.class));
        cache.resetForTests();

        assertThat(cache.isPresent()).isTrue();
    }

    @Test
    void doesNotThrowWhenBukkitAccessFails() {
        try (MockedStatic<Bukkit> failing = mockStatic(Bukkit.class)) {
            failing.when(Bukkit::getPluginManager).thenThrow(new IllegalStateException("server not ready"));

            PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", null, Logger.getLogger("test"));

            assertThat(cache.isPresent()).isFalse();
        }
    }

    @Test
    void nullMessageMeansNoLoggingAtAll() {
        PluginManager pm = mockPluginManager();
        when(pm.getPlugin("SomePlugin")).thenReturn(null);
        Logger logger = mock(Logger.class);

        PluginPresenceCache cache = new PluginPresenceCache("SomePlugin", null, logger);
        cache.isPresent();

        verifyNoInteractions(logger);
    }
}

// by t.me/NanoDev_mc
