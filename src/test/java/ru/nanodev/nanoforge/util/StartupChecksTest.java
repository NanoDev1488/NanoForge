package ru.nanodev.nanoforge.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.nanodev.nanoforge.util.StartupChecks.VersionStatus.*;

class StartupChecksTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // граничные значения диапазона
            "1.16-R0.1-SNAPSHOT,          SUPPORTED",
            "1.16.0-R0.1-SNAPSHOT,        SUPPORTED",
            "1.21.11-R0.1-SNAPSHOT,       SUPPORTED",
            "1.20.4-R0.1-SNAPSHOT,        SUPPORTED",

            // ниже минимума
            "1.15.2-R0.1-SNAPSHOT,        BELOW_MIN",
            "1.12.2-R0.1-SNAPSHOT,        BELOW_MIN",
            "1.8.8-R0.1-SNAPSHOT,         BELOW_MIN",

            // выше максимума
            "1.21.12-R0.1-SNAPSHOT,       ABOVE_MAX",
            "1.22.0-R0.1-SNAPSHOT,        ABOVE_MAX",
            "1.30.0-R0.1-SNAPSHOT,        ABOVE_MAX",
            "2.0.0-R0.1-SNAPSHOT,         ABOVE_MAX",

            // нераспознаваемое
            "garbage,                     UNKNOWN",
            "'',                          UNKNOWN",
    })
    void classifiesVersionsCorrectly(String rawVersion, String expected) {
        StartupChecks.VersionStatus expectedStatus = StartupChecks.VersionStatus.valueOf(expected.trim());
        assertThat(StartupChecks.classify(rawVersion)).isEqualTo(expectedStatus);
    }

    @org.junit.jupiter.api.Test
    void nullVersionIsUnknown() {
        assertThat(StartupChecks.classify(null)).isEqualTo(UNKNOWN);
    }

    @org.junit.jupiter.api.Test
    void versionWithoutDashSuffixStillWorks() {
        // не все сборки следуют формату "X.Y.Z-R...SNAPSHOT" - голая версия тоже должна парситься
        assertThat(StartupChecks.classify("1.20.4")).isEqualTo(SUPPORTED);
    }
}

// by t.me/NanoDev_mc
