package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.utils.SoTKeys; // Import the central keys class

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a Vault door/mechanism.
 * Requires a specific VaultColor key. Opening changes the vault marker block.
 * Does not use the standard open/close animation from the abstract Door class.
 */
public class VaultDoor extends Door {

    private final VaultColor vaultColor;

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
        // Call the abstract super constructor
        // Vault doors don't animate, so animationTickDelay doesn't matter much here
        super(plugin, teamId, bounds, lockLocation);
        this.vaultColor = Objects.requireNonNull(vaultColor, "VaultColor cannot be null");
        // Vault doors typically start closed/locked state
        this.isOpen = false;
    }

    /**
     * Checks if the provided ItemStack is the correct colored Vault Key.
     * Checks for "VAULT" type and matching VaultColor tag.
     *
     * @param keyStack The ItemStack to check.
     * @return true if it's the correct key, false otherwise.
     */
    @Override
    public boolean isCorrectKey(@Nullable ItemStack keyStack) {
        if (keyStack == null || keyStack.getType() == Material.AIR || !keyStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = keyStack.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check for correct key type ("VAULT")
        String keyType = pdc.get(SoTKeys.KEY_TYPE, PersistentDataType.STRING);
        if (!SoTKeys.VAULT_KEY_VALUE.equals(keyType)) {
            return false;
        }

        // Check for matching vault color
        String colorStr = pdc.get(SoTKeys.VAULT_COLOR, PersistentDataType.STRING);
        if (colorStr == null) {
            return false; // Vault key must have a color tag
        }

        try {
            VaultColor keyColor = VaultColor.valueOf(colorStr);
            return keyColor == this.vaultColor; // Check if key color matches this door's vault color
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Key item had invalid VaultColor string in PDC: " + colorStr);
            return false; // Invalid color string on key
        }
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
            default:    return Material.STONE; // Fallback, should not happen
        }
    }

    /**
     * Opens the Vault door. This typically involves changing the lock block
     * to Glass and triggering rewards, rather than an animation.
     * Overrides the base Door open method entirely.
     * Key checking and consumption should happen in DoorManager *before* calling this.
     *
     * @param player The player who opened the vault.
     * @return true if the vault was successfully opened, false if already open.
     */
    @Override
    public boolean open(@NotNull Player player) {
        if (this.isOpen) {
            plugin.getLogger().finer("VaultDoor " + id + " (" + vaultColor + ") already open.");
            return false; // Already open
        }
        // Vault doors don't have complex animations, so no need to check currentAnimationTask

        // Change the vault marker block (typically the lock location)
        try {
            Block lockBlock = lockLocation.getBlock();
            Material expectedMaterial = getClosedMaterial();
            // Optional: Verify the block is still the correct material before changing
            if (lockBlock.getType() != expectedMaterial) {
                 plugin.getLogger().warning("VaultDoor " + id + " (" + vaultColor + ") lock block at " + lockLocation.toVector() + " was not the expected material ("+ expectedMaterial + "), was " + lockBlock.getType() + ". Still opening.");
            }

            lockBlock.setType(Material.GLASS, true); // Change to glass, apply physics
            this.isOpen = true; // Set state

            // Play sound effect
            player.getWorld().playSound(lockLocation, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 1.2f); // Example sound
            player.getWorld().playSound(lockLocation, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.0f); // Example sound

            plugin.getLogger().info("Opened VaultDoor " + id + " (" + vaultColor + ") at " + lockLocation.toVector() + " by " + player.getName());

            // IMPORTANT: Reward logic should be triggered here or by the calling manager.
            // This could involve spawning items behind the door, giving score directly,
            // or firing a custom event like VaultOpenEvent.
            // Example: Bukkit.getPluginManager().callEvent(new VaultOpenEvent(player, teamId, vaultColor, lockLocation));
            // Example: gameManager.getScoreManager().giveVaultReward(player, vaultColor); // If ScoreManager handles it

            return true; // Successfully opened
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error opening VaultDoor " + id + " (" + vaultColor + ")", e);
            return false; // Failed to open
        }
    }

    /**
     * Vault doors cannot be closed once opened in the standard SoT game.
     *
     * @param player The player attempting the action (or null).
     * @return Always false.
     */
    @Override
    public boolean close(@Nullable Player player) {
        // Vaults generally don't close
        return false;
    }

    // Inherits other methods like getId, getTeamId, getBounds, getLockLocation, isOpen, setOpenState from abstract Door.
    // Note: setOpenState will just set the boolean flag; it won't change the block for VaultDoor unless overridden or modified in base class.
    // The base class animation methods (startOpeningAnimation, startClosingAnimation) are not used by this subclass due to overriding open().

}
