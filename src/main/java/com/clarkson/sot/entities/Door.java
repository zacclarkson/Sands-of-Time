package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin; // Needed for scheduling
import org.bukkit.scheduler.BukkitTask; // To manage animations
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.Objects;

/**
 * Represents a generic animated door within a dungeon instance.
 * Handles common state and provides abstract methods for key checks
 * and potentially concrete methods for shared open/close animations.
 */
public abstract class Door {

    // --- Shared Fields ---
    protected final Plugin plugin; // For scheduling animations
    protected final UUID id; // Unique ID for this door instance
    protected final UUID teamId;
    protected final Area bounds; // Defines the blocks making up the door structure
    protected final Location lockLocation; // Block to interact with
    protected boolean isOpen;
    protected BukkitTask currentAnimationTask; // To prevent overlapping animations

    /**
     * Constructor for the base Door class.
     *
     * @param plugin Plugin instance for scheduling.
     * @param teamId Team this door belongs to.
     * @param bounds Area containing the door blocks.
     * @param lockLocation Location of the lock block.
     */
    protected Door(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds, @NotNull Location lockLocation) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "Bounds cannot be null");
        this.lockLocation = Objects.requireNonNull(lockLocation, "Lock location cannot be null");

        // *** Initialize the final UUID field ***
        this.id = UUID.randomUUID(); // Assign a unique ID

        this.isOpen = false; // Doors start closed
        this.currentAnimationTask = null;
    }

    // --- Common Getters ---

    @NotNull public UUID getId() { return id; }
    @NotNull public UUID getTeamId() { return teamId; }
    @NotNull public Area getBounds() { return bounds; }
    @NotNull public Location getLockLocation() { return lockLocation; }
    public boolean isOpen() { return isOpen; }

    // --- Abstract Methods (Must be implemented by subclasses) ---

    /**
     * Checks if the provided ItemStack is the correct key for THIS specific door type.
     * Implementation differs for SegmentDoor (Rusty Key) vs VaultDoor (Colored Key).
     * @param keyStack The ItemStack to check.
     * @return true if it's the correct key, false otherwise.
     */
    public abstract boolean isCorrectKey(@Nullable ItemStack keyStack);

    /**
     * Gets the Material the door should be made of when closed.
     * Might differ between door types.
     * @return The Material for the closed door state.
     */
    @NotNull
    protected abstract Material getClosedMaterial();


    // --- Core Action Methods (Could be final if animation is always the same) ---

    /**
     * Initiates the process to open the door if it's closed and not already animating.
     * Key checking should happen *before* calling this (e.g., in DoorManager).
     * Calls the animation method.
     *
     * @param player The player initiating the action.
     * @return true if the opening animation started, false if already open or animating.
     */
    public boolean open(@NotNull Player player) {
        // Implementation omitted (Would check isOpen, check currentAnimationTask, then call startOpeningAnimation)
        throw new UnsupportedOperationException("open implementation not provided.");
    }

    /**
     * Initiates the process to close the door if it's open and not already animating.
     * VaultDoor might override this to always return false.
     *
     * @param player The player initiating the action (or null).
     * @return true if the closing animation started, false if already closed, animating, or not supported.
     */
    public boolean close(@Nullable Player player) {
         // Implementation omitted (Would check isOpen, check currentAnimationTask, then call startClosingAnimation)
        throw new UnsupportedOperationException("close implementation not provided.");
    }

    // --- Animation Logic (Protected - Called by open/close) ---

    /**
     * Starts the visual animation sequence for opening the door.
     * (e.g., blocks moving down iteratively). Sets isOpen = true on completion.
     * Implementation involves BukkitRunnable.
     * @param player Player who triggered the opening.
     */
    protected void startOpeningAnimation(@NotNull Player player) {
        // Implementation omitted (BukkitRunnable logic to change blocks over time)
        throw new UnsupportedOperationException("startOpeningAnimation implementation not provided.");
    }

    /**
     * Starts the visual animation sequence for closing the door.
     * (e.g., blocks moving up iteratively). Sets isOpen = false on completion.
     * Implementation involves BukkitRunnable.
     * @param player Player who triggered the closing (or null).
     */
    protected void startClosingAnimation(@Nullable Player player) {
        // Implementation omitted (BukkitRunnable logic to change blocks over time)
        throw new UnsupportedOperationException("startClosingAnimation implementation not provided.");
    }


    // --- State Management ---

    /**
     * Forcefully sets the open state without animation.
     * Used for initialization or resetting state.
     * @param isOpen The desired state.
     */
    public void setOpenState(boolean isOpen) {
        // Implementation omitted (Sets the isOpen field)
         throw new UnsupportedOperationException("setOpenState implementation not provided.");
    }

    /**
     * Cancels any ongoing opening/closing animation.
     */
    protected void cancelAnimation() {
        // Implementation omitted (Checks currentAnimationTask and cancels if not null/cancelled)
        throw new UnsupportedOperationException("cancelAnimation implementation not provided.");
    }

}
