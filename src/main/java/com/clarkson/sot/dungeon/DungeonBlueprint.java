package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.entities.Area; // Import the Area class
import org.bukkit.Location; // Needed for Area's internal representation
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Represents the complete blueprint of a dungeon layout, generated once.
 * Contains the list of segments with their relative origins, pre-calculated
 * relative locations for features (hub, vaults, keys, spawns), and the
 * overall relative bounding box (using Area) of the generated layout.
 * All locations/vectors are relative to a conceptual (0,0,0) origin.
 * Note: The Locations within the relativeBounds Area will have a null world.
 */
public class DungeonBlueprint {

    // List of segments with origins relative to the blueprint's 0,0,0
    private final List<PlacedSegment> relativeSegments;

    // Pre-calculated relative locations/vectors for features
    private final Vector hubRelativeLocation;
    private final Map<VaultColor, Vector> vaultMarkerRelativeLocations;
    private final Map<VaultColor, Vector> keySpawnRelativeLocations;
    private final List<Vector> sandSpawnRelativeLocations;
    private final List<Vector> coinSpawnRelativeLocations;
    private final List<Vector> itemSpawnRelativeLocations;

    // --- Changed: Use Area for Relative Bounding Box ---
    private final Area relativeBounds; // Represents bounds using relative Locations (null world)

    /**
     * Constructor - Typically called by DungeonGenerator after generation.
     * Takes lists/maps of relative locations/segments and the calculated relative Area bounds.
     */
    public DungeonBlueprint(@NotNull List<PlacedSegment> relativeSegments,
                            @NotNull Vector hubRelativeLocation,
                            @NotNull Map<VaultColor, Vector> vaultMarkerRelativeLocations,
                            @NotNull Map<VaultColor, Vector> keySpawnRelativeLocations,
                            @NotNull List<Vector> sandSpawnRelativeLocations,
                            @NotNull List<Vector> coinSpawnRelativeLocations,
                            @NotNull List<Vector> itemSpawnRelativeLocations,
                            @NotNull Area relativeBounds // Changed parameter
                           ) {

        // Validate inputs
        Objects.requireNonNull(relativeSegments, "relativeSegments cannot be null");
        Objects.requireNonNull(hubRelativeLocation, "hubRelativeLocation cannot be null");
        Objects.requireNonNull(vaultMarkerRelativeLocations, "vaultMarkerRelativeLocations cannot be null");
        Objects.requireNonNull(keySpawnRelativeLocations, "keySpawnRelativeLocations cannot be null");
        Objects.requireNonNull(sandSpawnRelativeLocations, "sandSpawnRelativeLocations cannot be null");
        Objects.requireNonNull(coinSpawnRelativeLocations, "coinSpawnRelativeLocations cannot be null");
        Objects.requireNonNull(itemSpawnRelativeLocations, "itemSpawnRelativeLocations cannot be null");
        Objects.requireNonNull(relativeBounds, "relativeBounds cannot be null");
        // Optional: Add check to ensure world is null in relativeBounds locations?
        // if (relativeBounds.getMinPoint().getWorld() != null || relativeBounds.getMaxPoint().getWorld() != null) {
        //     throw new IllegalArgumentException("relativeBounds Locations must have a null world for blueprint.");
        // }


        // Store immutable copies
        this.relativeSegments = Collections.unmodifiableList(new ArrayList<>(relativeSegments));
        this.hubRelativeLocation = hubRelativeLocation.clone();
        this.vaultMarkerRelativeLocations = Collections.unmodifiableMap(new HashMap<>(vaultMarkerRelativeLocations));
        this.keySpawnRelativeLocations = Collections.unmodifiableMap(new HashMap<>(keySpawnRelativeLocations));
        this.sandSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(sandSpawnRelativeLocations));
        this.coinSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(coinSpawnRelativeLocations));
        this.itemSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(itemSpawnRelativeLocations));
        this.relativeBounds = relativeBounds; // Store the Area object (Area itself is effectively immutable once constructed)
    }

    // --- Getters ---

    @NotNull public List<PlacedSegment> getRelativeSegments() { return relativeSegments; }
    @NotNull public Vector getHubRelativeLocation() { return hubRelativeLocation; }
    @NotNull public Map<VaultColor, Vector> getVaultMarkerRelativeLocations() { return vaultMarkerRelativeLocations; }
    @NotNull public Map<VaultColor, Vector> getKeySpawnRelativeLocations() { return keySpawnRelativeLocations; }
    @NotNull public List<Vector> getSandSpawnRelativeLocations() { return sandSpawnRelativeLocations; }
    @NotNull public List<Vector> getCoinSpawnRelativeLocations() { return coinSpawnRelativeLocations; }
    @NotNull public List<Vector> getItemSpawnRelativeLocations() { return itemSpawnRelativeLocations; }

    // --- Changed: Getter for Relative Bounds ---
    /**
     * Gets the relative bounding box of the blueprint.
     * Note: The Locations within this Area have a null world.
     * @return The Area representing the relative bounds.
     */
    @NotNull public Area getRelativeBounds() {
        // Return the Area object directly. Consider if cloning is needed, though Area is mostly immutable.
        return relativeBounds;
    }

    // Removed getRelativeMinBounds() and getRelativeMaxBounds()

    /**
     * Calculates the size of the blueprint's bounding box using the Area object.
     * @return A Vector representing the size (width, height, length).
     */
    @NotNull public Vector getBlueprintSize() {
        // Area class already calculates width/height/depth accurately (max - min)
        // Note: Area's width/height/depth might not exactly match maxCoord-minCoord+1 depending on its constructor logic.
        // Let's calculate from min/max points for consistency with previous Vector method.
        Location min = relativeBounds.getMinPoint();
        Location max = relativeBounds.getMaxPoint();
        // Add 1 because bounds are inclusive (max - min + 1 block)
        return new Vector(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        // Or return new Vector(relativeBounds.getWidth() + 1, relativeBounds.getHeight() + 1, relativeBounds.getDepth() + 1); if Area calculates size correctly.
    }

}
