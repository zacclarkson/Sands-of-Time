package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

// Import the central keys class
import com.clarkson.sot.utils.SoTKeys;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a standard door connecting dungeon segments.
 * Requires a key with the type "RUSTY".
 * Assumes the opening/closing animation is handled by the abstract Door class.
 */
public class SegmentDoor extends Door {

    // Material the door is made of when closed
    private final Material doorMaterial;

    // No need for local key definitions anymore

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

        // Key initialization is now done centrally via SoTKeys.initializeKeys() in onEnable
    }

    /**
     * Checks if the provided ItemStack is a Rusty Key.
     * It looks for a specific Persistent Data Container tag defined in SoTKeys.
     *
     * @param keyStack The ItemStack to check.
     * @return true if it's a valid Rusty Key, false otherwise.
     */
    @Override
    public boolean isCorrectKey(@Nullable ItemStack keyStack) {
        if (keyStack == null || keyStack.getType() == Material.AIR || !keyStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = keyStack.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Use the static key and value from SoTKeys
        if (SoTKeys.KEY_TYPE == null) {
             // This check should ideally not be needed if initialized correctly in onEnable
             plugin.getLogger().severe("KEY_TYPE is null in SegmentDoor! Ensure SoTKeys are initialized.");
             return false;
        }
        String keyType = pdc.get(SoTKeys.KEY_TYPE, PersistentDataType.STRING);
        return SoTKeys.RUSTY_KEY_VALUE.equals(keyType);
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
