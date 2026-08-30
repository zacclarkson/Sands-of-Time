package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

import com.clarkson.sot.dungeon.VaultColor;
// Import the ItemManager (adjust package if needed)
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.Material;
// Removed NamespacedKey, PDC, PDT, ItemMeta imports
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * The wall of coloured blocks that seals a vault off until that vault is opened.
 *
 * <p>Sized by the segment template's {@code VAULT_DOOR} marker ({@code Segment.getVaultDoorBound()})
 * and coloured by the vault that segment contains. It has <b>no keyhole and no key of its own</b>:
 * only one key of each colour exists per dungeon and {@code VaultManager} consumes it at the vault
 * marker, so a second keyhole here could never be opened. {@code VaultManager} calls
 * {@code DoorManager.openVaultDoors} when it marks the matching vault open -- the marker click stays
 * VaultManager's, this wall is DoorManager's.
 *
 * <p>{@link #isCorrectKey} is therefore never consulted at runtime. It survives because it is the
 * colour-matching contract pinned by {@code KeyItemTaggingTest}.
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
     * Opens the vault door: its blocks sink into the floor like any other door, plus the two vault
     * fanfare sounds.
     *
     * <p>This used to set a single block to GLASS instead of animating -- a leftover from when a
     * "vault door" meant the vault marker block itself. {@code VaultManager} owns the marker and the
     * rewards behind it; this class is only the wall.
     */
    @Override
    public boolean open() {
        if (!super.open()) return false;

        World world = lockLocation.getWorld();
        if (world != null) {
            world.playSound(lockLocation, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 1.2f);
            world.playSound(lockLocation, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        plugin.getLogger().info("Opened " + vaultColor + " vault door " + id + " at " + lockLocation.toVector());
        return true;
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
