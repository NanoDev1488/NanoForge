package ru.nanodev.nanoforge.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nanodev.nanoforge.model.Addon;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddonValidatorTest {

    @TempDir
    File tempDir;

    private Addon addonFrom(YamlConfiguration yaml) {
        return new Addon(new File(tempDir, "addon.yml"), yaml);
    }

    @Test
    void reportsOkForMinimalValidAddon() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "Minimal");
        yaml.set("type", "addon");
        yaml.set("target", "SomePlugin");
        yaml.set("commands.hello.actions", List.of(msgAction("hi")));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).startsWith("OK");
    }

    @Test
    void flagsMissingTargetForAddonType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "NoTarget");
        yaml.set("type", "addon");
        yaml.set("commands.hello.actions", List.of(msgAction("hi")));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("требует поле 'target'"));
    }

    @Test
    void flagsUnknownActionType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "Typo");
        yaml.set("type", "new");
        Map<String, Object> typo = new LinkedHashMap<>();
        typo.put("type", "mesage"); // опечатка
        typo.put("text", "hi");
        yaml.set("commands.hello.actions", List.of(typo));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("неизвестный type 'mesage'"));
    }

    @Test
    void flagsOpenmenuReferencingMissingMenu() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "BadMenuRef");
        yaml.set("type", "new");
        Map<String, Object> openmenu = new LinkedHashMap<>();
        openmenu.put("type", "openmenu");
        openmenu.put("menu", "does_not_exist");
        yaml.set("commands.hello.actions", List.of(openmenu));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("несуществующее меню 'does_not_exist'"));
    }

    @Test
    void doesNotFlagOpenmenuTargetingAnotherAddon() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "CrossAddon");
        yaml.set("type", "new");
        Map<String, Object> openmenu = new LinkedHashMap<>();
        openmenu.put("type", "openmenu");
        openmenu.put("menu", "main");
        openmenu.put("addon", "OtherAddon"); // ссылка на чужой аддон - не проверяем его меню статически
        yaml.set("commands.hello.actions", List.of(openmenu));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).noneMatch(i -> i.contains("несуществующее меню"));
    }

    @Test
    void flagsUnknownMaterialInMenuItem() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "BadMaterial");
        yaml.set("type", "new");
        yaml.set("menus.main.rows", 1);
        yaml.set("menus.main.items.0.material", "NOT_A_REAL_MATERIAL");

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("неизвестный материал 'NOT_A_REAL_MATERIAL'"));
    }

    @Test
    void flagsSlotOutOfRangeForMenuRows() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "SlotOverflow");
        yaml.set("type", "new");
        yaml.set("menus.main.rows", 1); // слоты 0-8
        yaml.set("menus.main.items.20.material", "STONE");

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("вне диапазона меню"));
    }

    @Test
    void doesNotStaticallyCheckMaterialsWithPlaceholders() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "Placeholder");
        yaml.set("type", "new");
        Map<String, Object> give = new LinkedHashMap<>();
        give.put("type", "give_item");
        give.put("material", "{arg1}");
        yaml.set("commands.give.actions", List.of(give));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).noneMatch(i -> i.contains("неизвестный материал"));
    }

    @Test
    void flagsUnresolvableEventClass() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "BadEvent");
        yaml.set("type", "new");
        yaml.set("events.TotallyMadeUpEvent.actions", List.of(msgAction("hi")));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("класс события не найден"));
    }

    @Test
    void flagsDelayActionWithoutNestedActions() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "BadDelay");
        yaml.set("type", "new");
        Map<String, Object> delay = new LinkedHashMap<>();
        delay.put("type", "delay");
        delay.put("ticks", 20);
        yaml.set("commands.hello.actions", List.of(delay));

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("delay требует вложенный список"));
    }

    @Test
    void warnsWhenAddonHasNothingAtAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "Empty");
        yaml.set("type", "new");

        List<String> issues = AddonValidator.validate(addonFrom(yaml));

        assertThat(issues).anyMatch(i -> i.contains("ничего не делает"));
    }

    private static Map<String, Object> msgAction(String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "message");
        m.put("text", text);
        return m;
    }
}

// by t.me/NanoDev_mc
