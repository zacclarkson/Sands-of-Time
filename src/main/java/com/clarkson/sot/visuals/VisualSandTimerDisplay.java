package com.clarkson.sot.visuals; // Example new package

import com.clarkson.sot.utils.SoTTeam; // Assuming SoTTeam is in .utils package

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Manages the physical sand block display for a single team's timer.
 * This class is responsible ONLY for the visual representation.
 * The actual game time is managed by SoTTeam.
 */
public class VisualSandTimerDisplay {

    private final Plugin plugin;
    private final SoTTeam team; // Link to the team whose time this represents
    private final Location bottomLocation; // The block *below* the lowest sand block
    private final Location topLocation;    // The highest possible sand block location
    private final int totalHeight;         // Max number of sand blocks possible

    private BukkitTask visualUpdateTask;
    private int lastKnownVisualBlocks = -1; // Track visual state to avoid unnecessary updates

    // How many seconds does one visual sand block represent?
    private static final int SECONDS_PER_BLOCK_VISUAL = 10;
    // How often to check if a visual block needs removing (in ticks)
    private static final long VISUAL_UPDATE_INTERVAL_TICKS = 20L; // Check every second

    public VisualSandTimerDisplay(Plugin plugin, SoTTeam team, Location bottomLocation, Location topLocation) {
        this.plugin = plugin;
        this.team = team;
        // Ensure bottom is actually below top
        this.bottomLocation = bottomLocation.getBlockY() < topLocation.getBlockY() ? bottomLocation.clone() : topLocation.clone();
        this.topLocation = topLocation.getBlockY() >= bottomLocation.getBlockY() ? topLocation.clone() : bottomLocation.clone();
        // Calculate height (inclusive)
        this.totalHeight = this.topLocation.getBlockY() - this.bottomLocation.getBlockY();

        if (this.totalHeight <= 0) {
            plugin.getLogger().log(Level.WARNING, "VisualSandTimerDisplay created with invalid height for team " + team.getTeamName());
        }
        plugin.getLogger().log(Level.INFO, "VisualSandTimerDisplay created for team " + team.getTeamName() + " Height: " + totalHeight);
    }

    /**
     * Starts the task that periodically updates the visual sand display.
     */
    public void startVisualUpdates() {
        if (visualUpdateTask != null && !visualUpdateTask.isCancelled()) {
            return; // Already running
        }
        if (totalHeight <= 0) return; // Don't run for invalid timers

        // Initialize visual state based on current time
        syncVisualState();

        // Schedule task to run periodically
        visualUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisuals, VISUAL_UPDATE_INTERVAL_TICKS, VISUAL_UPDATE_INTERVAL_TICKS);
        plugin.getLogger().log(Level.INFO, "Started visual timer updates for team " + team.getTeamName());
    }

    /**
     * Stops the visual update task.
     */
    public void stopVisualUpdates() {
        if (visualUpdateTask != null) {
            visualUpdateTask.cancel();
            visualUpdateTask = null;
            plugin.getLogger().log(Level.INFO, "Stopped visual timer updates for team " + team.getTeamName());
        }
    }

    /**
     * Periodically checks the actual remaining time and removes sand blocks visually if needed.
     */
    private void updateVisuals() {
        if (team == null || totalHeight <= 0) {
            stopVisualUpdates();
            return;
        }

        // Calculate how many blocks *should* be present based on actual time
        int targetBlocks = getTargetBlockCount(team.getRemainingSeconds());

        // Get current visual blocks (could be slightly out of sync)
        int currentBlocks = countCurrentSandBlocks();

        // Avoid redundant updates if the visual state hasn't changed
        if (targetBlocks == lastKnownVisualBlocks) {
            return;
        }

        // If the target is lower than current, remove blocks
        // We remove one block at a time per interval to make it look like it's draining
        if (targetBlocks < currentBlocks) {
            removeBottomSandBlock();
            // Optional: Recalculate currentBlocks if needed for immediate accuracy
        }

        // Update the last known visual state
        lastKnownVisualBlocks = targetBlocks;
        // If target is higher (e.g., time was added), syncVisualState handles adding blocks back.
        // We could potentially call syncVisualState here periodically too, but it's more intensive.
    }

    /**
    * Forces the visual sand blocks to match the team's current remaining seconds.
    * Useful after time is added or when starting the timer.
    */
    public void syncVisualState() {
        if (team == null || totalHeight <= 0) return;

        int targetBlocks = getTargetBlockCount(team.getRemainingSeconds());
        int currentBlocks = countCurrentSandBlocks(); // Count current physical blocks

        plugin.getLogger().log(Level.FINE, "Syncing visual state for " + team.getTeamName() + ". Target: " + targetBlocks + ", Current: " + currentBlocks);


        if (targetBlocks > currentBlocks) {
            // Need to ADD blocks
            addSandToTop(targetBlocks - currentBlocks);
        } else if (targetBlocks < currentBlocks) {
            // Need to REMOVE blocks
            removeSandFromBottom(currentBlocks - targetBlocks);
        }
        lastKnownVisualBlocks = targetBlocks; // Update tracker
    }


    /**
     * Calculates how many sand blocks should be visible based on remaining seconds.
     * @param remainingSeconds The actual remaining seconds from SoTTeam.
     * @return The number of sand blocks that should be present.
     */
    private int getTargetBlockCount(int remainingSeconds) {
        if (remainingSeconds <= 0) return 0;
        // Calculate blocks, ensuring it doesn't exceed the physical height
        int blocks = (int) Math.ceil((double) remainingSeconds / SECONDS_PER_BLOCK_VISUAL);
        return Math.min(blocks, this.totalHeight);
    }

    /**
     * Adds a specified number of sand blocks visually, starting from the lowest air block.
     * @param blocksToAdd Number of sand blocks to add.
     */
    private void addSandToTop(int blocksToAdd) {
        if (blocksToAdd <= 0 || totalHeight <= 0) return;
        World world = topLocation.getWorld();
        if (world == null) return;

        int blocksAdded = 0;
        // Start from the bottom potential block and go up
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY() && blocksAdded < blocksToAdd; y++) {
            Location currentLocation = new Location(world, topLocation.getBlockX(), y, topLocation.getBlockZ());
            Block block = currentLocation.getBlock();
            if (block.getType() == Material.AIR || block.isPassable()) { // Check if space is available
                block.setType(Material.SAND, false); // Set to sand, false = don't apply physics immediately
                blocksAdded++;
            }
        }
         if(blocksAdded > 0) plugin.getLogger().log(Level.FINE, "Visually added " + blocksAdded + " sand blocks for team " + team.getTeamName());
    }

    /**
     * Removes one sand block from the bottom of the visual stack.
     */
    private void removeBottomSandBlock() {
        removeSandFromBottom(1);
    }

    /**
     * Removes a specified number of sand blocks from the bottom of the visual stack.
     * @param blocksToRemove Number of blocks to remove.
     */
    private void removeSandFromBottom(int blocksToRemove) {
        if (blocksToRemove <= 0 || totalHeight <= 0) return;
        World world = bottomLocation.getWorld();
        if (world == null) return;

        int blocksRemoved = 0;
         // Start checking from the block above the bottom marker upwards
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY() && blocksRemoved < blocksToRemove; y++) {
            Location currentLocation = new Location(world, bottomLocation.getBlockX(), y, bottomLocation.getBlockZ());
            Block block = currentLocation.getBlock();
            if (block.getType() == Material.SAND) {
                block.setType(Material.AIR, false); // Set to air, no physics
                blocksRemoved++;
            } else {
                 // Stop if we hit air, means we reached the current top of the stack
                 break;
            }
        }
        if(blocksRemoved > 0) plugin.getLogger().log(Level.FINE, "Visually removed " + blocksRemoved + " sand blocks for team " + team.getTeamName());
    }


    /**
     * Counts the current number of physical sand blocks in the timer column.
     * @return The number of sand blocks found.
     */
    private int countCurrentSandBlocks() {
        if (totalHeight <= 0) return 0;
        World world = bottomLocation.getWorld();
        if (world == null) return 0;

        int count = 0;
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY(); y++) {
            Location currentLocation = new Location(world, bottomLocation.getBlockX(), y, bottomLocation.getBlockZ());
            if (currentLocation.getBlock().getType() == Material.SAND) {
                count++;
            }
        }
        return count;
    }
}