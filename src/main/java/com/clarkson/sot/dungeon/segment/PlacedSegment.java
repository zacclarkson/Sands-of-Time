package com.clarkson.sot.dungeon.segment;

// Import necessary classes
import com.clarkson.sot.entities.Area;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents an instance of a Segment template placed at a specific location.
 * It holds a reference to the template, the world origin (minimum corner) location,
 * and the depth of the segment relative to the dungeon hub.
 * Provides methods to get absolute world coordinates for features.
 */
public class PlacedSegment {

    private final Segment segmentTemplate; // The world-independent template
    private final Location worldOrigin;    // The absolute world location OR relative blueprint origin
    private final Area worldBounds;        // The calculated absolute world bounds OR relative blueprint bounds
    private final int depth;               // Depth from the hub (Hub = 0)

    /**
     * Creates an instance representing a placed segment template.
     *
     * @param segmentTemplate The world-independent Segment template.
     * @param worldOrigin     The absolute world location OR relative blueprint origin (min corner) where the template is placed.
     * @param depth           The depth of this segment from the dungeon hub (Hub is depth 0).
     */
    public PlacedSegment(@NotNull Segment segmentTemplate, @NotNull Location worldOrigin, int depth) {
        this.segmentTemplate = Objects.requireNonNull(segmentTemplate, "Segment template cannot be null");
        this.worldOrigin = Objects.requireNonNull(worldOrigin, "World origin location cannot be null");
        // World can be null for blueprint placement, so remove the null check on worldOrigin.getWorld() for blueprint stage.
        // Objects.requireNonNull(worldOrigin.getWorld(), "World origin location must have a valid world for absolute placement");

        this.depth = depth; // Assign depth

        // Calculate the bounds based on origin and template size
        BlockVector3 size = segmentTemplate.getSize();
        // Use clone() defensively. Subtract 1 because size includes the origin block.
        Location maxPoint = worldOrigin.clone().add(size.x() - 1, size.y() - 1, size.z() - 1);
        this.worldBounds = new Area(worldOrigin, maxPoint); // Assumes Area constructor takes min, max
    }

    // --- Getters ---

    @NotNull public Segment getSegmentTemplate() { return segmentTemplate; }
    @NotNull public Location getWorldOrigin() { return worldOrigin; }
    @NotNull public Area getWorldBounds() { return worldBounds; }
    @Nullable public World getWorld() { return worldOrigin.getWorld(); } // Can be null in blueprint stage
    public int getDepth() { return depth; } // Getter for depth

    // --- Delegated Getters (from template) ---

    @NotNull public String getName() { return segmentTemplate.getName(); }
    @Nullable public SegmentType getType() { return segmentTemplate.getType(); }
    public int getTotalCoins() { return segmentTemplate.getTotalCoins(); }
    @NotNull public String getSchematicFileName() { return segmentTemplate.getSchematicFileName(); }


    // --- Methods to get Absolute Locations ---

    /**
     * Calculates the absolute world location corresponding to a relative position within the template.
     * Requires that this PlacedSegment represents an actual world placement (worldOrigin has a valid world).
     *
     * @param relativePosition The BlockVector3 position relative to the template's origin.
     * @return The corresponding absolute Location in the world.
     * @throws IllegalStateException if the worldOrigin does not have a valid world set.
     */
    @NotNull
    public Location getAbsoluteLocation(@NotNull BlockVector3 relativePosition) {
        Objects.requireNonNull(relativePosition, "Relative position cannot be null");
        if (worldOrigin.getWorld() == null) {
            throw new IllegalStateException("Cannot calculate absolute location when worldOrigin's world is null (likely blueprint stage).");
        }
        // Note: Uses BlockVector3's integer coords for addition
        return worldOrigin.clone().add(
            relativePosition.x(),
            relativePosition.y(),
            relativePosition.z()
        );
    }

    /**
     * Calculates the absolute world locations for all entry points defined in the template.
     * Returns EntryPoint objects with absolute Locations.
     * Requires that this PlacedSegment represents an actual world placement.
     *
     * @return A new list of EntryPoint objects with absolute world locations.
     * @throws IllegalStateException if the worldOrigin does not have a valid world set.
     */
    @NotNull
    public List<EntryPoint> getAbsoluteEntryPoints() {
         if (worldOrigin.getWorld() == null) {
             throw new IllegalStateException("Cannot calculate absolute entry points when worldOrigin's world is null.");
         }
        List<EntryPoint> absoluteEntryPoints = new ArrayList<>();
        for (Segment.RelativeEntryPoint relEp : segmentTemplate.getEntryPoints()) {
            Location absLoc = getAbsoluteLocation(relEp.getRelativePosition());
            // Assuming original EntryPoint constructor takes Location and Direction
            absoluteEntryPoints.add(new EntryPoint(absLoc, relEp.getDirection()));
        }
        return absoluteEntryPoints;
    }

     /**
      * Calculates the absolute world locations for a list of relative spawn points.
      * Requires that this PlacedSegment represents an actual world placement.
      *
      * @param relativeSpawnPoints List of BlockVector3 relative positions from the template.
      * @return A new list of absolute world Locations.
      * @throws IllegalStateException if the worldOrigin does not have a valid world set.
      */
     @NotNull
     private List<Location> getAbsoluteSpawnLocations(@NotNull List<BlockVector3> relativeSpawnPoints) {
         if (worldOrigin.getWorld() == null) {
             throw new IllegalStateException("Cannot calculate absolute spawn locations when worldOrigin's world is null.");
         }
        return relativeSpawnPoints.stream()
                                  .map(this::getAbsoluteLocation) // Convert each relative vec to absolute loc
                                  .collect(Collectors.toList());
     }

     // Specific getters using the helper method
     // These will throw an IllegalStateException if called during the blueprint stage
     @NotNull public List<Location> getAbsoluteSandSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getSandSpawnLocations()); }
     @NotNull public List<Location> getAbsoluteItemSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getItemSpawnLocations()); }
     @NotNull public List<Location> getAbsoluteCoinSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getCoinSpawnLocations()); }


    @Override
    public String toString() {
        return "PlacedSegment{" +
                "name=" + getName() +
                ", template=" + segmentTemplate.getName() +
                ", origin=" + worldOrigin.toVector() + // Use vector for concise representation
                (worldOrigin.getWorld() != null ? " (World: " + worldOrigin.getWorld().getName() + ")" : " (Relative)") +
                ", depth=" + depth + // Added depth
                ", boundsMin=" + worldBounds.getMinPoint().toVector() + // Use vector
                ", boundsMax=" + worldBounds.getMaxPoint().toVector() + // Use vector
                '}';
    }
}
