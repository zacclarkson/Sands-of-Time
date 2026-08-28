package com.clarkson.sot.dungeon;

import org.bukkit.Material;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum VaultColor {
    BLUE, RED, GREEN, GOLD; // Add others if needed

    /** Solid block material representing this vault color (matches VaultManager/VaultDoor). */
    public Material getConcreteMaterial() {
        switch (this) {
            case BLUE:  return Material.BLUE_CONCRETE;
            case RED:   return Material.RED_CONCRETE;
            case GREEN: return Material.LIME_CONCRETE;
            case GOLD:  return Material.GOLD_BLOCK;
            default:    return Material.STONE;
        }
    }

    /** Stained-glass material representing this vault color (used by builder frames). */
    public Material getGlassMaterial() {
        switch (this) {
            case BLUE:  return Material.BLUE_STAINED_GLASS;
            case RED:   return Material.RED_STAINED_GLASS;
            case GREEN: return Material.LIME_STAINED_GLASS;
            case GOLD:  return Material.YELLOW_STAINED_GLASS;
            default:    return Material.WHITE_STAINED_GLASS;
        }
    }

    /** Adventure text color for this vault color (labels, item names). */
    public TextColor getTextColor() {
        switch (this) {
            case BLUE:  return NamedTextColor.BLUE;
            case RED:   return NamedTextColor.RED;
            case GREEN: return NamedTextColor.GREEN;
            case GOLD:  return NamedTextColor.GOLD;
            default:    return NamedTextColor.WHITE;
        }
    }
}
