package ru.nanodev.nanoforge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberUtilTest {

    @Test
    void toDoubleParsesValidNumericString() {
        assertThat(NumberUtil.toDouble("3.5", 0)).isEqualTo(3.5);
    }

    @Test
    void toDoubleParsesIntegerObject() {
        assertThat(NumberUtil.toDouble(42, 0)).isEqualTo(42.0);
    }

    @Test
    void toDoubleReturnsDefaultForNull() {
        assertThat(NumberUtil.toDouble(null, 7)).isEqualTo(7);
    }

    @Test
    void toDoubleReturnsDefaultForGarbage() {
        assertThat(NumberUtil.toDouble("не число", 9)).isEqualTo(9);
    }

    @Test
    void toIntTruncatesFractionalPart() {
        assertThat(NumberUtil.toInt("3.9", 0)).isEqualTo(3);
    }

    @Test
    void toIntReturnsDefaultForNull() {
        assertThat(NumberUtil.toInt(null, 5)).isEqualTo(5);
    }
}

// by t.me/NanoDev_mc
