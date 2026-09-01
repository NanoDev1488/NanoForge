package ru.nanodev.nanoforge.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class ActionLineParserTest {

    @Test
    void parsesMessage() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("message &aПривет, {player}!", err);

        assertThat(action).isNotNull();
        assertThat(action.get("type")).isEqualTo("message");
        assertThat(action.get("text")).isEqualTo("&aПривет, {player}!");
    }

    @Test
    void parsesBroadcast() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("broadcast Всем привет", err);

        assertThat(action).isNotNull();
        assertThat(action.get("type")).isEqualTo("broadcast");
        assertThat(action.get("text")).isEqualTo("Всем привет");
    }

    @Test
    void parsesConsole() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("console heal {player}", err);

        assertThat(action.get("type")).isEqualTo("console");
        assertThat(action.get("command")).isEqualTo("heal {player}");
    }

    @Test
    void parsesCallWithArgs() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("call EssentialsX getVersion arg1 arg2", err);

        assertThat(action.get("type")).isEqualTo("call");
        assertThat(action.get("plugin")).isEqualTo("EssentialsX");
        assertThat(action.get("method")).isEqualTo("getVersion");
        assertThat((List<String>) action.get("args")).containsExactly("arg1", "arg2");
    }

    @Test
    void parsesCallWithoutArgs() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("call EssentialsX getVersion", err);

        assertThat(action.get("type")).isEqualTo("call");
        assertThat((List<?>) action.get("args")).isEmpty();
    }

    @Test
    void callWithoutMethodFails() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("call EssentialsX", err);

        assertThat(action).isNull();
        assertThat(err[0]).contains("call требует");
    }

    @Test
    void parsesOpenMenuWithoutAddon() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("openmenu main", err);

        assertThat(action.get("type")).isEqualTo("openmenu");
        assertThat(action.get("menu")).isEqualTo("main");
        assertThat(action).doesNotContainKey("addon");
    }

    @Test
    void parsesOpenMenuWithAddon() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("openmenu sub OtherAddon", err);

        assertThat(action.get("menu")).isEqualTo("sub");
        assertThat(action.get("addon")).isEqualTo("OtherAddon");
    }

    @Test
    void parsesCloseMenu() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("closemenu", err);

        assertThat(action.get("type")).isEqualTo("closemenu");
    }

    @Test
    void parsesSetvarPlayerScopeByDefault() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("setvar rank vip", err);

        assertThat(action.get("type")).isEqualTo("setvar");
        assertThat(action.get("key")).isEqualTo("rank");
        assertThat(action.get("value")).isEqualTo("vip");
        assertThat(action).doesNotContainKey("scope");
    }

    @Test
    void parsesSetvarGlobalScope() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("setvar event started global", err);

        assertThat(action.get("scope")).isEqualTo("global");
    }

    @Test
    void parsesAddvar() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("addvar coins 10", err);

        assertThat(action.get("type")).isEqualTo("addvar");
        assertThat(action.get("key")).isEqualTo("coins");
        assertThat(action.get("amount")).isEqualTo("10");
    }

    @Test
    void parsesEcoGiveAndTake() {
        String[] err = new String[1];

        Map<String, Object> give = ActionLineParser.parse("eco_give 50", err);
        assertThat(give.get("type")).isEqualTo("eco_give");
        assertThat(give.get("amount")).isEqualTo("50");

        Map<String, Object> take = ActionLineParser.parse("eco_take 20", err);
        assertThat(take.get("type")).isEqualTo("eco_take");
        assertThat(take.get("amount")).isEqualTo("20");
    }

    @Test
    void emptyLineFails() {
        String[] err = new String[1];
        assertThat(ActionLineParser.parse("", err)).isNull();
        assertThat(err[0]).isNotBlank();
    }

    @Test
    void nullLineFails() {
        String[] err = new String[1];
        assertThat(ActionLineParser.parse(null, err)).isNull();
    }

    @Test
    void unknownTypeFails() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("teleport spawn", err);

        assertThat(action).isNull();
        assertThat(err[0]).contains("Неизвестный тип действия");
    }

    @Test
    void isCaseInsensitiveForActionType() {
        String[] err = new String[1];
        Map<String, Object> action = ActionLineParser.parse("MESSAGE Привет", err);

        assertThat(action).isNotNull();
        assertThat(action.get("type")).isEqualTo("message");
    }
}

// by t.me/NanoDev_mc
