package ru.nanodev.nanoforge.wizard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class WizardHolder implements InventoryHolder {

    public enum Stage { TYPE_CHOICE, PLUGIN_CHOICE }

    private final Stage stage;
    private final int page;
    private Inventory inventory;

    public WizardHolder(Stage stage, int page) {
        this.stage = stage;
        this.page = page;
    }

    public Stage getStage() {
        return stage;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

// by t.me/NanoDev_mc
