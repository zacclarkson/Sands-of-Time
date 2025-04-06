package com.clarkson.sot.entities;

import org.bukkit.Location;
import org.bukkit.entity.Entity; // Use base Entity or specific type like ItemDisplay
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Interface defining the contract for items found on the floor within a dungeon instance.
 * Implementations must provide methods to access item properties, context (like depth),
 * state, and handle pickup logic.
 */
public interface FloorItem {

    /**
     * @return The unique identifier for this specific floor item instance.
     * Used for tracking and removal.
     */
    UUID getUniqueId();

    /**
     * @return The absolute world location where the item exists or is visually represented.
     */
    Location getLocation();

    /**
     * @return The underlying ItemStack representing the type and amount of this item.
     * May return null or an empty stack for conceptual items (like a pure score pickup)
     * that don't grant a physical item.
     */
    ItemStack getItemStack(); // Or Optional<ItemStack>

    /**
     * @return The visual entity representing this item in the world (e.g., ItemDisplay, ArmorStand, Item).
     * May return null if there's no specific visual entity or it hasn't spawned yet.
     */
    Entity getVisualEntity(); // Consider specific type like ItemDisplay if always used

    /**
     * @return The dungeon depth level (e.g., segments from hub) where this item was spawned.
     * Crucial for mechanics like coin value scaling.
     */
    int getDepth();

    /**
     * @return The UUID of the team whose dungeon instance this item belongs to.
     * Necessary for scoping interactions in per-team dungeons.
     */
    UUID getTeamId();

    /**
     * @return The UUID of the specific PlacedSegment instance where this item originated.
     * Useful for context-specific logic or debugging.
     */
    UUID getSegmentInstanceId(); // Or potentially return PlacedSegment if feasible

    /**
     * @return True if the item has already been picked up and should no longer be interactable
     * or visible, false otherwise. Implementing classes manage this state.
     */
    boolean isPickedUp();

    /**
     * Defines the action to take when a player attempts to pick up this item.
     * Implementing classes will handle giving rewards (items, score), applying effects,
     * consuming the item, and updating the pickup state (calling logic similar to markAsPickedUp).
     *
     * @param player The player who triggered the pickup action.
     */
    void handlePickup(Player player);

    /**
     * Removes any visual representation of this item from the world (e.g., removes the ItemDisplay entity).
     * Implementing classes should call this when the item is picked up or otherwise needs to disappear.
     */
    void removeRepresentation();

    
    void spawnRepresentation( Location location); // Optional: If items can be spawned at different locations

}
