package com.clarkson.sot.dungeon.segment; // Assuming this package

// Assuming VaultColor and SegmentType are in dungeon package
import com.clarkson.sot.dungeon.VaultColor;

import com.sk89q.worldedit.math.BlockVector3; // Make sure this is imported

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a world-independent template or blueprint for a dungeon segment.
 * Stores metadata (including type, vault/key info), relative locations
 * of features (entry points, spawns, vault/key offsets), dimensions, and a link
 * to the schematic file. All positions are relative to the segment's conceptual
 * origin (usually its minimum corner).
 * Segment characteristics like Hub, Puzzle Room, Lava Parkour are now determined by SegmentType.
 */
public class Segment {

    // --- Core Identification & Structure ---
    private final String name;
    private final SegmentType type; // General category (e.g., HUB, PUZZLE_ROOM, LAVA_PARKOUR, CORRIDOR)
    private final String schematicFileName;
    private final BlockVector3 size; // Width(X), Height(Y), Length(Z)
    private final List<RelativeEntryPoint> entryPoints;

    // --- Feature Spawn Locations (Relative) ---
    private final List<BlockVector3> sandSpawnLocations;
    private final List<BlockVector3> itemSpawnLocations;
    private final List<BlockVector3> coinSpawnLocations;

    // --- Gameplay Metadata ---
    private final int totalCoins; // Base number of coins expected (approximate)
    // Removed: coinMultiplier - Coin scaling might now depend on PlacedSegment.depth
    // Removed: isHub, isPuzzleRoom, isLavaParkour - Now inferred from 'type' field
    private final VaultColor containedVault; // Which vault entrance is in this segment (null if none)
    private final VaultColor containedVaultKey; // Which vault key is in this segment (null if none)
    @Nullable private final BlockVector3 vaultLocationOffset; // Relative position of the vault marker block, if containedVault is not null
    @Nullable private final BlockVector3 keyLocationOffset;   // Relative position of the key spawn, if containedVaultKey is not null


    /**
     * Constructor for creating a Segment template.
     * Values are typically loaded from JSON metadata.
     *
     * @param name                Unique name of the segment template.
     * @param type                General category/type of the segment (e.g., HUB, PUZZLE_ROOM).
     * @param schematicFileName   Filename of the associated schematic (e.g., "my_segment.schem").
     * @param size                Dimensions (width, height, length) of the segment.
     * @param entryPoints         List of entry points with relative positions and directions.
     * @param sandSpawnLocations  List of relative spawn locations for sand.
     * @param itemSpawnLocations  List of relative spawn locations for items.
     * @param coinSpawnLocations  List of relative spawn locations for coins.
     * @param totalCoins          Approximate total base coin value in the segment.
     * @param containedVault      The color of the vault entrance within this segment, or null.
     * @param containedVaultKey   The color of the vault key found within this segment, or null.
     * @param vaultLocationOffset Relative offset (from segment origin) of the vault marker block, if containedVault is not null.
     * @param keyLocationOffset   Relative offset (from segment origin) of the key spawn location, if containedVaultKey is not null.
     */
    public Segment(
            @NotNull String name,
            @Nullable SegmentType type, // Type is now crucial
            @NotNull String schematicFileName,
            @NotNull BlockVector3 size,
            @NotNull List<RelativeEntryPoint> entryPoints,
            @NotNull List<BlockVector3> sandSpawnLocations,
            @NotNull List<BlockVector3> itemSpawnLocations,
            @NotNull List<BlockVector3> coinSpawnLocations,
            int totalCoins,
            @Nullable VaultColor containedVault,
            @Nullable VaultColor containedVaultKey,
            @Nullable BlockVector3 vaultLocationOffset,
            @Nullable BlockVector3 keyLocationOffset
    ) {
        // --- Basic Validation ---
        Objects.requireNonNull(name, "Segment name cannot be null");
        Objects.requireNonNull(schematicFileName, "Schematic filename cannot be null");
        // Removed null checks for booleans
        // ... other null checks ...
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Segment name cannot be empty");
        // ... other validation ...
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) throw new IllegalArgumentException("Segment dimensions must be positive");

        // Validation: Offset should only be present if the corresponding item is present
        if (containedVault == null && vaultLocationOffset != null) {
            System.err.println("Warning: Segment '" + name + "' has vaultLocationOffset but containedVault is null.");
            // vaultLocationOffset = null; // Optionally nullify
        }
        if (containedVaultKey == null && keyLocationOffset != null) {
             System.err.println("Warning: Segment '" + name + "' has keyLocationOffset but containedVaultKey is null.");
             // keyLocationOffset = null; // Optionally nullify
        }


        // --- Assign Fields ---
        this.name = name;
        this.type = type; // Assign the crucial type
        this.schematicFileName = schematicFileName;
        this.size = size;
        this.entryPoints = new ArrayList<>(entryPoints);
        this.sandSpawnLocations = new ArrayList<>(sandSpawnLocations);
        this.itemSpawnLocations = new ArrayList<>(itemSpawnLocations);
        this.coinSpawnLocations = new ArrayList<>(coinSpawnLocations);
        this.totalCoins = totalCoins;
        // Removed assignments for: coinMultiplier, isHub, isPuzzleRoom, isLavaParkour

        // Assign Metadata Fields
        this.containedVault = containedVault;
        this.containedVaultKey = containedVaultKey;
        // Assign Offset Fields
        this.vaultLocationOffset = vaultLocationOffset;
        this.keyLocationOffset = keyLocationOffset;
    }

    // --- Getters for Core Info ---
    @NotNull public String getName() { return name; }
    @Nullable public SegmentType getType() { return type; } // Getter for the type
    @NotNull public String getSchematicFileName() { return schematicFileName; }
    @NotNull public BlockVector3 getSize() { return size; }
    @NotNull public List<RelativeEntryPoint> getEntryPoints() { return Collections.unmodifiableList(entryPoints); }

    // --- Getters for Spawn Locations ---
    @NotNull public List<BlockVector3> getSandSpawnLocations() { return Collections.unmodifiableList(sandSpawnLocations); }
    @NotNull public List<BlockVector3> getItemSpawnLocations() { return Collections.unmodifiableList(itemSpawnLocations); }
    @NotNull public List<BlockVector3> getCoinSpawnLocations() { return Collections.unmodifiableList(coinSpawnLocations); }

    // --- Getters for Gameplay Metadata ---
    public int getTotalCoins() { return totalCoins; }
    // Removed getters: getCoinMultiplier(), isHub(), isPuzzleRoom(), isLavaParkour()
    @Nullable public VaultColor getContainedVault() { return containedVault; }
    @Nullable public VaultColor getContainedVaultKey() { return containedVaultKey; }

    /**
     * Gets the relative offset (from segment origin) for the vault marker block,
     * if this segment contains a vault entrance (i.e., getContainedVault() is not null).
     *
     * @return The relative BlockVector3 offset, or null if no vault or offset is defined.
     */
    @Nullable
    public BlockVector3 getVaultOffset() {
        // Return the offset only if a vault is actually supposed to be contained
        return (this.containedVault != null) ? this.vaultLocationOffset : null;
    }

    /**
     * Gets the relative offset (from segment origin) for the vault key spawn location,
     * if this segment contains a vault key (i.e., getContainedVaultKey() is not null).
     *
     * @return The relative BlockVector3 offset, or null if no key or offset is defined.
     */
    @Nullable
    public BlockVector3 getKeyOffset() {
        // Return the offset only if a key is actually supposed to be contained
        return (this.containedVaultKey != null) ? this.keyLocationOffset : null;
    }


    // --- Template-related Logic ---
    public boolean hasEntryPointInDirection(@NotNull Direction dir) {
        Objects.requireNonNull(dir, "Direction cannot be null");
        for (RelativeEntryPoint ep : this.entryPoints) {
            if (ep.getDirection() == dir) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public RelativeEntryPoint findEntryPointByDirection(@NotNull Direction direction) {
        Objects.requireNonNull(direction, "Direction cannot be null");
         for (RelativeEntryPoint ep : this.entryPoints) {
             if (ep.getDirection() == direction) {
                 return ep;
             }
         }
         return null;
     }

    @Nullable
    public File getSchematicFile(@NotNull File baseDataFolder, @NotNull String schematicSubDir) {
        Objects.requireNonNull(baseDataFolder, "Base data folder cannot be null");
        Objects.requireNonNull(schematicSubDir, "Schematic subdirectory cannot be null");
        if (!baseDataFolder.isDirectory()) {
            return null;
        }
        File schematicsDir = new File(baseDataFolder, schematicSubDir);
        return new File(schematicsDir, this.schematicFileName);
    }

    @Override
    public String toString() {
        // Updated toString to remove deleted fields
        return "Segment{" +
                "name='" + name + '\'' +
                ", type=" + type + // Type is now primary descriptor
                ", schematic='" + schematicFileName + '\'' +
                ", size=" + size +
                ", entryPoints=" + entryPoints.size() +
                ", vault=" + containedVault + (vaultLocationOffset != null ? "@" + vaultLocationOffset : "") +
                ", key=" + containedVaultKey + (keyLocationOffset != null ? "@" + keyLocationOffset : "") +
                '}';
    }

    // --- Inner Class: RelativeEntryPoint ---
    // (Remains the same)
    public static class RelativeEntryPoint {
        private final BlockVector3 relativePosition;
        private final Direction direction;

        public RelativeEntryPoint(@NotNull BlockVector3 relativePosition, @NotNull Direction direction) {
            this.relativePosition = Objects.requireNonNull(relativePosition, "Relative position cannot be null");
            this.direction = Objects.requireNonNull(direction, "Direction cannot be null");
        }

        @NotNull public BlockVector3 getRelativePosition() { return relativePosition; }
        @NotNull public Direction getDirection() { return direction; }
        @NotNull public Direction getOppositeDirection() { return direction.getOpposite(); }

        // These conversion methods likely belong elsewhere or need world context
        // public com.sk89q.worldedit.util.Location toWorldEditLocation(@NotNull com.sk89q.worldedit.util.Location segmentOriginInWorld) { return null;}
        // public org.bukkit.Location toBukkitLocation(@NotNull org.bukkit.Location segmentOriginInWorld) { return null;}

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
}
