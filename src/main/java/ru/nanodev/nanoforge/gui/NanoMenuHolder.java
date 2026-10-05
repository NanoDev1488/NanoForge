package ru.nanodev.nanoforge.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Помечает Inventory принадлежностью к конкретному меню конкретного аддона,
 * чтобы глобальный InventoryClickEvent-листенер знал, какие actions запускать.
 */
public class NanoMenuHolder implements InventoryHolder {

    private final String addonName;
    private final String menuKey;
    private Inventory inventory;
    private boolean editMode = false;
    private int currentPage = 0;
    // слот -> "значение" элемента списка (имя игрока и т.п.), которым сгенерирован
    // этот конкретный слот через menus.<key>.list - для передачи как {value} в actions
    private final java.util.Map<Integer, String> generatedValues = new java.util.HashMap<>();

    public NanoMenuHolder(String addonName, String menuKey) {
        this.addonName = addonName;
        this.menuKey = menuKey;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, page);
    }

    public void putGeneratedValue(int slot, String value) {
        generatedValues.put(slot, value);
    }

    /** null, если этот слот - обычный статический пункт, а не сгенерированный из menus.*.list. */
    public String getGeneratedValue(int slot) {
        return generatedValues.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String getAddonName() {
        return addonName;
    }

    public String getMenuKey() {
        return menuKey;
    }
}

// by t.me/NanoDev_mc
