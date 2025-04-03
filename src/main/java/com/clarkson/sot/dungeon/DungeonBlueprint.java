package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment; // Use PlacedSegment with relative origins
import org.bukkit.util.Vector; // Use Vector for relative locations

import java.util.*;

/**
 * Represents the complete blueprint of a dungeon layout, generated once.
 * Contains the list of segments with their relative origins and pre-calculated
 * relative locations for all important features (hub, vaults, keys, spawns).
 * All locations/vectors are relative to a conceptual (0,0,0) origin.
 */
public class DungeonBlueprint {

    // List of segments with origins relative to the blueprint's 0,0,0
    private final List<PlacedSegment> relativeSegments;

    // Pre-calculated relative locations/vectors
    private final Vector hubRelativeLocation; // Relative location of the hub segment's origin
    private final Map<VaultColor, Vector> vaultMarkerRelativeLocations;
    private final Map<VaultColor, Vector> keySpawnRelativeLocations;
    private final List<Vector> sandSpawnRelativeLocations;
    private final List<Vector> coinSpawnRelativeLocations;
    private final List<Vector> itemSpawnRelativeLocations;
    // Optional: Add overall relative bounds, puzzle locations, etc.

    /**
     * Constructor - Typically called by DungeonGenerator after generation.
     * Takes lists/maps of relative locations/segments.
     */
    public DungeonBlueprint(List<PlacedSegment> relativeSegments,
                            Vector hubRelativeLocation,
                            Map<VaultColor, Vector> vaultMarkerRelativeLocations,
                            Map<VaultColor, Vector> keySpawnRelativeLocations,
                            List<Vector> sandSpawnRelativeLocations,
                            List<Vector> coinSpawnRelativeLocations,
                            List<Vector> itemSpawnRelativeLocations) {

        // Store immutable copies
        this.relativeSegments = Collections.unmodifiableList(new ArrayList<>(relativeSegments));
        this.hubRelativeLocation = hubRelativeLocation; // Can be null if hub not found
        this.vaultMarkerRelativeLocations = Collections.unmodifiableMap(new HashMap<>(vaultMarkerRelativeLocations));
        this.keySpawnRelativeLocations = Collections.unmodifiableMap(new HashMap<>(keySpawnRelativeLocations));
        this.sandSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(sandSpawnRelativeLocations));
        this.coinSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(coinSpawnRelativeLocations));
        this.itemSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(itemSpawnRelativeLocations));
    }

    // --- Getters ---

    public List<PlacedSegment> getRelativeSegments() {
        return relativeSegments;
    }

    public Vector getHubRelativeLocation() {
        return hubRelativeLocation;
    }

    public Map<VaultColor, Vector> getVaultMarkerRelativeLocations() {
        return vaultMarkerRelativeLocations;
    }

    public Map<VaultColor, Vector> getKeySpawnRelativeLocations() {
        return keySpawnRelativeLocations;
    }

    public List<Vector> getSandSpawnRelativeLocations() {
        return sandSpawnRelativeLocations;
    }

    public List<Vector> getCoinSpawnRelativeLocations() {
        return coinSpawnRelativeLocations;
    }

    public List<Vector> getItemSpawnRelativeLocations() {
        return itemSpawnRelativeLocations;
    }

    // Add getters for any other stored blueprint data (bounds, etc.)
}
