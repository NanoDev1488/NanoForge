package ru.nanodev.nanoforge.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDebuggerTest {

    @AfterEach
    void tearDown() {
        ActionDebugger.resetForTests();
    }

    @Test
    void isDisabledByDefault() {
        assertThat(ActionDebugger.isEnabled("SomeAddon")).isFalse();
    }

    @Test
    void toggleEnablesThenDisablesAgain() {
        assertThat(ActionDebugger.toggle("SomeAddon")).isTrue();
        assertThat(ActionDebugger.isEnabled("SomeAddon")).isTrue();

        assertThat(ActionDebugger.toggle("SomeAddon")).isFalse();
        assertThat(ActionDebugger.isEnabled("SomeAddon")).isFalse();
    }

    @Test
    void isCaseInsensitive() {
        ActionDebugger.toggle("SomeAddon");
        assertThat(ActionDebugger.isEnabled("someaddon")).isTrue();
        assertThat(ActionDebugger.isEnabled("SOMEADDON")).isTrue();
    }

    @Test
    void isEnabledReturnsFalseForNull() {
        assertThat(ActionDebugger.isEnabled(null)).isFalse();
    }

    @Test
    void addonsAreIndependent() {
        ActionDebugger.toggle("A");
        assertThat(ActionDebugger.isEnabled("A")).isTrue();
        assertThat(ActionDebugger.isEnabled("B")).isFalse();
    }
}

// by t.me/NanoDev_mc
