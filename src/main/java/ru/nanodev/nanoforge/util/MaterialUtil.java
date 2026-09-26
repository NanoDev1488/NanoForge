package ru.nanodev.nanoforge.util;

import org.bukkit.Material;

/**
 * До 1.5.0 {@code Material.valueOf(name.toUpperCase())} в try/catch был
 * независимо написан в трёх местах (MenuManager.buildItem, ActionRunner
 * give_item, AddonValidator.checkMaterial) - реализация тривиальна, но само
 * наличие трёх копий означало три места, которые пришлось бы синхронно
 * поправить при любом будущем изменении (например, если понадобится алиас
 * для устаревших имён материалов). Поведение при ошибке у каждого вызывающего
 * разное (тихий fallback на STONE / лог + отмена action / текст в отчёте
 * валидатора), поэтому сюда вынесен только сам парсинг - что делать при
 * null решает каждый вызывающий сам.
 */
public final class MaterialUtil {

    private MaterialUtil() {
    }

    /** null, если materialName == null, пустая строка или не существует такого материала. */
    public static Material tryParse(String materialName) {
        if (materialName == null || materialName.trim().isEmpty()) return null;
        try {
            return Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

// by t.me/NanoDev_mc
