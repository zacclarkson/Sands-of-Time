package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;
import com.clarkson.sot.utils.EntryPoint; // Original EntryPoint with absolute Location

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
 * Represents an instance of a Segment template placed at a specific location in the world.
 * It holds a reference to the template and the world origin (minimum corner) location.
 * Provides methods to get absolute world coordinates for features.
 */
public class PlacedSegment {

    private final Segment segmentTemplate; // The world-independent template
    private final Location worldOrigin;    // The absolute world location of the template's min corner (origin)
    private final Area worldBounds;        // The calculated absolute world bounds

    /**
     * Creates an instance representing a placed segment template.
     *
     * @param segmentTemplate The world-independent Segment template.
     * @param worldOrigin     The absolute world location where the template's origin (minimum corner) should be placed.
     */
    public PlacedSegment(@NotNull Segment segmentTemplate, @NotNull Location worldOrigin) {
        this.segmentTemplate = Objects.requireNonNull(segmentTemplate, "Segment template cannot be null");
        this.worldOrigin = Objects.requireNonNull(worldOrigin, "World origin location cannot be null");
        Objects.requireNonNull(worldOrigin.getWorld(), "World origin location must have a valid world");

        // Calculate the absolute world bounds based on origin and template size
        BlockVector3 size = segmentTemplate.getSize();
        Location maxPoint = worldOrigin.clone().add(size.x() - 1, size.y() - 1, size.z() - 1);
        this.worldBounds = new Area(worldOrigin, maxPoint); // Assumes Area constructor takes min, max
    }

    // --- Getters ---

    @NotNull public Segment getSegmentTemplate() { return segmentTemplate; }
    @NotNull public Location getWorldOrigin() { return worldOrigin; }
    @NotNull public Area getWorldBounds() { return worldBounds; } // Provides absolute min/max Locations
    @NotNull public World getWorld() { return worldOrigin.getWorld(); } // Convenience getter

    // --- Delegated Getters (from template) ---

    @NotNull public String getName() { return segmentTemplate.getName(); }
    @Nullable public SegmentType getType() { return segmentTemplate.getType(); }
    public int getTotalCoins() { return segmentTemplate.getTotalCoins(); }
    @NotNull public String getSchematicFileName() { return segmentTemplate.getSchematicFileName(); }


    // --- Methods to get Absolute Locations ---

    /**
     * Calculates the absolute world location corresponding to a relative position within the template.
     * @param relativePosition The BlockVector3 position relative to the template's origin.
     * @return The corresponding absolute Location in the world.
     */
    @NotNull
    public Location getAbsoluteLocation(@NotNull BlockVector3 relativePosition) {
        Objects.requireNonNull(relativePosition, "Relative position cannot be null");
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
     * @return A new list of EntryPoint objects with absolute world locations.
     */
    @NotNull
    public List<EntryPoint> getAbsoluteEntryPoints() {
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
      * @param relativeSpawnPoints List of BlockVector3 relative positions from the template.
      * @return A new list of absolute world Locations.
      */
     @NotNull
     private List<Location> getAbsoluteSpawnLocations(@NotNull List<BlockVector3> relativeSpawnPoints) {
        return relativeSpawnPoints.stream()
                                  .map(this::getAbsoluteLocation) // Convert each relative vec to absolute loc
                                  .collect(Collectors.toList());
     }

     // Specific getters using the helper method
     @NotNull public List<Location> getAbsoluteSandSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getSandSpawnLocations()); }
     @NotNull public List<Location> getAbsoluteItemSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getItemSpawnLocations()); }
     @NotNull public List<Location> getAbsoluteCoinSpawnLocations() { return getAbsoluteSpawnLocations(segmentTemplate.getCoinSpawnLocations()); }


    @Override
    public String toString() {
        return "PlacedSegment{" +
                "name=" + getName() +
                ", template=" + segmentTemplate.getName() + // Avoid full template toString recursion
                ", worldOrigin=" + worldOrigin +
                ", worldBounds=" + worldBounds +
                '}';
    }
}
