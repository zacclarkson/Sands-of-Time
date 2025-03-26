package com.clarkson.sot.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class SandTimer implements Listener{

    private final Plugin plugin;
    private final List<Location> sandInputLocations; // Locations where sand can be added
    private final Location bottomLocation; // Bottom of the sand timer
    private final Location topLocation; // Top of the sand timer
    private BukkitTask timerTask; // Task for the timer
    private boolean isRunning;

    public SandTimer(Plugin plugin, List<Location> sandInputLocations, Location bottomLocation, Location topLocation) {
        this.plugin = plugin;
        this.sandInputLocations = sandInputLocations;
        this.bottomLocation = bottomLocation;
        this.topLocation = topLocation;
        this.isRunning = false;
    }

    /**
     * Starts the sand timer.
     */
    public void start() {
        if (isRunning) {
            plugin.getLogger().info("Sand timer is already running.");
            return;
        }

        isRunning = true;

        // Schedule a repeating task to remove the bottom sand block every 10 seconds
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World world = bottomLocation.getWorld();
            if (world == null) return;

            // Remove the bottom sand block
            if (bottomLocation.getBlock().getType() == Material.SAND) {
                bottomLocation.getBlock().setType(Material.AIR);

                // Move the bottom location up by 1 block
                bottomLocation.add(0, 1, 0);

                // Check if the bottom has reached the top
                if (bottomLocation.getY() > topLocation.getY()) {
                    stop(); // Stop the timer if all sand is gone
                    plugin.getLogger().info("Sand timer has run out.");
                }
            }
        }, 0L, 200L); // 200 ticks = 10 seconds
    }

    /**
     * Stops the sand timer.
     */
    public void stop() {
        if (!isRunning) {
            plugin.getLogger().info("Sand timer is not running.");
            return;
        }

        isRunning = false;

        // Cancel the task
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        plugin.getLogger().info("Sand timer stopped.");
    }

    /**
     * Refills the sand timer to the top.
     */
    public void refill() {
        World world = bottomLocation.getWorld();
        if (world == null) return;

        // Reset the bottom location to the original position
        bottomLocation.setY(topLocation.getY());

        // Fill the sand timer from bottom to top
        for (double y = bottomLocation.getY(); y >= topLocation.getY(); y--) {
            Location currentLocation = new Location(world, bottomLocation.getX(), y, bottomLocation.getZ());
            currentLocation.getBlock().setType(Material.SAND);
        }

        plugin.getLogger().info("Sand timer refilled.");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        if (placedBlock.getType() == Material.SAND) {
            Location placedLocation = placedBlock.getLocation();

            // Check if the placed location matches one of the input locations
            if (sandInputLocations.contains(placedLocation)) {
                // Handle the sand being added to the timer
                placedBlock.setType(Material.AIR); // Remove the sand from the input location
                addSandToTop(); // Add sand to the top of the timer
            }
        }
    }
    
    private void addSandToTop() {
        // Add sand to the top of the timer
        for (double y = topLocation.getY(); y >= bottomLocation.getY(); y--) {
            Location currentLocation = new Location(topLocation.getWorld(), topLocation.getX(), y, topLocation.getZ());
            if (currentLocation.getBlock().getType() == Material.AIR) {
                currentLocation.getBlock().setType(Material.SAND);
                break;
            }
        }
    }

    /**
     * Adds sand to the timer from the input locations.
     */
    public void addSandFromInputs() {
        World world = bottomLocation.getWorld();
        if (world == null) return;

        for (Location inputLocation : sandInputLocations) {
            if (inputLocation.getBlock().getType() == Material.SAND) {
                inputLocation.getBlock().setType(Material.AIR); // Remove sand from input
                refill(); // Refill the timer
                break;
            }
        }
    }
}