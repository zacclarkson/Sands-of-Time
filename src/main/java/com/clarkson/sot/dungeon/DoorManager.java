package com.clarkson.sot.dungeon; // Or com.clarkson.sot.dungeon

import com.clarkson.sot.entities.Area; // Needed for init logic later
import com.clarkson.sot.entities.Door; // Import the interface/abstract class
import com.clarkson.sot.entities.VaultDoor;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component; // Adventure API
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all Door instances within active dungeon instances.
 * Handles initialization, interaction (locking/unlocking), and state.
 */
public class DoorManager implements Listener {

    private final SoT plugin;
    private final GameManager gameManager;
    // Store active doors per team instance, mapped by their Lock Location for quick lookup
    private final Map<UUID, Map<Location, Door>> doorsByTeamAndLockLocation;

    public DoorManager(SoT plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.doorsByTeamAndLockLocation = new ConcurrentHashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("DoorManager initialized.");
    }

    /**
     * Initializes all doors for a specific dungeon instance.
     * Creates SegmentDoor and VaultDoor objects based on the dungeon data.
     * Should be called by DungeonManager after segments are pasted.
     *
     * @param dungeonData The Dungeon object containing absolute locations for this instance.
     */
    public void initializeDoorsForInstance(@NotNull Dungeon dungeonData) {
        UUID teamId = dungeonData.getTeamId();
        plugin.getLogger().info("Initializing doors for team instance: " + teamId);
        Map<Location, Door> teamDoors = new ConcurrentHashMap<>();

        // --- Create Segment Doors (Between segments) ---
        // TODO: Implement logic to find adjacent segments and create doors at entry points.
        // This requires iterating through placedSegments in dungeonData.getBlueprintData(),
        // finding connections between adjacent segments based on their relative origins and entry points,
        // calculating the absolute location of the entry point block(s) (potential lock location / bounds),
        // determining the bounds (e.g., 1x2 or 2x2 area around the entry point),
        // and choosing a door material (e.g., IRON_BARS or from segment metadata).
        plugin.getLogger().warning("SegmentDoor initialization logic is not yet implemented in DoorManager!");
        // Example Placeholder:
        // Location exampleLockLoc = dungeonData.getHubLocation().clone().add(5, 0, 0); // Totally fake location
        // Area exampleBounds = new Area(exampleLockLoc.clone().add(0,0,0), exampleLockLoc.clone().add(0,1,0)); // Fake 1x2 bounds
        // SegmentDoor exampleDoor = new SegmentDoor(plugin, teamId, exampleBounds, exampleLockLoc, Material.IRON_BARS);
        // teamDoors.put(exampleLockLoc, exampleDoor);


        // --- Create Vault Doors ---
        for (Map.Entry<VaultColor, Location> entry : dungeonData.getVaultMarkerLocations().entrySet()) {
            VaultColor color = entry.getKey();
            Location lockLoc = entry.getValue(); // Vault marker location is the lock location
            if (lockLoc != null && lockLoc.isWorldLoaded()) {
                // Bounds for a vault door might just be the single marker block itself
                Area vaultBounds = new Area(lockLoc, lockLoc); // Area containing just the lock block
                VaultDoor door = new VaultDoor(plugin, teamId, vaultBounds, lockLoc, color);
                teamDoors.put(lockLoc, door);
                 plugin.getLogger().finer("Created VaultDoor instance for " + color + " at " + lockLoc.toVector());
            } else {
                 plugin.getLogger().warning("Invalid location for " + color + " vault marker when creating VaultDoor for team " + teamId);
            }
        }

        doorsByTeamAndLockLocation.put(teamId, teamDoors);
        plugin.getLogger().info("Finished initializing " + teamDoors.size() + " doors for team instance: " + teamId);
    }

     /**
      * Clears door state for a specific team.
      * @param teamId The UUID of the team whose state should be cleared.
      */
     public void clearTeamState(UUID teamId) {
         doorsByTeamAndLockLocation.remove(teamId);
         plugin.getLogger().info("Cleared door state for team: " + teamId);
     }

    /**
     * Finds a door associated with a specific team at a given lock location.
     * Compares block coordinates for accuracy.
     * @param teamId The team's UUID.
     * @param lockLocation The potential lock location.
     * @return The Door object, or null if no door exists for that team at that location.
     */
    @Nullable
    private Door getDoorAtLockLocation(UUID teamId, Location lockLocation) {
        Map<Location, Door> teamDoors = doorsByTeamAndLockLocation.get(teamId);
        if (teamDoors == null || lockLocation == null || lockLocation.getWorld() == null) {
            return null;
        }
        World world = lockLocation.getWorld();
        int x = lockLocation.getBlockX();
        int y = lockLocation.getBlockY();
        int z = lockLocation.getBlockZ();

        // Check map directly first if using Location as key works reliably
        // (depends on Location's hashCode/equals implementation across server restarts/reloads)
        // For robustness, iterating and comparing coords is safer.
        for (Map.Entry<Location, Door> entry : teamDoors.entrySet()) {
             Location keyLoc = entry.getKey();
             if (keyLoc.getWorld().equals(world) &&
                 keyLoc.getBlockX() == x &&
                 keyLoc.getBlockY() == y &&
                 keyLoc.getBlockZ() == z)
             {
                 return entry.getValue();
             }
        }
        return null;
    }


    @EventHandler(priority = EventPriority.HIGH) // High priority to potentially cancel interaction
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getCurrentState() != GameState.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        UUID teamId = gameManager.getTeamManager().getPlayerTeamId(player);
        if (teamId == null) return; // Player not on a team

        Block clickedBlock = event.getClickedBlock();
        Location clickedLocation = clickedBlock.getLocation();

        // Find if a door lock exists at this location for the player's team
        Door door = getDoorAtLockLocation(teamId, clickedLocation);

        if (door != null) {
            event.setCancelled(true); // We are handling this interaction

            if (door.isOpen()) {
                 player.sendMessage(Component.text("This door is already open.", NamedTextColor.YELLOW));
                 // Optionally allow closing SegmentDoors?
                 // if (door instanceof SegmentDoor) { door.close(player); }
                return;
            }

            // Door is closed, check for key
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            // Use the door's own logic to check the key
            if (door.isCorrectKey(itemInHand)) {
                // Attempt to consume the key (ItemManager handles specifics)
                if (consumeKeyItem(player, itemInHand, door)) { // Pass door to know which key type to consume
                    // Key consumed, attempt to open the door
                    if (door.open(player)) {
                        // Success message might depend on door type
                        if (door instanceof VaultDoor) {
                             player.sendMessage(Component.text("You unlocked the vault!", NamedTextColor.GOLD));
                        } else {
                             player.sendMessage(Component.text("You unlocked the door!", NamedTextColor.GREEN));
                        }
                    } else {
                        player.sendMessage(Component.text("The door mechanism seems stuck...", NamedTextColor.RED));
                        // TODO: Give key back?
                    }
                } else {
                     player.sendMessage(Component.text("Error: Could not use the key!", NamedTextColor.RED));
                     plugin.getLogger().warning("Failed to consume key from " + player.getName() + " for door " + door.getId() + " despite isCorrectKey being true.");
                }
            } else {
                player.sendMessage(Component.text("This door is locked. You need the correct key.", NamedTextColor.RED));
            }
        }
    }

    /** Consumes one of the required key item from the player's main hand. */
    private boolean consumeKeyItem(Player player, ItemStack keyItem, Door door) {
         if (keyItem == null || keyItem.getType() == Material.AIR) return false;

         // We already know it's the correct key type from door.isCorrectKey()
         // Just need to decrement the amount
         if (keyItem.getAmount() > 1) {
             keyItem.setAmount(keyItem.getAmount() - 1);
         } else {
             player.getInventory().setItemInMainHand(null); // Remove item stack completely
         }
         player.updateInventory();
         return true;
    }

    public void clearAllTeamStates() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'clearAllTeamStates'");
    }

    // TODO: Add method to create Rusty Key ItemStack using ItemManager
    // public ItemStack createRustyKey() { return ItemManager.createRustyKey(); }

}
