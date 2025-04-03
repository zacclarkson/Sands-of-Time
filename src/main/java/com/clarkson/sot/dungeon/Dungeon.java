package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * Represents the consolidated data and important locations for a specific
 * team's fully generated and initialized dungeon instance.
 * This object is created by the DungeonManager instance after pasting and feature placement.
 */
public class Dungeon {

    private final UUID teamId;
    private final Location origin; // Absolute world origin of this dungeon instance
    private final World world;
    private final List<PlacedSegment> placedSegments; // Reference to the actual segments placed

    // Consolidated locations within this specific instance
    private final Location hubLocation;
    private final Map<VaultColor, Location> vaultMarkerLocations;
    private final Map<VaultColor, Location> keySpawnLocations; // Assuming keys are placed at specific spots
    private final List<Location> sandSpawnLocations;
    private final List<Location> coinSpawnLocations;
    private final List<Location> itemSpawnLocations;
    // Add other relevant locations if needed (e.g., puzzle elements, sacrifice points)
    // private final Map<String, Location> puzzleLocations;

    /**
     * Constructor for the Dungeon data object.
     * Should be called by the DungeonManager instance after segments are pasted
     * and features (vaults, keys) are placed.
     *
     * @param teamId The ID of the team this dungeon belongs to.
     * @param origin The absolute world origin of this instance.
     * @param world The world this instance resides in.
     * @param placedSegments The list of PlacedSegment instances making up this dungeon.
     * @param hubLocation The absolute location of the hub within this instance.
     * @param vaultMarkerLocations Map of vault colors to their absolute marker locations.
     * @param keySpawnLocations Map of vault colors to their absolute key spawn locations.
     * @param sandSpawnLocations List of absolute sand spawn locations.
     * @param coinSpawnLocations List of absolute coin spawn locations.
     * @param itemSpawnLocations List of absolute general item spawn locations.
     */
    public Dungeon(UUID teamId, Location origin, World world,
                   List<PlacedSegment> placedSegments,
                   Location hubLocation,
                   Map<VaultColor, Location> vaultMarkerLocations,
                   Map<VaultColor, Location> keySpawnLocations,
                   List<Location> sandSpawnLocations,
                   List<Location> coinSpawnLocations,
                   List<Location> itemSpawnLocations) {

        this.teamId = Objects.requireNonNull(teamId, "Team ID cannot be null");
        this.origin = Objects.requireNonNull(origin, "Dungeon origin cannot be null");
        this.world = Objects.requireNonNull(world, "Dungeon world cannot be null");
        // Store an immutable copy of the segment list
        this.placedSegments = Collections.unmodifiableList(new ArrayList<>(placedSegments));
        this.hubLocation = hubLocation; // Can be null if hub not found
        // Store immutable copies of maps/lists
        this.vaultMarkerLocations = Collections.unmodifiableMap(new HashMap<>(vaultMarkerLocations));
        this.keySpawnLocations = Collections.unmodifiableMap(new HashMap<>(keySpawnLocations));
        this.sandSpawnLocations = Collections.unmodifiableList(new ArrayList<>(sandSpawnLocations));
        this.coinSpawnLocations = Collections.unmodifiableList(new ArrayList<>(coinSpawnLocations));
        this.itemSpawnLocations = Collections.unmodifiableList(new ArrayList<>(itemSpawnLocations));
    }

    // --- Getters ---

    public UUID getTeamId() { return teamId; }
    public Location getOrigin() { return origin; }
    public World getWorld() { return world; }
    public List<PlacedSegment> getPlacedSegments() { return placedSegments; }
    public Location getHubLocation() { return hubLocation; } // Might be null
    public Map<VaultColor, Location> getVaultMarkerLocations() { return vaultMarkerLocations; }
    public Map<VaultColor, Location> getKeySpawnLocations() { return keySpawnLocations; }
    public List<Location> getSandSpawnLocations() { return sandSpawnLocations; }
    public List<Location> getCoinSpawnLocations() { return coinSpawnLocations; }
    public List<Location> getItemSpawnLocations() { return itemSpawnLocations; }

    // --- Helper Methods (Examples) ---

    /**
     * Checks if the given absolute world location corresponds to a known vault marker location
     * within this dungeon instance.
     * @param location The absolute world location to check.
     * @return The VaultColor if it's a vault location, otherwise null.
     */
    public VaultColor getVaultColorAt(Location location) {
        if (!world.equals(location.getWorld())) return null; // Check world first
        // Efficiently check against the pre-calculated map
        for (Map.Entry<VaultColor, Location> entry : vaultMarkerLocations.entrySet()) {
            // Compare block coordinates for robustness
            if (entry.getValue().getBlockX() == location.getBlockX() &&
                entry.getValue().getBlockY() == location.getBlockY() &&
                entry.getValue().getBlockZ() == location.getBlockZ()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Finds the PlacedSegment instance that contains the given world location.
     * (This might duplicate logic from DungeonManager, decide where it best fits)
     * @param location The absolute world location.
     * @return The PlacedSegment containing the location, or null if not found.
     */
    public PlacedSegment getSegmentAt(Location location) {
        if (!world.equals(location.getWorld())) return null;
        for (PlacedSegment segment : placedSegments) {
             // Assumes Area class has a suitable contains method
             if (segment.getWorldBounds().contains(location)) {
                 return segment;
             }
        }
        return null;
    }

}
