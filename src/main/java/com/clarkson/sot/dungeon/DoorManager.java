package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.entities.Area;
import com.clarkson.sot.entities.Door;
import com.clarkson.sot.entities.Gate;
import com.clarkson.sot.entities.SegmentDoor;
import com.clarkson.sot.entities.VaultDoor;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
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

    /**
     * Material a closed gate is built from. See-through on purpose: the mechanic is deciding whether
     * what is behind the gate is worth opening it for, and an opaque wall hides the very thing being
     * weighed. It also reads as a gate rather than as one of the dark oak segment doors.
     */
    static final Material GATE_MATERIAL = Material.IRON_BARS;

    /**
     * Placed under a lever whose marker cell has no solid neighbour. A lever with nothing to hang on
     * pops off at the first neighbour update, and gates nothing can open are worse than a stray block.
     */
    static final Material LEVER_SUPPORT_MATERIAL = Material.POLISHED_DEEPSLATE;

    /** Faces searched for something to hang a lever on. Walls first -- that is what a builder clicks. */
    private static final BlockFace[] LEVER_SUPPORT_ORDER = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.DOWN, BlockFace.UP };

    // Plugin, not SoT: this manager only needs a logger and a scheduler handle to pass to its
    // doors, and taking the interface lets the tests drive it with a real plugin.
    private final Plugin plugin;
    private final GameManager gameManager;
    // Store active doors per team instance, mapped by their Lock Location for quick lookup
    private final Map<UUID, Map<Location, Door>> doorsByTeamAndLockLocation;

    /**
     * Lever location -> the gates that lever opens, per team. One lever, many gates: a segment with
     * gates carries exactly one lever, and pulling it opens that segment's gates and no others.
     */
    private final Map<UUID, Map<Location, List<Gate>>> gatesByTeamAndLeverLocation;

    /**
     * Vault colour -> that colour's doors, per team. Deliberately <em>not</em> in
     * {@link #doorsByTeamAndLockLocation}: a vault door takes no key, and keeping it out of the
     * lock-location map is what stops {@link #getDoorAt} ever resolving one into the key-checking
     * branch of {@link #onPlayerInteract} -- the shape of bug #65.
     */
    private final Map<UUID, Map<VaultColor, List<VaultDoor>>> vaultDoorsByTeamAndColor;

    public DoorManager(Plugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.doorsByTeamAndLockLocation = new ConcurrentHashMap<>();
        this.gatesByTeamAndLeverLocation = new ConcurrentHashMap<>();
        this.vaultDoorsByTeamAndColor = new ConcurrentHashMap<>();
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
     * Builds this instance's gates closed with a real lever at each group's marker cell, and builds
     * every vault door closed.
     *
     * <p>Called after {@link #initializeDoorsForInstance}, so a gate that overlaps an opening
     * {@code sealUnusedOpenings} filled with plain wall wins -- the interactive thing should.
     *
     * @param teamId     The team whose instance these belong to.
     * @param gateGroups Absolute gates paired with the lever that opens them, one group per segment.
     * @param vaultDoors Absolute vault door walls with the vault colour that opens each.
     */
    public void initializeGatesForInstance(@NotNull UUID teamId,
                                           @NotNull List<GateGroup> gateGroups,
                                           @NotNull List<VaultDoorPlacement> vaultDoors) {
        Map<Location, List<Gate>> teamGates = new ConcurrentHashMap<>();
        int gateCount = 0;
        for (GateGroup group : gateGroups) {
            Location leverLocation = group.getLeverLocation();
            if (!leverLocation.isWorldLoaded()) {
                plugin.getLogger().warning("Skipping gate group from segment " + group.getSegmentName()
                        + " for team " + teamId + ": lever world is not loaded.");
                continue;
            }

            List<Gate> gates = new ArrayList<>();
            for (Area bounds : group.getGateBounds()) {
                Gate gate = new Gate(plugin, teamId, bounds);
                // Templates carve the gated opening as an open hole, exactly as they do doorways, so
                // without this the gate exists only as a Java object and the passage stays walkable.
                gate.buildClosed();
                gates.add(gate);
                gateCount++;
            }
            placeLeverBlock(leverLocation);
            teamGates.put(leverLocation, gates);
        }
        gatesByTeamAndLeverLocation.put(teamId, teamGates);

        Map<VaultColor, List<VaultDoor>> teamVaultDoors = new ConcurrentHashMap<>();
        for (VaultDoorPlacement placement : vaultDoors) {
            Area bounds = placement.getBounds();
            if (!bounds.getMinPoint().isWorldLoaded()) {
                plugin.getLogger().warning("Skipping " + placement.getColor() + " vault door from segment "
                        + placement.getSegmentName() + " for team " + teamId + ": world is not loaded.");
                continue;
            }
            // The lock location is a cell of the wall itself: there is no keyhole, and buildClosed
            // stamps the lock material there, so it must be a block the wall already owns.
            VaultDoor door = new VaultDoor(plugin, teamId, bounds, bounds.getMinPoint().clone(),
                    placement.getColor());
            door.buildClosed();
            teamVaultDoors.computeIfAbsent(placement.getColor(), c -> new ArrayList<>()).add(door);
        }
        vaultDoorsByTeamAndColor.put(teamId, teamVaultDoors);

        plugin.getLogger().info("Built " + gateCount + " gate(s) on " + teamGates.size()
                + " lever(s) and " + vaultDoors.size() + " vault door(s) for team " + teamId);
    }

    /**
     * Writes the physical lever that opens a segment's gates.
     *
     * <p>The LEVER marker is recorded at the <em>air</em> cell next to the wall the builder clicked,
     * and the marker itself is a display entity that never reaches the schematic -- so after the paste
     * the cell is air, and air never fires {@code RIGHT_CLICK_BLOCK}. This is the same trap
     * {@code Door.buildClosed()} exists for.
     */
    private void placeLeverBlock(@NotNull Location leverLocation) {
        Block cell = leverLocation.getBlock();
        if (!cell.getType().isAir()) {
            plugin.getLogger().fine("Lever cell at " + leverLocation.toVector() + " held "
                    + cell.getType() + " rather than air; overwriting it with the lever.");
        }

        BlockFace support = findLeverSupport(cell);
        if (support == null) {
            cell.getRelative(BlockFace.DOWN).setType(LEVER_SUPPORT_MATERIAL, false);
            support = BlockFace.DOWN;
            plugin.getLogger().warning("Lever at " + leverLocation.toVector() + " had no solid neighbour;"
                    + " placed a support block beneath it so its gates can still be opened.");
        }

        // No physics on either write: this runs while the dungeon is still being built, and a physics
        // pass mid-build can pop the lever straight back off its support.
        cell.setType(Material.LEVER, false);
        BlockData data = cell.getBlockData();
        if (data instanceof Switch lever) {
            switch (support) {
                case DOWN -> {
                    lever.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
                    lever.setFacing(BlockFace.SOUTH);
                }
                case UP -> {
                    lever.setAttachedFace(FaceAttachable.AttachedFace.CEILING);
                    lever.setFacing(BlockFace.SOUTH);
                }
                // A wall lever faces away from the block it is bolted to.
                default -> {
                    lever.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                    lever.setFacing(support.getOppositeFace());
                }
            }
            cell.setBlockData(lever, false);
        } else {
            // A server whose LEVER block data is not a Switch still gets a clickable lever, just in
            // the default orientation -- which is all the gate logic actually needs.
            plugin.getLogger().fine("Lever block data was " + data.getClass().getSimpleName()
                    + ", not a Switch; leaving the default orientation.");
        }
    }

    /** The face of {@code cell} whose neighbour is solid, or null when the cell is free-floating. */
    @Nullable
    private static BlockFace findLeverSupport(@NotNull Block cell) {
        for (BlockFace face : LEVER_SUPPORT_ORDER) {
            if (cell.getRelative(face).getType().isSolid()) return face;
        }
        return null;
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
         Map<Location, Door> teamDoors = doorsByTeamAndLockLocation.remove(teamId);
         if (teamDoors != null) {
             // Stop any animation still running, or it keeps writing door blocks into a dungeon
             // region that end-of-round cleanup has already air-filled.
             for (Door door : teamDoors.values()) {
                 door.cancelAnimation();
             }
         }

         Map<Location, List<Gate>> teamGates = gatesByTeamAndLeverLocation.remove(teamId);
         if (teamGates != null) {
             for (List<Gate> gates : teamGates.values()) {
                 for (Gate gate : gates) gate.cancelAnimation();
             }
         }

         Map<VaultColor, List<VaultDoor>> teamVaultDoors = vaultDoorsByTeamAndColor.remove(teamId);
         if (teamVaultDoors != null) {
             for (List<VaultDoor> doors : teamVaultDoors.values()) {
                 for (VaultDoor door : doors) door.cancelAnimation();
             }
         }

         plugin.getLogger().info("Cleared door, gate and vault-door state for team: " + teamId);
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


    /**
     * The gates opened by the lever at a location, or null when no lever of this team's is there.
     * Compares block coordinates, like {@link #getDoorAt}, rather than trusting Location equality.
     */
    @Nullable
    private List<Gate> getGatesAtLever(UUID teamId, Location clickedLocation) {
        Map<Location, List<Gate>> teamGates = gatesByTeamAndLeverLocation.get(teamId);
        if (teamGates == null || clickedLocation == null || clickedLocation.getWorld() == null) {
            return null;
        }
        World world = clickedLocation.getWorld();
        int x = clickedLocation.getBlockX();
        int y = clickedLocation.getBlockY();
        int z = clickedLocation.getBlockZ();

        for (Map.Entry<Location, List<Gate>> entry : teamGates.entrySet()) {
            Location leverLoc = entry.getKey();
            if (world.equals(leverLoc.getWorld())
                    && leverLoc.getBlockX() == x
                    && leverLoc.getBlockY() == y
                    && leverLoc.getBlockZ() == z) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Pulls a lever, opening every gate on it. One-way: a lever that has already been pulled does
     * nothing.
     *
     * @return true if this pull opened at least one gate.
     */
    public boolean pullLever(@NotNull UUID teamId, @NotNull Location leverLocation, @Nullable Player puller) {
        List<Gate> gates = getGatesAtLever(teamId, leverLocation);
        if (gates == null) return false;

        int opened = 0;
        for (Gate gate : gates) {
            if (gate.open()) opened++;
        }
        if (opened == 0) return false;

        plugin.getLogger().fine((puller != null ? puller.getName() : "server") + " pulled the lever at "
                + leverLocation.toVector() + ", opening " + opened + " gate(s) for team " + teamId);
        return true;
    }

    /**
     * Opens a team's vault doors of one colour.
     *
     * <p>Called by {@link VaultManager} when it marks the matching vault open. The vault marker click
     * is VaultManager's alone (bug #65); the wall behind it is this manager's, which built it and owns
     * its animation task. There is no second keyhole and no second key -- opening the vault is what
     * opens its door.
     *
     * @return how many doors this opened; 0 when no segment carried a door of that colour.
     */
    public int openVaultDoors(@NotNull UUID teamId, @NotNull VaultColor color) {
        Map<VaultColor, List<VaultDoor>> teamDoors = vaultDoorsByTeamAndColor.get(teamId);
        if (teamDoors == null) return 0;
        List<VaultDoor> doors = teamDoors.get(color);
        if (doors == null) return 0;

        int opened = 0;
        for (VaultDoor door : doors) {
            if (door.open()) opened++;
        }
        return opened;
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
            handleDoorClick(event, player, door);
            return;
        }

        // A door lock is a keyhole block inside a doorway and a lever is a lever in a segment
        // interior, so the two can never be the same block -- but returning above keeps it that way
        // by construction rather than by coincidence.
        List<Gate> gates = getGatesAtLever(teamId, clickedLocation);
        if (gates != null) {
            handleLeverClick(event, player, teamId, clickedLocation);
        }
    }

    /** The existing rusty-key flow for a segment door the player clicked the keyhole of. */
    private void handleDoorClick(PlayerInteractEvent event, Player player, Door door) {
        event.setCancelled(true); // We are handling this interaction

        if (door.isOpen()) {
             player.sendMessage(Component.text("This door is already open.", NamedTextColor.YELLOW));
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
                }
            } else {
                 player.sendMessage(Component.text("Error: Could not use the key!", NamedTextColor.RED));
                 plugin.getLogger().warning("Failed to consume key from " + player.getName() + " for door " + door.getId() + " despite isCorrectKey being true.");
            }
        } else {
            player.sendMessage(Component.text("This door is locked. You need the correct key.", NamedTextColor.RED));
        }
    }

    /**
     * Pulls a segment's lever, or refuses a lever that has already been pulled.
     *
     * <p>The first pull is deliberately <em>not</em> cancelled: vanilla then flips the lever to
     * powered, so the world itself records that these gates are open and there is no second copy of
     * that state to drift. Every later click is cancelled, which is what stops the lever being flipped
     * back to off while it sits over an open gate.
     */
    private void handleLeverClick(PlayerInteractEvent event, Player player, UUID teamId, Location leverLocation) {
        if (pullLever(teamId, leverLocation, player)) {
            player.sendMessage(Component.text("The gates grind open!", NamedTextColor.GREEN));
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Component.text("This lever has already been pulled.", NamedTextColor.YELLOW));
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
        // The union of all three maps, not just the doors: a team can hold gates or a vault door
        // without holding a segment door, and an in-flight gate animation that outlives teardown
        // writes iron bars back into a region cleanupInstance() has already air-filled.
        Set<UUID> teams = new HashSet<>(doorsByTeamAndLockLocation.keySet());
        teams.addAll(gatesByTeamAndLeverLocation.keySet());
        teams.addAll(vaultDoorsByTeamAndColor.keySet());

        for (UUID teamId : teams) {
            clearTeamState(teamId); // Cancels in-flight animations as well as dropping the doors
        }
        doorsByTeamAndLockLocation.clear();
        gatesByTeamAndLeverLocation.clear();
        vaultDoorsByTeamAndColor.clear();
        plugin.getLogger().info("Cleared door states for " + teams.size() + " teams.");
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
