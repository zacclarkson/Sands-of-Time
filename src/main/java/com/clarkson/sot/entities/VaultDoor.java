package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

import com.clarkson.sot.dungeon.VaultColor;
// Import the ItemManager (adjust package if needed)
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.Material;
// Removed NamespacedKey, PDC, PDT, ItemMeta imports
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a Vault door/mechanism.
 * Requires a specific VaultColor key, checked via ItemManager.
 * Opening changes the vault marker block.
 * Does not use the standard open/close animation from the abstract Door class.
 */
public class VaultDoor extends Door {

    private final VaultColor vaultColor;

    // Key constants are now managed by ItemManager

    /**
     * Constructor for VaultDoor.
     *
     * @param plugin Plugin instance.
     * @param teamId Team this door belongs to.
     * @param bounds Area defining the vault marker block(s). Usually just 1x1x1.
     * @param lockLocation Location of the vault marker block (used as the lock).
     * @param vaultColor The color of this vault.
     */
    public VaultDoor(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds, @NotNull Location lockLocation, @NotNull VaultColor vaultColor) {
        super(plugin, teamId, bounds, lockLocation);
        this.vaultColor = Objects.requireNonNull(vaultColor, "VaultColor cannot be null");
        this.isOpen = false;
    }

    /**
     * Checks if the provided ItemStack is the correct colored Vault Key
     * by delegating checks to the ItemManager.
     *
     * @param keyStack The ItemStack to check.
     * @return true if it's the correct key, false otherwise.
     */
    @Override
    public boolean isCorrectKey(@Nullable ItemStack keyStack) {
        // 1. Check if it's any type of Vault Key using ItemManager
        if (!ItemManager.isVaultKey(keyStack)) {
            return false;
        }
        // 2. Get the color from the key using ItemManager
        VaultColor keyColor = ItemManager.getVaultKeyColor(keyStack);

        // 3. Check if the key's color matches this vault's color
        return keyColor == this.vaultColor;
    }

    /**
     * Gets the Material the vault marker should be when closed/locked.
     *
     * @return The Material for the closed vault state (e.g., colored concrete).
     */
    @Override
    @NotNull
    protected Material getClosedMaterial() {
        // Return material based on vault color
        switch (this.vaultColor) {
            case BLUE:  return Material.BLUE_CONCRETE;
            case RED:   return Material.RED_CONCRETE;
            case GREEN: return Material.LIME_CONCRETE; // Or GREEN_CONCRETE
            case GOLD:  return Material.GOLD_BLOCK;
            default:    return Material.STONE; // Fallback
        }
    }

    /**
     * Opens the Vault door. Changes the lock block to Glass.
     * Assumes key check and consumption happened before calling.
     * Overrides the base Door open method entirely.
     *
     * @param player The player who opened the vault.
     * @return true if the vault was successfully opened, false if already open.
     */
    @Override
    public boolean open(@NotNull Player player) {
        if (this.isOpen) {
            plugin.getLogger().finer("VaultDoor " + id + " (" + vaultColor + ") already open.");
            return false;
        }

        try {
            Block lockBlock = lockLocation.getBlock();
            Material expectedMaterial = getClosedMaterial();
            if (lockBlock.getType() != expectedMaterial) {
                 plugin.getLogger().warning("VaultDoor " + id + " (" + vaultColor + ") lock block at " + lockLocation.toVector() + " was not the expected material ("+ expectedMaterial + "), was " + lockBlock.getType() + ". Still opening.");
            }

            lockBlock.setType(Material.GLASS, true); // Change to glass
            this.isOpen = true; // Set state

            player.getWorld().playSound(lockLocation, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 1.2f);
            player.getWorld().playSound(lockLocation, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.0f);

            plugin.getLogger().info("Opened VaultDoor " + id + " (" + vaultColor + ") at " + lockLocation.toVector() + " by " + player.getName());

            // TODO: Trigger reward logic (call ScoreManager, spawn items, fire event, etc.)

            return true;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error opening VaultDoor " + id + " (" + vaultColor + ")", e);
            return false;
        }
    }

    /**
     * Vault doors cannot be closed once opened.
     * @return Always false.
     */
    @Override
    public boolean close(@Nullable Player player) {
        return false;
    }

    // Inherits other methods from abstract Door.
}
