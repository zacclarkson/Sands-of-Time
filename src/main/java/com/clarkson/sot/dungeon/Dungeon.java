package com.clarkson.sot.dungeon;

// Removed PlacedSegment import as it's no longer stored here
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // For hubLocation potentially

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
    private final Location safeExitLocation; // Null if no segment defined a safe exit marker
    private final Map<VaultColor, Location> vaultMarkerLocations;
    private final Map<VaultColor, Location> keySpawnLocations;
    private final List<Location> sandSpawnLocations;
    private final List<Location> coinSpawnLocations;
    private final List<Location> itemSpawnLocations;
    private final List<DeathCage> deathCages;

    /**
     * Constructor for the Dungeon data object.
     * Should be called by the DungeonManager instance after calculating absolute locations.
     *
     * @param teamId The ID of the team this dungeon belongs to.
     * @param world The world this instance resides in.
     * @param origin The absolute world origin of this instance.
     * @param blueprint The blueprint used to generate this dungeon.
     * @param hubLocation The absolute location of the hub within this instance.
     * @param safeExitLocation The absolute location escaping players are teleported to, or null if undefined.
     * @param vaultMarkerLocations Map of vault colors to their absolute marker locations.
     * @param keySpawnLocations Map of vault colors to their absolute key spawn locations.
     * @param sandSpawnLocations List of absolute sand spawn locations.
     * @param coinSpawnLocations List of absolute coin spawn locations.
     * @param itemSpawnLocations List of absolute general item spawn locations.
     * @param deathCages List of death cage + sacrifice point pairs (max 4).
     */
    public Dungeon(@NotNull UUID teamId, @NotNull World world, @NotNull Location origin, @NotNull DungeonBlueprint blueprint,
                   @Nullable Location hubLocation,
                   @Nullable Location safeExitLocation,
                   @NotNull Map<VaultColor, Location> vaultMarkerLocations,
                   @NotNull Map<VaultColor, Location> keySpawnLocations,
                   @NotNull List<Location> sandSpawnLocations,
                   @NotNull List<Location> coinSpawnLocations,
                   @NotNull List<Location> itemSpawnLocations,
                   @NotNull List<DeathCage> deathCages) {

        this.instanceId = UUID.randomUUID();
        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.world = Objects.requireNonNull(world, "Dungeon world cannot be null");
        this.origin = Objects.requireNonNull(origin, "Dungeon origin cannot be null");
        this.blueprint = Objects.requireNonNull(blueprint, "Blueprint cannot be null");
        this.hubLocation = hubLocation;
        this.safeExitLocation = safeExitLocation;

        // Store immutable copies of maps/lists containing ABSOLUTE locations
        this.vaultMarkerLocations = Collections.unmodifiableMap(new HashMap<>(vaultMarkerLocations));
        this.keySpawnLocations = Collections.unmodifiableMap(new HashMap<>(keySpawnLocations));
        this.sandSpawnLocations = Collections.unmodifiableList(new ArrayList<>(sandSpawnLocations));
        this.coinSpawnLocations = Collections.unmodifiableList(new ArrayList<>(coinSpawnLocations));
        this.itemSpawnLocations = Collections.unmodifiableList(new ArrayList<>(itemSpawnLocations));
        this.deathCages = Collections.unmodifiableList(new ArrayList<>(deathCages));
    }

    // --- Getters ---

    @NotNull public UUID getInstanceId() { return instanceId; }
    @NotNull public UUID getTeamId() { return teamId; }
    @NotNull public Location getOrigin() { return origin.clone(); } // Clone for safety
    @NotNull public World getWorld() { return world; }
    @NotNull public DungeonBlueprint getBlueprintData() { return blueprint; } // Allow access to original blueprint if needed
    @Nullable public Location getHubLocation() { return hubLocation != null ? hubLocation.clone() : null; }

    /**
     * Gets the absolute location escaping players are teleported to in this instance.
     * Null when no segment in the layout carried a safe exit marker; callers should
     * fall back to the hub in that case.
     */
    @Nullable public Location getSafeExitLocation() { return safeExitLocation != null ? safeExitLocation.clone() : null; }
    @NotNull public Map<VaultColor, Location> getVaultMarkerLocations() { return vaultMarkerLocations; } // Already unmodifiable
    @NotNull public Map<VaultColor, Location> getKeySpawnLocations() { return keySpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getSandSpawnLocations() { return sandSpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getCoinSpawnLocations() { return coinSpawnLocations; } // Already unmodifiable
    @NotNull public List<Location> getItemSpawnLocations() { return itemSpawnLocations; } // Already unmodifiable

    @NotNull public List<DeathCage> getDeathCages() {
        return deathCages; // Already unmodifiable
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