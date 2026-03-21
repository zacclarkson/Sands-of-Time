package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.entities.Area;
import com.clarkson.sot.entities.Door;
import com.clarkson.sot.entities.SegmentDoor;
import com.clarkson.sot.entities.VaultDoor;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;

import net.kyori.adventure.text.Component;
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
import org.bukkit.util.Vector;
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
        // Iterate placed segments and create doors at entry points that connect to other segments.
        // Entry points are 3 wide x 4 tall. The marker is at bottom center.
        DungeonManager teamDungeonManager = gameManager.getTeamDungeonManager(teamId);
        if (teamDungeonManager != null) {
            List<PlacedSegment> placedSegments = teamDungeonManager.getPlacedSegmentsInWorld();
            Set<String> processedConnections = new HashSet<>(); // Avoid duplicate doors

            for (PlacedSegment segment : placedSegments) {
                for (EntryPoint ep : segment.getAbsoluteEntryPoints()) {
                    Location epLoc = ep.getLocation();
                    Direction dir = ep.getDirection();
                    if (epLoc == null || dir == null) continue;

                    // Create a unique key for this connection to avoid duplicates
                    // (two segments share the same connection point)
                    String connectionKey = epLoc.getBlockX() + "," + epLoc.getBlockY() + "," + epLoc.getBlockZ();
                    if (processedConnections.contains(connectionKey)) continue;
                    processedConnections.add(connectionKey);

                    // Build door bounds: 3 wide x 4 tall centered on the entry point marker (bottom center)
                    // The marker is at bottom center of the 3x4 opening
                    Vector perpendicular = getPerpendicular(dir);
                    Location min = epLoc.clone().add(perpendicular.clone().multiply(-1)); // One block left
                    Location max = epLoc.clone().add(perpendicular).add(0, 3, 0); // One block right, 3 blocks up

                    Area doorBounds = new Area(min, max);
                    Location lockLoc = epLoc.clone().add(0, 1, 0); // Lock at eye level (1 above marker)

                    SegmentDoor segDoor = new SegmentDoor(plugin, teamId, doorBounds, lockLoc, Material.DARK_OAK_PLANKS);
                    teamDoors.put(lockLoc, segDoor);
                    plugin.getLogger().finer("Created SegmentDoor at " + lockLoc.toVector() + " for team " + teamId);
                }
            }
            plugin.getLogger().info("Created " + processedConnections.size() + " segment doors for team " + teamId);
        }


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
        int count = doorsByTeamAndLockLocation.size();
        doorsByTeamAndLockLocation.clear();
        plugin.getLogger().info("Cleared door states for " + count + " teams.");
    }

    /**
     * Gets a perpendicular horizontal vector for a given direction.
     * Used to calculate door width across an entry point.
     */
    private Vector getPerpendicular(Direction dir) {
        switch (dir) {
            case NORTH:
            case SOUTH:
                return new Vector(1, 0, 0); // Door spans along X axis
            case EAST:
            case WEST:
                return new Vector(0, 0, 1); // Door spans along Z axis
            default:
                return new Vector(1, 0, 0); // Fallback
        }
    }
}
