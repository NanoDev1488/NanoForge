package ru.nanodev.nanoforge.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FancyFontTest {

    @Test
    void nullTextReturnsNull() {
        assertThat(FancyFont.stylize(null)).isNull();
    }

    @Test
    void emptyTextStaysEmpty() {
        assertThat(FancyFont.stylize("")).isEmpty();
    }

    @Test
    void digitsSpacesAndPunctuationAreUnchanged() {
        String text = "123 !? .,:;-()";
        assertThat(FancyFont.stylize(text)).isEqualTo(text);
    }

    @Test
    void colorCodesArePreservedAsIs() {
        // цветовой код (& или §) + следующий символ никогда не трогается,
        // даже если этот следующий символ сам по себе есть в таблице (например &a)
        assertThat(FancyFont.stylize("&aПривет")).startsWith("&a");
        assertThat(FancyFont.stylize("\u00A7cОшибка")).startsWith("\u00A7c");
    }

    @Test
    void latinLettersAreStyled() {
        String result = FancyFont.stylize("hello");
        assertThat(result).isNotEqualTo("hello");
        assertThat(result).hasSize(5);
    }

    @Test
    void cyrillicLettersAreStyled() {
        String result = FancyFont.stylize("привет");
        assertThat(result).isNotEqualTo("привет");
        assertThat(result).hasSize(6);
    }

    @Test
    void unmappedCyrillicLettersPassThroughUnchanged() {
        // ё и ъ не входят в исходную таблицу шрифта - должны остаться как есть
        assertThat(FancyFont.stylize("ёъ")).isEqualTo("ёъ");
    }

    @Test
    void upperCaseIsFoldedToSameStyledGlyphAsLowerCase() {
        assertThat(FancyFont.stylize("HELLO")).isEqualTo(FancyFont.stylize("hello"));
        assertThat(FancyFont.stylize("ПРИВЕТ")).isEqualTo(FancyFont.stylize("привет"));
    }

    @Test
    void mixedTextKeepsNonLetterPartsIntact() {
        String result = FancyFont.stylize("&aAddon 'Test' enabled!");
        assertThat(result).startsWith("&a");
        assertThat(result).contains("'").contains("!");
    }

    @Test
    void placeholdersArePreservedForLaterSubstitution() {
        // критично: PlaceholderUtil ищет точную строку "{player}" - если бы буквы
        // внутри стилизовались, подстановка плейсхолдера сломалась бы после FancyFont
        String result = FancyFont.stylize("Привет, {player}! Ты в {world}.");
        assertThat(result).contains("{player}").contains("{world}");
    }

    @Test
    void unclosedBraceIsNotTreatedAsPlaceholder() {
        // без закрывающей } - это просто буква "п" в фигурных скобках, а не плейсхолдер
        String result = FancyFont.stylize("{привет");
        assertThat(result).startsWith("{");
    }
}

// by t.me/NanoDev_mc
