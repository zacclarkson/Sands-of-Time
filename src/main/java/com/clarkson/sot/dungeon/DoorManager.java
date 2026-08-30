package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.entities.Area;
import com.clarkson.sot.entities.Door;
import com.clarkson.sot.entities.SegmentDoor;
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
 * Manages the segment doors within active dungeon instances.
 * Handles initialization, interaction (locking/unlocking), and state.
 *
 * <p>Vault marker blocks are deliberately <em>not</em> doors. {@link VaultManager} owns them
 * end to end -- key consumption, per-team open state, rewards and the team broadcast -- so
 * registering them here as well made a single right-click emit every message twice.
 */
public class DoorManager implements Listener {

    /** Material the door blocks are built from when closed. */
    private static final Material DOOR_MATERIAL = Material.DARK_OAK_PLANKS;

    /**
     * Material of the keyhole block at each door's lock location. Deliberately different from
     * {@link #DOOR_MATERIAL}: without it the door is a featureless wall with no clue as to which
     * of its twelve blocks accepts the key.
     */
    private static final Material KEYHOLE_MATERIAL = Material.OXIDIZED_CUT_COPPER;

    /** Height of a doorway opening, in blocks, measured from the entry point marker upwards. */
    private static final int OPENING_HEIGHT = 4;

    private final SoT plugin;
    private final GameManager gameManager;
    // Store active doors per team instance, mapped by their Lock Location for quick lookup
    private final Map<UUID, Map<Location, Door>> doorsByTeamAndLockLocation;

    public DoorManager(SoT plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.doorsByTeamAndLockLocation = new ConcurrentHashMap<>();
        // Registered as a listener by SoT.onEnable, which is the single registration point for the
        // GameManager-owned manager instances. Registering here too would double-fire every event.
        plugin.getLogger().info("DoorManager initialized.");
    }

    /**
     * Initializes all doors for a specific dungeon instance.
     * Builds a rusty-key SegmentDoor at every connection between placed segments, and seals the
     * entry points generation never attached a neighbour to.
     * Should be called by DungeonManager after segments are pasted.
     *
     * @param dungeonData The Dungeon object containing absolute locations for this instance.
     */
    public void initializeDoorsForInstance(@NotNull Dungeon dungeonData) {
        UUID teamId = dungeonData.getTeamId();
        plugin.getLogger().info("Initializing doors for team instance: " + teamId);
        Map<Location, Door> teamDoors = new ConcurrentHashMap<>();

        // --- Create Segment Doors (Between segments) ---
        // The generator records exactly which entry points it connected; both sides of a
        // connection share one cell, so each doorway yields one door.
        for (EntryPoint doorway : dungeonData.getDoorways()) {
            Location markerLoc = doorway.getLocation();
            Direction dir = doorway.getDirection();
            if (markerLoc == null || dir == null || !markerLoc.isWorldLoaded()) {
                plugin.getLogger().warning("Skipping doorway with no location, direction or loaded world for team " + teamId);
                continue;
            }

            Area doorBounds = openingBounds(markerLoc, dir);
            Location lockLoc = markerLoc.clone().add(0, 1, 0); // Lock at eye level (1 above marker)

            SegmentDoor segDoor = new SegmentDoor(plugin, teamId, doorBounds, lockLoc, DOOR_MATERIAL, KEYHOLE_MATERIAL);
            // Templates carve doorways as open holes, so the door has to be built before it exists
            // to a player: without this the passage stays walkable and the lock location is air.
            segDoor.buildClosed();
            teamDoors.put(lockLoc, segDoor);
            plugin.getLogger().finer("Created SegmentDoor at " + lockLoc.toVector() + " for team " + teamId);
        }
        plugin.getLogger().info("Created " + teamDoors.size() + " segment doors for team " + teamId);

        // Vault marker blocks are intentionally left out: VaultManager handles those clicks.

        doorsByTeamAndLockLocation.put(teamId, teamDoors);

        sealUnusedOpenings(dungeonData);

        plugin.getLogger().info("Finished initializing " + teamDoors.size() + " doors for team instance: " + teamId);
    }

    /**
     * Fills the entry points generation attached no neighbour to with plain wall.
     *
     * <p>A segment template carves every entry point it declares as an open 3x4 hole -- the hub
     * alone declares nine -- and the DFS only ever uses some of them. The leftovers open onto
     * nothing, so they are walled off rather than dressed as doors, which would cost a rusty key
     * to open onto empty space.
     *
     * @param dungeonData The Dungeon object containing absolute locations for this instance.
     */
    private void sealUnusedOpenings(@NotNull Dungeon dungeonData) {
        int sealed = 0;
        for (EntryPoint opening : dungeonData.getUnusedOpenings()) {
            Location markerLoc = opening.getLocation();
            Direction dir = opening.getDirection();
            if (markerLoc == null || dir == null || !markerLoc.isWorldLoaded()) continue;

            fillOpening(openingBounds(markerLoc, dir), DOOR_MATERIAL);
            sealed++;
        }
        if (sealed > 0) {
            plugin.getLogger().info("Sealed " + sealed + " unused openings for team " + dungeonData.getTeamId());
        }
    }

    /**
     * The blocks making up a doorway opening: 3 wide across the passage and
     * {@link #OPENING_HEIGHT} tall, with the entry point marker at its bottom centre.
     *
     * @param marker The entry point marker block (bottom centre of the opening).
     * @param dir The direction the opening faces.
     */
    @NotNull
    private Area openingBounds(@NotNull Location marker, @NotNull Direction dir) {
        Vector perpendicular = getPerpendicular(dir);
        Location min = marker.clone().add(perpendicular.clone().multiply(-1)); // One block left
        Location max = marker.clone().add(perpendicular).add(0, OPENING_HEIGHT - 1, 0); // One right, up
        return new Area(min, max);
    }

    /** Sets every block in an area to one material, skipping unloaded chunks. */
    private void fillOpening(@NotNull Area bounds, @NotNull Material material) {
        Location min = bounds.getMinPoint();
        Location max = bounds.getMaxPoint();
        World world = min.getWorld();
        if (world == null) return;

        for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != material) block.setType(material, false);
                }
            }
        }
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
    public Door getDoorAt(UUID teamId, Location lockLocation) {
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
        Door door = getDoorAt(teamId, clickedLocation);

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
                        player.sendMessage(Component.text("You unlocked the door!", NamedTextColor.GREEN));
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
