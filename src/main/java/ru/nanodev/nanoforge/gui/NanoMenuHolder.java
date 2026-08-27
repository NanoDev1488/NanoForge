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
