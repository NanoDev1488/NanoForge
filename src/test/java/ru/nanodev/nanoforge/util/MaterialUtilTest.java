package ru.nanodev.nanoforge.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialUtilTest {

    @Test
    void tryParseResolvesValidMaterialCaseInsensitively() {
        assertThat(MaterialUtil.tryParse("diamond")).isEqualTo(Material.DIAMOND);
        assertThat(MaterialUtil.tryParse("DIAMOND")).isEqualTo(Material.DIAMOND);
        assertThat(MaterialUtil.tryParse("DiAmOnD")).isEqualTo(Material.DIAMOND);
    }

    @Test
    void tryParseTrimsWhitespace() {
        assertThat(MaterialUtil.tryParse("  DIAMOND  ")).isEqualTo(Material.DIAMOND);
    }

    @Test
    void tryParseReturnsNullForUnknownMaterial() {
        assertThat(MaterialUtil.tryParse("NOT_A_REAL_MATERIAL")).isNull();
    }

    @Test
    void tryParseReturnsNullForNullOrEmpty() {
        assertThat(MaterialUtil.tryParse(null)).isNull();
        assertThat(MaterialUtil.tryParse("")).isNull();
        assertThat(MaterialUtil.tryParse("   ")).isNull();
    }
}

// by t.me/NanoDev_mc
