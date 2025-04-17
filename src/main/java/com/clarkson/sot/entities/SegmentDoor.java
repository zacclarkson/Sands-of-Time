package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

// Import the ItemManager (assuming path)
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.Material;
// Removed NamespacedKey, PDC, PDT imports as they are handled by ItemManager now
import org.bukkit.inventory.ItemStack;
// Removed ItemMeta import
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a standard door connecting dungeon segments.
 * Requires a RUSTY key type, checked via ItemManager.
 * Assumes the opening/closing animation is handled by the abstract Door class.
 */
public class SegmentDoor extends Door {

    // Material the door is made of when closed
    private final Material doorMaterial;

    // Key constants are now managed by ItemManager

    /**
     * Constructor for SegmentDoor.
     *
     * @param plugin Plugin instance for scheduling.
     * @param teamId Team this door belongs to.
     * @param bounds Area containing the door blocks.
     * @param lockLocation Location of the lock block.
     * @param doorMaterial The material the door blocks should be when closed.
     */
    public SegmentDoor(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds, @NotNull Location lockLocation, @NotNull Material doorMaterial) {
        // Call the abstract super constructor
        super(plugin, teamId, bounds, lockLocation);
        this.doorMaterial = Objects.requireNonNull(doorMaterial, "Door material cannot be null");

        // Key initialization is handled centrally (e.g., SoTKeys/ItemManager init in onEnable)
    }

    /**
     * Checks if the provided ItemStack is a Rusty Key by delegating to ItemManager.
     *
     * @param keyStack The ItemStack to check.
     * @return true if it's a valid Rusty Key, false otherwise.
     */
    @Override
    public boolean isCorrectKey(@Nullable ItemStack keyStack) {
        // Delegate the check to the central ItemManager
        // This assumes ItemManager has a static method like this:
        // public static boolean isRustyKey(ItemStack item) { ... checks PDC ... }
        return ItemManager.isRustyKey(keyStack);
    }

    /**
     * Gets the Material the door should be made of when closed.
     *
     * @return The Material for the closed door state.
     */
    @Override
    @NotNull
    protected Material getClosedMaterial() {
        return this.doorMaterial;
    }

    // open() and close() are inherited from abstract Door
}
