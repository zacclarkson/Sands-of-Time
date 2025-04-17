package com.clarkson.sot.entities; // Or com.clarkson.sot.dungeon

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Represents a generic animated door within a dungeon instance.
 * Handles common state and provides abstract methods for key checks
 * and concrete methods for shared open/close animations.
 */
public abstract class Door {

    // --- Shared Fields ---
    protected final Plugin plugin;
    protected final UUID id;
    protected final UUID teamId;
    protected final Area bounds; // Defines the blocks making up the door structure
    protected final Location lockLocation; // Block to interact with
    protected boolean isOpen;
    protected BukkitTask currentAnimationTask;
    protected final int animationTickDelay;

    // Constructor and other methods...
    protected Door(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds, @NotNull Location lockLocation) {
        this(plugin, teamId, bounds, lockLocation, 3);
    }
    protected Door(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds, @NotNull Location lockLocation, int animationTickDelay) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "Bounds cannot be null");
        this.lockLocation = Objects.requireNonNull(lockLocation, "Lock location cannot be null");
        this.id = UUID.randomUUID();
        this.isOpen = false;
        this.currentAnimationTask = null;
        this.animationTickDelay = Math.max(1, animationTickDelay);
    }

    @NotNull public UUID getId() { return id; }
    @NotNull public UUID getTeamId() { return teamId; }
    @NotNull public Area getBounds() { return bounds; }
    @NotNull public Location getLockLocation() { return lockLocation; }
    public boolean isOpen() { return isOpen; }

    public abstract boolean isCorrectKey(@Nullable ItemStack keyStack);
    @NotNull protected abstract Material getClosedMaterial();

    public boolean open(@NotNull Player player) {
        if (isOpen || (currentAnimationTask != null && !currentAnimationTask.isCancelled())) return false;
        startOpeningAnimation(player);
        return true;
    }

    public boolean close(@Nullable Player player) {
         if (!isOpen || (currentAnimationTask != null && !currentAnimationTask.isCancelled())) return false;
         startClosingAnimation(player);
         return true;
    }

    protected void startOpeningAnimation(@NotNull Player player) {
        cancelAnimation();
        final List<Block> blocksToChange = getBlocksSorted(true); // Get blocks sorted top-to-bottom
        final World world = lockLocation.getWorld();

        if (blocksToChange.isEmpty() || world == null) {
            plugin.getLogger().warning("Cannot start opening animation for door " + id + ": No blocks found or world is null.");
            this.isOpen = true;
            return;
        }

        this.currentAnimationTask = new BukkitRunnable() {
            int currentIndex = 0;
            @Override
            public void run() {
                if (currentIndex >= blocksToChange.size()) { finishOpening(); return; }
                double currentY = blocksToChange.get(currentIndex).getY();
                boolean layerProcessed = false;
                while (currentIndex < blocksToChange.size() && blocksToChange.get(currentIndex).getY() == currentY) {
                    Block block = blocksToChange.get(currentIndex++);
                    if (block.getType() != Material.AIR) { block.setType(Material.AIR, false); layerProcessed = true; }
                }
                if (layerProcessed) world.playSound(lockLocation, Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.5f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, animationTickDelay);
    }

     private void finishOpening() {
         this.isOpen = true; this.currentAnimationTask = null;
         if (lockLocation.getWorld() != null) lockLocation.getWorld().playSound(lockLocation, Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
         plugin.getLogger().fine("Finished opening door " + id);
     }

    protected void startClosingAnimation(@Nullable Player player) {
        cancelAnimation();
        final List<Block> blocksToChange = getBlocksSorted(false); // Get blocks sorted bottom-to-top
        final Material closedMat = getClosedMaterial();
        final World world = lockLocation.getWorld();

        if (blocksToChange.isEmpty() || world == null) {
             plugin.getLogger().warning("Cannot start closing animation for door " + id + ": No blocks found or world is null.");
             this.isOpen = false;
             return;
        }

        this.currentAnimationTask = new BukkitRunnable() {
            int currentIndex = 0;
            @Override
            public void run() {
                if (currentIndex >= blocksToChange.size()) { finishClosing(); return; }
                 double currentY = blocksToChange.get(currentIndex).getY();
                 boolean layerProcessed = false;
                 while (currentIndex < blocksToChange.size() && blocksToChange.get(currentIndex).getY() == currentY) {
                     Block block = blocksToChange.get(currentIndex++);
                     if (block.getType() != closedMat) { block.setType(closedMat, false); layerProcessed = true; }
                 }
                 if (layerProcessed) world.playSound(lockLocation, Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.5f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, animationTickDelay);
    }

     private void finishClosing() {
         this.isOpen = false; this.currentAnimationTask = null;
         List<Block> finalBlocks = getBlocksSorted(false); // Get blocks again to be safe
         forceBlockUpdates(finalBlocks); // Update physics now
         if (lockLocation.getWorld() != null) lockLocation.getWorld().playSound(lockLocation, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f);
         plugin.getLogger().fine("Finished closing door " + id);
     }

    public void setOpenState(boolean open) {
        cancelAnimation();
        this.isOpen = open;
        Material targetMaterial = open ? Material.AIR : getClosedMaterial();
        // Get blocks using the now implemented helper
        List<Block> blocks = getBlocksSorted(false); // Sorting order doesn't matter for instant set

        if (!Bukkit.isPrimaryThread()) {
            new BukkitRunnable() { @Override public void run() { setBlocksInstantly(blocks, targetMaterial); } }.runTask(plugin);
        } else {
            setBlocksInstantly(blocks, targetMaterial);
        }
         plugin.getLogger().fine("Set door " + id + " state to " + (open ? "OPEN" : "CLOSED") + " instantly.");
    }

    private void setBlocksInstantly(List<Block> blocks, Material material) {
        for (Block block : blocks) { if (block.getType() != material) block.setType(material, false); }
        forceBlockUpdates(blocks);
    }

     private void forceBlockUpdates(List<Block> blocks) {
         if (blocks.isEmpty()) return;
         // Run on next tick to allow block changes to settle slightly? Optional.
         new BukkitRunnable() {
             @Override
             public void run() {
                  for(Block block : blocks) {
                      // Check if chunk is loaded before updating state
                      if (block.getChunk().isLoaded()) {
                           block.getState().update(true, true); // Force update, apply physics
                      }
                  }
             }
         }.runTask(plugin);
     }

    protected void cancelAnimation() {
        if (this.currentAnimationTask != null && !this.currentAnimationTask.isCancelled()) {
            this.currentAnimationTask.cancel();
             plugin.getLogger().finer("Cancelled animation task for door " + id);
        }
        this.currentAnimationTask = null;
    }

    // --- Helper Methods ---

    /**
     * Gets the list of blocks within the door's bounds by iterating coordinates,
     * then sorts them by Y-coordinate.
     * @param topToBottom If true, sort highest Y first. If false, sort lowest Y first.
     * @return Sorted list of blocks. Returns empty list if bounds are invalid or world is null.
     */
    @NotNull
    protected List<Block> getBlocksSorted(boolean topToBottom) {
        List<Block> blocks = new ArrayList<>();
        World world = lockLocation.getWorld(); // Get world from lock location
        Location min = bounds.getMinPoint();
        Location max = bounds.getMaxPoint();

        // Validate bounds and world
        if (world == null || min == null || max == null) {
             plugin.getLogger().severe("Cannot get blocks for door " + id + ": Invalid bounds or null world.");
             return blocks; // Return empty list
        }

        // Ensure min/max are correct (Area constructor should handle this, but double-check)
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        // Iterate through all coordinates within the bounds
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Check if chunk is loaded before getting block - prevents sync load
                    if (world.isChunkLoaded(x >> 4, z >> 4)) {
                        blocks.add(world.getBlockAt(x, y, z));
                    } else {
                        plugin.getLogger().warning("Skipping block for door " + id + " at " + x + "," + y + "," + z + " - Chunk not loaded.");
                    }
                }
            }
        }

        // Sort the collected blocks based on Y coordinate
        if (topToBottom) {
            blocks.sort(Comparator.comparingDouble(Block::getY).reversed()); // Highest Y first
        } else {
            blocks.sort(Comparator.comparingDouble(Block::getY)); // Lowest Y first
        }
        return blocks;
    }
}
