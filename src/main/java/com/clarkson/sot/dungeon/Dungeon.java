package com.clarkson.sot.dungeon;

// Removed PlacedSegment import as it's no longer stored here
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // For hubLocation potentially

import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.dungeon.segment.PlacedSegment;

import java.util.*;

/**
 * Represents the consolidated data and important ABSOLUTE locations for a specific
 * team's fully generated and initialized dungeon instance.
 * This object is created by the DungeonManager instance after calculating absolute positions.
 */
public class Dungeon {

    private final UUID instanceId; // Unique ID for this specific dungeon instance run
    private final UUID teamId;
    private final Location origin; // Absolute world origin of this dungeon instance
    private final World world;
    private final DungeonBlueprint blueprint; // Keep reference to the blueprint used

    // Consolidated ABSOLUTE locations within this specific instance
    private final Location hubLocation; // Can be null if blueprint had no hub? (Shouldn't happen)
    private final Map<VaultColor, Location> vaultMarkerLocations;
    private final Map<VaultColor, Location> keySpawnLocations;
    private final List<Location> sandSpawnLocations;
    private final List<Location> coinSpawnLocations;
    private final List<Location> itemSpawnLocations;
    private final List<DeathCage> deathCages;
    private final Location safeExitLocation; // Null when no segment template defined one
    private final Location bankLocation; // Cell holding the coin bank; null when no BANK marker was defined
    private final List<Location> playerSpawnLocations; // Absolute per-player start points (may be empty)
    private final List<Location> sandTimerLocations; // Absolute sand deposit cells (may be empty)
    private final List<Location> mobSpawnerLocations; // Absolute mob spawner cells (may be empty)
    private final List<Location> sandTradeLocations; // Absolute sand trade chests (may be empty)

    // Absolute openings between segments. Doorways get a rusty-key door; unused openings are the
    // entry points generation never attached a neighbour to and are sealed as plain wall.
    private final List<EntryPoint> doorways;
    private final List<EntryPoint> unusedOpenings;

    /**
     * Constructor for the Dungeon data object.
     * Should be called by the DungeonManager instance after calculating absolute locations.
     *
     * @param teamId The ID of the team this dungeon belongs to.
     * @param world The world this instance resides in.
     * @param origin The absolute world origin of this instance.
     * @param blueprint The blueprint used to generate this dungeon.
     * @param hubLocation The absolute location of the hub within this instance.
     * @param vaultMarkerLocations Map of vault colors to their absolute marker locations.
     * @param keySpawnLocations Map of vault colors to their absolute key spawn locations.
     * @param sandSpawnLocations List of absolute sand spawn locations.
     * @param coinSpawnLocations List of absolute coin spawn locations.
     * @param itemSpawnLocations List of absolute general item spawn locations.
     * @param deathCages List of death cage + sacrifice point pairs (max 4).
     * @param safeExitLocation The absolute location players interact with to escape, or null if undefined.
     * @param bankLocation The absolute cell the coin bank stands in, or null if no BANK marker was defined.
     * @param playerSpawnLocations List of absolute per-player spawn points (may be empty).
     * @param sandTimerLocations List of absolute cells where carried sand is deposited onto the timer.
     * @param mobSpawnerLocations List of absolute cells where hostile mobs are armed to spawn.
     * @param sandTradeLocations List of absolute cells holding a sand trade chest (may be empty).
     * @param doorways Absolute doorways between connected segments (one rusty-key door each).
     * @param unusedOpenings Absolute entry points with no neighbour attached, to be sealed.
     */
    public Dungeon(@NotNull UUID teamId, @NotNull World world, @NotNull Location origin, @NotNull DungeonBlueprint blueprint,
                   @Nullable Location hubLocation,
                   @NotNull Map<VaultColor, Location> vaultMarkerLocations,
                   @NotNull Map<VaultColor, Location> keySpawnLocations,
                   @NotNull List<Location> sandSpawnLocations,
                   @NotNull List<Location> coinSpawnLocations,
                   @NotNull List<Location> itemSpawnLocations,
                   @NotNull List<DeathCage> deathCages,
                   @Nullable Location safeExitLocation,
                   @Nullable Location bankLocation,
                   @NotNull List<Location> playerSpawnLocations,
                   @NotNull List<Location> sandTimerLocations,
                   @NotNull List<Location> mobSpawnerLocations,
                   @NotNull List<Location> sandTradeLocations,
                   @NotNull List<EntryPoint> doorways,
                   @NotNull List<EntryPoint> unusedOpenings) {

        this.instanceId = UUID.randomUUID();
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.world = Objects.requireNonNull(world, "Dungeon world cannot be null");
        this.origin = Objects.requireNonNull(origin, "Dungeon origin cannot be null");
        this.blueprint = Objects.requireNonNull(blueprint, "Blueprint cannot be null");
        this.hubLocation = hubLocation;
        this.safeExitLocation = safeExitLocation;
        this.bankLocation = bankLocation;

        // Store immutable copies of maps/lists containing ABSOLUTE locations
        this.vaultMarkerLocations = Collections.unmodifiableMap(new HashMap<>(vaultMarkerLocations));
        this.keySpawnLocations = Collections.unmodifiableMap(new HashMap<>(keySpawnLocations));
        this.sandSpawnLocations = Collections.unmodifiableList(new ArrayList<>(sandSpawnLocations));
        this.coinSpawnLocations = Collections.unmodifiableList(new ArrayList<>(coinSpawnLocations));
        this.itemSpawnLocations = Collections.unmodifiableList(new ArrayList<>(itemSpawnLocations));
        this.deathCages = Collections.unmodifiableList(new ArrayList<>(deathCages));
        this.playerSpawnLocations = Collections.unmodifiableList(new ArrayList<>(playerSpawnLocations));
        this.sandTimerLocations = Collections.unmodifiableList(new ArrayList<>(sandTimerLocations));
        this.mobSpawnerLocations = Collections.unmodifiableList(new ArrayList<>(mobSpawnerLocations));
        this.sandTradeLocations = Collections.unmodifiableList(new ArrayList<>(sandTradeLocations));
        this.doorways = Collections.unmodifiableList(new ArrayList<>(doorways));
        this.unusedOpenings = Collections.unmodifiableList(new ArrayList<>(unusedOpenings));
    }

    // --- Getters ---

    @NotNull public UUID getInstanceId() { return instanceId; }
    @NotNull public UUID getTeamId() { return teamId; }
    @NotNull public Location getOrigin() { return origin.clone(); } // Clone for safety
    @NotNull public World getWorld() { return world; }
    @NotNull public DungeonBlueprint getBlueprintData() { return blueprint; } // Allow access to original blueprint if needed
    @Nullable public Location getHubLocation() { return hubLocation != null ? hubLocation.clone() : null; }
    @NotNull public Map<VaultColor, Location> getVaultMarkerLocations() { return vaultMarkerLocations; } // Already unmodifiable
    @NotNull public Map<VaultColor, Location> getKeySpawnLocations() { return keySpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getSandSpawnLocations() { return sandSpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getCoinSpawnLocations() { return coinSpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getItemSpawnLocations() { return itemSpawnLocations; } // Already unmodifiable

    @NotNull public List<DeathCage> getDeathCages() {
        return deathCages; // Already unmodifiable
    }

    /** Absolute doorways between connected segments; DoorManager builds a rusty-key door at each. */
    @NotNull public List<EntryPoint> getDoorways() { return doorways; }

    /** Absolute entry points with no neighbour attached; DoorManager seals these as plain wall. */
    @NotNull public List<EntryPoint> getUnusedOpenings() { return unusedOpenings; }

    /** Absolute per-player spawn points (empty if no PLAYER_SPAWN markers were defined). */
    @NotNull public List<Location> getPlayerSpawnLocations() { return playerSpawnLocations; } // Already unmodifiable

    /** Absolute sand deposit cells (empty if no TIMER_DEPOSIT markers were defined). */
    @NotNull public List<Location> getSandTimerLocations() { return sandTimerLocations; } // Already unmodifiable

    /**
     * Absolute mob spawner cells (empty if no MOB_SPAWNER markers were defined).
     *
     * <p>These are only <em>armed</em> at instance setup — {@code MobManager} spawns the mobs when
     * a member of the owning team first comes near.
     */
    @NotNull public List<Location> getMobSpawnerLocations() { return mobSpawnerLocations; } // Already unmodifiable

    /**
     * Absolute sand trade cells (empty if no segment template defined a {@code SAND_TRADE} marker).
     *
     * <p>A chest is built at each of these; right-clicking one buys depth-scaled coins for a sand.
     */
    @NotNull public List<Location> getSandTradeLocations() { return sandTradeLocations; } // Already unmodifiable

    /**
     * True if the given block location is one of this instance's sand trade chests.
     *
     * <p>Matched on exact block coordinates, with no tolerance, for the same reason as
     * {@link #isBankAt}: the builder tool records a {@code SAND_TRADE} marker at the <em>air cell</em>
     * next to the face the builder clicked, which is precisely the cell the chest is written into.
     */
    public boolean isSandTradePointAt(@NotNull Location location) {
        for (Location trade : sandTradeLocations) {
            if (trade.getBlockX() == location.getBlockX()
                    && trade.getBlockY() == location.getBlockY()
                    && trade.getBlockZ() == location.getBlockZ()
                    && Objects.equals(trade.getWorld(), location.getWorld())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the given block location is one of this instance's sand deposit cells.
     *
     * <p>Matched on exact block coordinates, with no tolerance. The builder tool records a
     * TIMER_DEPOSIT marker at the <em>air cell</em> next to the face the builder clicked, which is
     * precisely the cell a placed sand block occupies — so unlike the safe exit (which compares a
     * clicked solid block against an air-cell marker and therefore needs a +/-1 Y allowance) a deposit
     * is an exact match.
     */
    public boolean isSandTimerDepositAt(@NotNull Location location) {
        for (Location deposit : sandTimerLocations) {
            if (deposit.getBlockX() == location.getBlockX()
                    && deposit.getBlockY() == location.getBlockY()
                    && deposit.getBlockZ() == location.getBlockZ()
                    && Objects.equals(deposit.getWorld(), location.getWorld())) {
                return true;
            }
        }
        return false;
    }

    /** Absolute cell the coin bank stands in, or null if no segment template defined a BANK marker. */
    @Nullable public Location getBankLocation() { return bankLocation != null ? bankLocation.clone() : null; }

    /**
     * True if the given block location is this instance's coin bank.
     *
     * <p>Matched on exact block coordinates, with no tolerance, for the same reason as
     * {@link #isSandTimerDepositAt}: the builder tool records a BANK marker at the <em>air cell</em>
     * next to the face the builder clicked, which is precisely the cell the bank block is written into.
     */
    public boolean isBankAt(@NotNull Location location) {
        return bankLocation != null
                && bankLocation.getBlockX() == location.getBlockX()
                && bankLocation.getBlockY() == location.getBlockY()
                && bankLocation.getBlockZ() == location.getBlockZ()
                && Objects.equals(bankLocation.getWorld(), location.getWorld());
    }

    /**
     * Gets the absolute safe exit location, or null if no segment template defined one.
     */
    @Nullable public Location getSafeExitLocation() {
        return safeExitLocation != null ? safeExitLocation.clone() : null;
    }

    /**
     * Checks if the given location is this instance's safe exit block (block coordinates).
     * Always false when this dungeon has no safe exit defined.
     */
    public boolean isSafeExitAt(@NotNull Location location) {
        return safeExitLocation != null
            && safeExitLocation.getBlockX() == location.getBlockX()
            && safeExitLocation.getBlockY() == location.getBlockY()
            && safeExitLocation.getBlockZ() == location.getBlockZ()
            && Objects.equals(safeExitLocation.getWorld(), location.getWorld());
    }

    /**
     * Helper method to get the depth associated with a segment containing a specific location.
     * Requires iterating through the PlacedSegments managed by DungeonManager.
     * NOTE: Consider if this logic better belongs in DungeonManager.
     * @param location Absolute world location.
     * @param placedSegments The list of segments placed in the world for this instance.
     * @return The depth of the segment, or 0 if not found.
     */
    public int getDepthAtLocation(@NotNull Location location, @NotNull List<PlacedSegment> placedSegments) {
         if (!world.equals(location.getWorld())) return 0;
         for (PlacedSegment segment : placedSegments) {
             // Assumes Area class has a suitable contains method
             if (segment.getWorldBounds().contains(location)) {
                 return segment.getDepth();
             }
         }
         return 0; // Default depth if outside known segments
     }

}