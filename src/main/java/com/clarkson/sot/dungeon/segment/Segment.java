package com.clarkson.sot.dungeon.segment;

import com.clarkson.sot.dungeon.DungeonManager;
import com.clarkson.sot.dungeon.SegmentType;
import com.clarkson.sot.dungeon.DungeonManager.VaultType;
import com.clarkson.sot.utils.Direction; // Assuming your Direction enum/class
// import com.clarkson.sot.utils.EntryPoint; // We might need a modified EntryPoint, see below
import com.sk89q.worldedit.math.BlockVector3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // Optional, for clarity

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a world-independent template or blueprint for a dungeon segment.
 * Stores metadata, relative locations of features (entry points, spawns),
 * dimensions, and a link to the schematic file containing block data.
 * All positions are relative to the segment's conceptual origin (usually its minimum corner).
 */
public class Segment {

    private final String name;
    private final SegmentType type;
    private final String schematicFileName; // Link to the .schem file name

    // Dimensions of the segment
    private final BlockVector3 size; // Width(X), Height(Y), Length(Z)

    // Features stored with relative positions (offsets from the conceptual origin)
    private final List<RelativeEntryPoint> entryPoints; // Use a modified EntryPoint class
    private final List<BlockVector3> sandSpawnLocations;
    private final List<BlockVector3> itemSpawnLocations;
    private final List<BlockVector3> coinSpawnLocations;
    private final int totalCoins;

    /**
     * Constructor for creating a Segment template.
     * It's assumed the relative positions and size have already been calculated
     * (e.g., by a builder or factory method that scanned an in-world structure).
     *
     * @param name              The unique name of the segment template.
     * @param type              The type or category of the segment.
     * @param schematicFileName The filename (e.g., "my_segment.schem") of the associated schematic.
     * @param size              The dimensions (width, height, length) of the segment.
     * @param entryPoints       List of entry points with relative positions.
     * @param sandSpawnLocations List of relative spawn locations for sand.
     * @param itemSpawnLocations List of relative spawn locations for items.
     * @param coinSpawnLocations List of relative spawn locations for coins.
     * @param totalCoins        Total number of coins associated with this segment.
     */
    public Segment(
            @NotNull String name,
            @Nullable SegmentType type, // Type might be optional
            @NotNull String schematicFileName,
            @NotNull BlockVector3 size,
            @NotNull List<RelativeEntryPoint> entryPoints,
            @NotNull List<BlockVector3> sandSpawnLocations,
            @NotNull List<BlockVector3> itemSpawnLocations,
            @NotNull List<BlockVector3> coinSpawnLocations,
            int totalCoins
    ) {
        // Basic validation
        Objects.requireNonNull(name, "Segment name cannot be null");
        Objects.requireNonNull(schematicFileName, "Schematic filename cannot be null");
        Objects.requireNonNull(size, "Segment size cannot be null");
        Objects.requireNonNull(entryPoints, "Entry points list cannot be null");
        Objects.requireNonNull(sandSpawnLocations, "Sand spawn locations list cannot be null");
        Objects.requireNonNull(itemSpawnLocations, "Item spawn locations list cannot be null");
        Objects.requireNonNull(coinSpawnLocations, "Coin spawn locations list cannot be null");
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Segment name cannot be empty");
        if (schematicFileName.trim().isEmpty()) throw new IllegalArgumentException("Schematic filename cannot be empty");
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) throw new IllegalArgumentException("Segment dimensions must be positive");


        this.name = name;
        this.type = type;
        this.schematicFileName = schematicFileName;
        this.size = size;
        // Store defensive copies of mutable lists
        this.entryPoints = new ArrayList<>(entryPoints);
        this.sandSpawnLocations = new ArrayList<>(sandSpawnLocations);
        this.itemSpawnLocations = new ArrayList<>(itemSpawnLocations);
        this.coinSpawnLocations = new ArrayList<>(coinSpawnLocations);
        this.totalCoins = totalCoins;
    }

    // --- Getters ---

    @NotNull public String getName() { return name; }
    @Nullable public SegmentType getType() { return type; } // Type might be optional
    @NotNull public String getSchematicFileName() { return schematicFileName; }
    @NotNull public BlockVector3 getSize() { return size; } // Returns dimensions (width, height, length)

    // Return unmodifiable lists to prevent external modification
    @NotNull public List<RelativeEntryPoint> getEntryPoints() { return Collections.unmodifiableList(entryPoints); }
    @NotNull public List<BlockVector3> getSandSpawnLocations() { return Collections.unmodifiableList(sandSpawnLocations); }
    @NotNull public List<BlockVector3> getItemSpawnLocations() { return Collections.unmodifiableList(itemSpawnLocations); }
    @NotNull public List<BlockVector3> getCoinSpawnLocations() { return Collections.unmodifiableList(coinSpawnLocations); }
    public int getTotalCoins() { return totalCoins; }

    // --- Template-related Logic ---

    /**
     * Checks if this segment template has at least one entry point facing the specified direction.
     * @param dir The direction to check for.
     * @return true if an entry point with that direction exists, false otherwise.
     */
    public boolean hasEntryPointInDirection(@NotNull Direction dir) {
        Objects.requireNonNull(dir, "Direction cannot be null");
        for (RelativeEntryPoint ep : this.entryPoints) {
            if (ep.getDirection() == dir) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first entry point in this template that faces the specified direction.
     *
     * @param direction The direction to search for.
     * @return The first matching RelativeEntryPoint, or null if none found.
     */
    @Nullable
    public RelativeEntryPoint findEntryPointByDirection(@NotNull Direction direction) {
        Objects.requireNonNull(direction, "Direction cannot be null");
         for (RelativeEntryPoint ep : this.entryPoints) {
             if (ep.getDirection() == direction) {
                 return ep;
             }
         }
         return null; // No matching entry point found
     }

    /**
     * Helper method to get the File object representing the schematic file,
     * assuming it's located in a standard subdirectory within the plugin's data folder.
     *
     * @param baseDataFolder The plugin's data folder (e.g., obtained via plugin.getDataFolder()).
     * @param schematicSubDir The name of the subdirectory where schematics are stored (e.g., "schematics").
     * @return The File object, or null if the baseDataFolder is invalid.
     */
    @Nullable
    public File getSchematicFile(@NotNull File baseDataFolder, @NotNull String schematicSubDir) {
        Objects.requireNonNull(baseDataFolder, "Base data folder cannot be null");
        Objects.requireNonNull(schematicSubDir, "Schematic subdirectory cannot be null");
        if (!baseDataFolder.isDirectory()) {
            // Log error maybe?
            return null;
        }
        File schematicsDir = new File(baseDataFolder, schematicSubDir);
        return new File(schematicsDir, this.schematicFileName);
    }


    @Override
    public String toString() {
        return "Segment{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", schematicFileName='" + schematicFileName + '\'' +
                ", size=" + size +
                ", entryPoints=" + entryPoints.size() +
                ", sandSpawns=" + sandSpawnLocations.size() +
                ", itemSpawns=" + itemSpawnLocations.size() +
                ", coinSpawns=" + coinSpawnLocations.size() +
                ", totalCoins=" + totalCoins +
                '}';
    }

    // --- Removed Methods ---
    // - cloneToLocation(Location to) -> Belongs in a placement/generator class
    // - cloneByEntryPoint(EntryPoint entryPoint) -> Belongs in a placement/generator class
    // - getAdjacentEntryPoint(EntryPoint oppositeEntryPoint) -> Logic might be needed in placement class
    // - findRelativeNwCorner(EntryPoint oppositeEntryPoint) -> Belongs in a placement/generator class
    // - getEntryPoint(EntryPoint adjacentEntryPoint) -> Replaced by findEntryPointByDirection
    // - getStartLocation() -> Replaced by relative bounds/size concept
    // - getEndLocation() -> Replaced by relative bounds/size concept
    // - getBounds() -> Replaced by getSize() or potentially methods to get relative min/max based on size

    public static class RelativeEntryPoint {
        private final BlockVector3 relativePosition; // Position relative to segment origin
        private final Direction direction;           // Direction it faces outwards
    
        public RelativeEntryPoint(@NotNull BlockVector3 relativePosition, @NotNull Direction direction) {
            this.relativePosition = Objects.requireNonNull(relativePosition, "Relative position cannot be null");
            this.direction = Objects.requireNonNull(direction, "Direction cannot be null");
        }
    
        @NotNull
        public BlockVector3 getRelativePosition() {
            return relativePosition;
        }
    
        @NotNull
        public Direction getDirection() {
            return direction;
        }
    
        /**
         * Calculates the position of this entry point in the world IF the segment's
         * origin (minimum corner) were placed at the given world location (WorldEdit Location).
         *
         * @param segmentOriginInWorld The absolute world location where the segment's origin (min corner) is placed.
         * @return The absolute WorldEdit location of this entry point.
         */
        public com.sk89q.worldedit.util.Location toWorldEditLocation(@NotNull com.sk89q.worldedit.util.Location segmentOriginInWorld) {
            Objects.requireNonNull(segmentOriginInWorld, "Segment origin location cannot be null");
            return new com.sk89q.worldedit.util.Location(
                segmentOriginInWorld.getExtent(),
                segmentOriginInWorld.getX() + relativePosition.x(),
                segmentOriginInWorld.getY() + relativePosition.y(),
                segmentOriginInWorld.getZ() + relativePosition.z()
            );
        }
    
        /**
         * Calculates the position of this entry point in the world IF the segment's
         * origin (minimum corner) were placed at the given Bukkit world location.
         *
         * @param segmentOriginInWorld The absolute Bukkit world location where the segment's origin (min corner) is placed.
         * @return The absolute Bukkit location of this entry point.
         */
        public org.bukkit.Location toBukkitLocation(@NotNull org.bukkit.Location segmentOriginInWorld) {
            Objects.requireNonNull(segmentOriginInWorld, "Segment origin location cannot be null");
            return new org.bukkit.Location(
                segmentOriginInWorld.getWorld(),
                segmentOriginInWorld.getX() + relativePosition.x(),
                segmentOriginInWorld.getY() + relativePosition.y(),
                segmentOriginInWorld.getZ() + relativePosition.z()
            );
        }
    
        @NotNull
        public Direction getOppositeDirection() {
            return direction.getOpposite(); // Assuming Direction has this method
        }
    
        @Override
        public String toString() {
            return "RelativeEntryPoint{" +
                    "relativePosition=" + relativePosition +
                    ", direction=" + direction +
                    '}';
        }
    
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelativeEntryPoint that = (RelativeEntryPoint) o;
            return Objects.equals(relativePosition, that.relativePosition) && direction == that.direction;
        }
    
        @Override
        public int hashCode() {
            return Objects.hash(relativePosition, direction);
        }
    }

    public VaultType getVaultType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVaultType'");
    }

    public VaultType getKeyForVault() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getKeyForVault'");
    }

    public boolean isPuzzleRoom() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPuzzleRoom'");
    }

    public Object getContainedVault() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getContainedVault'");
    }

    public Object isLavaParkour() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isLavaParkour'");
    }

    public Object isHub() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isHub'");
    }
}