package ru.nanodev.nanoforge.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * "Красивый" шрифт для сообщений плагина, которые видит игрок в чате -
 * обычные буквы (латиница QWERTY и кириллица ЙЦУКЕН) заменяются на
 * стилизованные unicode-глифы (мелкие капсы/IPA), визуально другие, но
 * узнаваемые. Цифры, пробелы, пунктуация и коды цвета (&a / §a) не трогаются.
 * Плейсхолдеры вида {player}/{world}/{result} тоже не трогаются целиком -
 * иначе PlaceholderUtil перестанет находить точную строку "{player}" после
 * того, как буквы внутри фигурных скобок будут стилизованы.
 *
 * Таблица построена из исходного файла со шрифтом через юникод-эскейпы
 * (формат u+XXXX) - специально так, чтобы не перепечатывать экзотические
 * глифы руками и не рисковать опечататься в символе.
 *
 * ВАЖНО: применяется только к тексту, который видит ИГРОК В ЧАТЕ (клиент
 * Minecraft отрисовывает юникод нормально). НЕ применяется к тому, что
 * пишется в консоль/лог сервера (plugin.getLogger()...) - там эти же
 * символы на части терминалов/шрифтов могут выглядеть нечитаемым мусором,
 * а логи должны оставаться обычным читаемым и grep-абельным текстом.
 */
public class FancyFont {

    private static final Map<Character, Character> MAP = new HashMap<>();

    static {
        MAP.put('й', '\u0439');
        MAP.put('ц', '\u0446');
        MAP.put('у', '\u0443');
        MAP.put('к', '\u1D0B');
        MAP.put('е', '\u1D07');
        MAP.put('н', '\u043D');
        MAP.put('г', '\u1D26');
        MAP.put('ш', '\u026F');
        MAP.put('щ', '\u0449');
        MAP.put('з', '\u0437');
        MAP.put('х', '\u0445');
        MAP.put('ф', '\u0278');
        MAP.put('ы', '\u044B');
        MAP.put('в', '\u0299');
        MAP.put('а', '\u1D00');
        MAP.put('п', '\u1D28');
        MAP.put('р', '\u1D29');
        MAP.put('о', '\u043E');
        MAP.put('л', '\u1D27');
        MAP.put('д', '\u0434');
        MAP.put('ж', '\u0436');
        MAP.put('э', '\u03F6');
        MAP.put('я', '\u044F');
        MAP.put('ч', '\u0447');
        MAP.put('с', '\u1D04');
        MAP.put('м', '\u028D');
        MAP.put('и', '\u0438');
        MAP.put('т', '\u1D1B');
        MAP.put('ь', '\u044C');
        MAP.put('б', '\u0431');
        MAP.put('ю', '\u044E');
        MAP.put('q', '\u01EB');
        MAP.put('w', '\u1D21');
        MAP.put('e', '\u1D07');
        MAP.put('r', '\u0280');
        MAP.put('t', '\u1D1B');
        MAP.put('y', '\u028F');
        MAP.put('u', '\u1D1C');
        MAP.put('i', '\u026A');
        MAP.put('o', '\u1D0F');
        MAP.put('p', '\u1D18');
        MAP.put('a', '\u1D00');
        MAP.put('s', '\u0073');
        MAP.put('d', '\u1D05');
        MAP.put('f', '\uA730');
        MAP.put('g', '\u0262');
        MAP.put('h', '\u029C');
        MAP.put('j', '\u1D0A');
        MAP.put('k', '\u1D0B');
        MAP.put('l', '\u029F');
        MAP.put('z', '\u1D22');
        MAP.put('x', '\u0078');
        MAP.put('c', '\u1D04');
        MAP.put('v', '\u1D20');
        MAP.put('b', '\u0299');
        MAP.put('n', '\u0274');
        MAP.put('m', '\u1D0D');
    }

    /** Преобразует текст в стилизованный шрифт. Цвета (&/§-коды), цифры, пробелы, пунктуация не трогаются. */
    public static String stylize(String text) {
        if (text == null) return null;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // не трогаем цветовой код целиком (& или § + следующий символ - например &a, §c)
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            // не трогаем плейсхолдеры вида {player}/{world}/{result} целиком - иначе
            // PlaceholderUtil перестанет находить точную строку "{player}" после стилизации букв внутри
            if (c == '{') {
                int close = text.indexOf('}', i);
                if (close > i) {
                    out.append(text, i, close + 1);
                    i = close;
                    continue;
                }
            }
            Character styled = MAP.get(Character.toLowerCase(c));
            out.append(styled != null ? styled : c);
        }
        return out.toString();
    }
}

// by t.me/NanoDev_mc
