package com.clarkson.sot.timer; // Or com.clarkson.sot.visuals based on original

import com.clarkson.sot.utils.SoTTeam; // Assuming SoTTeam provides getRemainingSeconds()

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Manages the physical sand block display for a single team's timer.
 * This class is responsible ONLY for the visual representation.
 * The actual game time is obtained from the associated SoTTeam object.
 * (Note: Dependency might change to TeamTimer if SoTTeam no longer holds time directly).
 */
public class VisualSandTimerDisplay {

    private final Plugin plugin;
    // TODO: Consider changing dependency from SoTTeam to TeamTimer or Supplier<Integer>
    // if SoTTeam no longer directly holds remainingSeconds after refactoring.
    private final SoTTeam team; // Source of the remaining time data
    private final Location bottomLocation; // Block *below* the lowest sand block
    private final Location topLocation;    // Highest possible sand block location
    private final int totalHeight;         // Max number of sand blocks possible (topY - bottomY)

    private BukkitTask visualUpdateTask; // The Bukkit scheduler task for updates
    private int lastKnownVisualBlocks = -1; // Tracks the last calculated target block count

    // Configuration constants
    private static final int SECONDS_PER_BLOCK_VISUAL = 10; // How many seconds each sand block represents
    private static final long VISUAL_UPDATE_INTERVAL_TICKS = 20L; // How often to check (20 ticks = 1 second)

    /**
     * Constructor for VisualSandTimerDisplay.
     *
     * @param plugin         The main plugin instance.
     * @param team           The SoTTeam whose time this display represents.
     * @param bottomLocation The Location of the block *directly below* the sand column base.
     * @param topLocation    The Location of the highest possible sand block in the column.
     */
    public VisualSandTimerDisplay(Plugin plugin, SoTTeam team, Location bottomLocation, Location topLocation) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.team = Objects.requireNonNull(team, "SoTTeam cannot be null");

        // Validate locations
        Objects.requireNonNull(bottomLocation, "Bottom location cannot be null");
        Objects.requireNonNull(topLocation, "Top location cannot be null");
        if (!Objects.equals(bottomLocation.getWorld(), topLocation.getWorld())) {
            throw new IllegalArgumentException("Bottom and Top locations must be in the same world!");
        }
        if (bottomLocation.getBlockY() >= topLocation.getBlockY()) {
             plugin.getLogger().warning("VisualSandTimerDisplay created with bottom location Y not below top location Y for team " + team.getTeamName());
             // Assign defensively, but height will be <= 0
             this.bottomLocation = topLocation.clone();
             this.topLocation = bottomLocation.clone();
        } else {
             this.bottomLocation = bottomLocation.clone();
             this.topLocation = topLocation.clone();
        }
        if (bottomLocation.getBlockX() != topLocation.getBlockX() || bottomLocation.getBlockZ() != topLocation.getBlockZ()) {
            throw new IllegalArgumentException("Top location must be directly above the bottom location (same X and Z coordinates).");
        }

        // Calculate total possible height (number of blocks in the column)
        this.totalHeight = this.topLocation.getBlockY() - this.bottomLocation.getBlockY();

        if (this.totalHeight <= 0) {
            plugin.getLogger().log(Level.SEVERE, "VisualSandTimerDisplay created with invalid height (<= 0) for team " + team.getTeamName() + ". Display will be disabled.");
        } else {
             plugin.getLogger().log(Level.INFO, "VisualSandTimerDisplay created for team " + team.getTeamName() + ". Height: " + totalHeight + " blocks.");
        }
        this.lastKnownVisualBlocks = -1; // Initialize tracking
    }

    /**
     * Starts the scheduled task that periodically updates the visual sand display
     * by removing blocks as time decreases. Also performs an initial sync.
     */
    public void startVisualUpdates() {
        // Don't start if already running or if height is invalid
        if (visualUpdateTask != null && !visualUpdateTask.isCancelled()) {
            plugin.getLogger().fine("Visual update task already running for team " + team.getTeamName());
            return;
        }
        if (totalHeight <= 0) {
            plugin.getLogger().warning("Cannot start visual updates for team " + team.getTeamName() + ": Invalid height.");
            return;
        }

        // Perform an initial sync to set the correct number of blocks
        plugin.getLogger().fine("Performing initial visual sync for team " + team.getTeamName());
        syncVisualState(); // Set initial state based on current time

        // Schedule the repeating task
        plugin.getLogger().info("Starting visual timer updates task for team " + team.getTeamName());
        visualUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisuals, VISUAL_UPDATE_INTERVAL_TICKS, VISUAL_UPDATE_INTERVAL_TICKS);
    }

    /**
     * Stops the scheduled task that updates the visual sand display.
     */
    public void stopVisualUpdates() {
        if (visualUpdateTask != null) {
            if (!visualUpdateTask.isCancelled()) {
                visualUpdateTask.cancel();
            }
            visualUpdateTask = null; // Clear the task reference
            plugin.getLogger().log(Level.INFO, "Stopped visual timer updates task for team " + team.getTeamName());
        }
    }

    /**
     * Internal method run periodically by the scheduler.
     * Checks the logical time and removes one sand block if the visual display
     * has more blocks than the target count dictates. Designed for the "draining" effect.
     */
    private void updateVisuals() {
        // Stop if task was cancelled externally or height is invalid
        if (totalHeight <= 0 || visualUpdateTask == null || visualUpdateTask.isCancelled()) {
            stopVisualUpdates(); // Ensure stopped state
            return;
        }

        // Get current logical time and calculate target block count
        int remainingSeconds = team.getRemainingSeconds(); // Assumes SoTTeam provides this
        int targetBlocks = getTargetBlockCount(remainingSeconds);

        // Optimization: If the target hasn't changed since the last check, do nothing.
        // This prevents unnecessary block counting if time hasn't dropped enough to change target.
        // Note: This differs slightly from syncVisualState which always checks physical blocks.
        if (targetBlocks == lastKnownVisualBlocks) {
            return;
        }

        // Get the current number of physical blocks (more expensive check)
        int currentBlocks = countCurrentSandBlocks();

        // If the visual display has more blocks than it should, remove one from the bottom
        if (targetBlocks < currentBlocks) {
            removeBottomSandBlock();
            // Update last known count based on the new target, not the physical count,
            // so it continues trying to remove blocks on subsequent ticks if needed.
            lastKnownVisualBlocks = targetBlocks;
        } else {
            // If target >= current, syncVisualState handles adding blocks if needed.
            // Update last known count even if no change occurred this tick.
            lastKnownVisualBlocks = targetBlocks;
        }
    }

    /**
     * Forces the visual sand blocks to exactly match the team's current remaining seconds.
     * Compares the target number of blocks to the physically present blocks and adds/removes
     * the difference. Useful after time is added or when starting/resetting the timer.
     */
    public void syncVisualState() {
        if (totalHeight <= 0) return; // Don't run for invalid timers

        // Get current logical time and calculate target block count
        int remainingSeconds = team.getRemainingSeconds(); // Assumes SoTTeam provides this
        int targetBlocks = getTargetBlockCount(remainingSeconds);

        // Check the actual number of sand blocks currently in the world
        int currentBlocks = countCurrentSandBlocks();

        plugin.getLogger().log(Level.FINEST, "Syncing visual state for " + team.getTeamName() + ". Target: " + targetBlocks + ", Current: " + currentBlocks);

        // Add or remove blocks to match the target
        if (targetBlocks > currentBlocks) {
            addSandToTop(targetBlocks - currentBlocks);
        } else if (targetBlocks < currentBlocks) {
            removeSandFromBottom(currentBlocks - targetBlocks);
        }
        // else: currentBlocks == targetBlocks, no change needed

        // Update the tracker to the target state
        lastKnownVisualBlocks = targetBlocks;
    }

    /**
     * Calculates how many sand blocks should be visible based on remaining seconds.
     *
     * @param remainingSeconds The actual remaining seconds from the logical timer source.
     * @return The number of sand blocks that should be present, capped by totalHeight.
     */
    private int getTargetBlockCount(int remainingSeconds) {
        if (remainingSeconds <= 0) return 0;
        // Calculate blocks needed: ceiling(time / seconds_per_block)
        // Example: 1s -> 1 block, 10s -> 1 block, 11s -> 2 blocks
        int blocks = (int) Math.ceil((double) remainingSeconds / SECONDS_PER_BLOCK_VISUAL);
        // Ensure calculated blocks don't exceed the physical height of the column
        return Math.min(blocks, this.totalHeight);
    }

    /**
     * Adds a specified number of sand blocks visually to the top of the column,
     * starting from the lowest available air/passable block.
     *
     * @param blocksToAdd Number of sand blocks to add.
     */
    private void addSandToTop(int blocksToAdd) {
        if (blocksToAdd <= 0 || totalHeight <= 0) return;
        World world = topLocation.getWorld(); // Assumes world is loaded and valid
        if (world == null) {
             plugin.getLogger().severe("Cannot add sand: World is null for team " + team.getTeamName());
             return;
        }

        int blocksAdded = 0;
        // Start from the block ABOVE the bottom marker and go upwards
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY() && blocksAdded < blocksToAdd; y++) {
            Location currentLocation = new Location(world, topLocation.getBlockX(), y, topLocation.getBlockZ());
            Block block = currentLocation.getBlock();
            // Place sand if the block is air or passable (e.g., water, tall grass)
            if (block.getType() == Material.AIR || block.isPassable()) {
                // Set to sand, false = don't apply physics immediately (prevents chain reactions)
                block.setType(Material.SAND, false);
                blocksAdded++;
            }
            // If we hit a solid block, assume the column is full up to this point
            else if (block.getType() == Material.SAND) {
                 continue; // Already sand, check next block up
            } else {
                 plugin.getLogger().warning("Visual timer path obstructed at Y=" + y + " for team " + team.getTeamName() + " (" + block.getType() + ")");
                 break; // Stop adding if path is blocked by non-sand/non-air
            }
        }
        if (blocksAdded > 0) plugin.getLogger().log(Level.FINE, "Visually added " + blocksAdded + " sand blocks for team " + team.getTeamName());
    }

    /**
     * Convenience method to remove one sand block from the bottom of the visual stack.
     */
    private void removeBottomSandBlock() {
        removeSandFromBottom(1);
    }

    /**
     * Removes a specified number of sand blocks visually from the bottom of the column.
     *
     * @param blocksToRemove Number of blocks to remove.
     */
    private void removeSandFromBottom(int blocksToRemove) {
        if (blocksToRemove <= 0 || totalHeight <= 0) return;
        World world = bottomLocation.getWorld();
        if (world == null) {
             plugin.getLogger().severe("Cannot remove sand: World is null for team " + team.getTeamName());
             return;
        }

        int blocksRemoved = 0;
        // Start checking from the block ABOVE the bottom marker upwards
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY() && blocksRemoved < blocksToRemove; y++) {
            Location currentLocation = new Location(world, bottomLocation.getBlockX(), y, bottomLocation.getBlockZ());
            Block block = currentLocation.getBlock();
            // If we find a sand block, remove it
            if (block.getType() == Material.SAND) {
                block.setType(Material.AIR, false); // Set to air, no physics update
                blocksRemoved++;
            } else {
                // Stop if we hit air or any non-sand block, means we reached the current top of the stack
                break;
            }
        }
        if (blocksRemoved > 0) plugin.getLogger().log(Level.FINE, "Visually removed " + blocksRemoved + " sand blocks for team " + team.getTeamName());
    }


    /**
     * Counts the current number of physical sand blocks present in the timer column in the world.
     *
     * @return The number of Material.SAND blocks found within the defined column bounds.
     */
    private int countCurrentSandBlocks() {
        if (totalHeight <= 0) return 0;
        World world = bottomLocation.getWorld();
        if (world == null) return 0; // Cannot count in null world

        int count = 0;
        // Iterate vertically from the block above bottomLocation up to topLocation (inclusive)
        for (int y = bottomLocation.getBlockY() + 1; y <= topLocation.getBlockY(); y++) {
            // Create location for the current Y level
            Location currentLocation = new Location(world, bottomLocation.getBlockX(), y, bottomLocation.getBlockZ());
            // Check if the block at this location is sand
            if (currentLocation.getBlock().getType() == Material.SAND) {
                count++;
            }
        }
        return count;
    }
}
