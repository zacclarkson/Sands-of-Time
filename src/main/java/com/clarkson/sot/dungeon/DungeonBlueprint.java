package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.entities.Area; // Import the Area class
import org.bukkit.Location; // Needed for Area's internal representation
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    // Where players escape the dungeon. Null when no segment template carries a SAFE_EXIT marker.
    @Nullable private final Vector safeExitRelativeLocation;

    // Base of the visual sand-timer column. Null when no segment carries a TIMER marker.
    @Nullable private final Vector timerBaseRelativeLocation;

    // Cell the coin bank (ender chest) is built in. Null when no segment carries a BANK marker.
    @Nullable private final Vector bankRelativeLocation;
    private final List<Vector> playerSpawnRelativeLocations;

    // Cells where players deposit carried sand onto the timer (TIMER_DEPOSIT markers). Empty when
    // no segment carries one.
    private final List<Vector> sandTimerRelativeLocations;

    // Cells where hostile mobs are armed to spawn (MOB_SPAWNER markers). Empty when no segment
    // carries one -- the bundled hub does not, so a stock server generates no mob encounters.
    private final List<Vector> mobSpawnerRelativeLocations;

    // Death cages (DEATH_CAGE markers) and the sacrifice points that free them (SAND_SACRIFICE
    // markers). These two lists are index-aligned and always the same length: DungeonGenerator
    // reconciles them, deriving a point beside any cage the templates left unpaired, so
    // DungeonManager can zip them straight into DeathCage objects.
    private final List<Vector> deathCageRelativeLocations;
    private final List<Vector> sandSacrificeRelativeLocations;

    // Connections between segments. Each becomes a rusty-key door; the unused openings are the
    // entry points the generator never attached a neighbour to, and get sealed as plain wall.
    private final List<Doorway> doorways;
    private final List<Doorway> unusedOpenings;

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
                            @NotNull Area relativeBounds, // Changed parameter
                            @Nullable Vector safeExitRelativeLocation,
                            @Nullable Vector timerBaseRelativeLocation,
                            @Nullable Vector bankRelativeLocation,
                            @NotNull List<Vector> playerSpawnRelativeLocations,
                            @NotNull List<Vector> sandTimerRelativeLocations,
                            @NotNull List<Vector> deathCageRelativeLocations,
                            @NotNull List<Vector> sandSacrificeRelativeLocations,
                            @NotNull List<Doorway> doorways,
                            @NotNull List<Doorway> unusedOpenings,
                            @NotNull List<Vector> mobSpawnerRelativeLocations
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
        // Deliberately not null-checked: segment templates predating the SAFE_EXIT marker have none.
        this.safeExitRelativeLocation = (safeExitRelativeLocation != null) ? safeExitRelativeLocation.clone() : null;
        this.timerBaseRelativeLocation = (timerBaseRelativeLocation != null) ? timerBaseRelativeLocation.clone() : null;
        this.bankRelativeLocation = (bankRelativeLocation != null) ? bankRelativeLocation.clone() : null;
        this.playerSpawnRelativeLocations = Collections.unmodifiableList(new ArrayList<>(playerSpawnRelativeLocations));
        this.sandTimerRelativeLocations = Collections.unmodifiableList(new ArrayList<>(sandTimerRelativeLocations));
        this.deathCageRelativeLocations = Collections.unmodifiableList(new ArrayList<>(deathCageRelativeLocations));
        this.sandSacrificeRelativeLocations = Collections.unmodifiableList(new ArrayList<>(sandSacrificeRelativeLocations));
        this.doorways = Collections.unmodifiableList(new ArrayList<>(doorways));
        this.unusedOpenings = Collections.unmodifiableList(new ArrayList<>(unusedOpenings));
        this.mobSpawnerRelativeLocations = Collections.unmodifiableList(new ArrayList<>(mobSpawnerRelativeLocations));
    }

    // --- Getters ---

    @NotNull public List<PlacedSegment> getRelativeSegments() { return relativeSegments; }
    @NotNull public Vector getHubRelativeLocation() { return hubRelativeLocation; }
    @NotNull public Map<VaultColor, Vector> getVaultMarkerRelativeLocations() { return vaultMarkerRelativeLocations; }
    @NotNull public Map<VaultColor, Vector> getKeySpawnRelativeLocations() { return keySpawnRelativeLocations; }
    @NotNull public List<Vector> getSandSpawnRelativeLocations() { return sandSpawnRelativeLocations; }
    @NotNull public List<Vector> getCoinSpawnRelativeLocations() { return coinSpawnRelativeLocations; }
    @NotNull public List<Vector> getItemSpawnRelativeLocations() { return itemSpawnRelativeLocations; }

    /**
     * Gets the relative location of the safe exit, or null if no segment template defines one.
     */
    @Nullable public Vector getSafeExitRelativeLocation() {
        return safeExitRelativeLocation != null ? safeExitRelativeLocation.clone() : null;
    }

    /** Relative doorways between connected segments; each gets a rusty-key door. */
    @NotNull public List<Doorway> getDoorways() { return doorways; }

    /** Relative entry points no neighbour was attached to; these get sealed as plain wall. */
    @NotNull public List<Doorway> getUnusedOpenings() { return unusedOpenings; }

    /** Relative per-player spawn points (empty if no segment defines a PLAYER_SPAWN marker). */
    @NotNull public List<Vector> getPlayerSpawnRelativeLocations() { return playerSpawnRelativeLocations; }

    /** Relative mob spawner cells (empty if no segment defines a MOB_SPAWNER marker). */
    @NotNull public List<Vector> getMobSpawnerRelativeLocations() { return mobSpawnerRelativeLocations; }

    /** Relative sand deposit cells (empty if no segment defines a TIMER_DEPOSIT marker). */
    @NotNull public List<Vector> getSandTimerRelativeLocations() { return sandTimerRelativeLocations; }

    /**
     * Relative death cage cells. Index-aligned with {@link #getSandSacrificeRelativeLocations()}:
     * the cage at index i is freed by the sacrifice point at index i.
     */
    @NotNull public List<Vector> getDeathCageRelativeLocations() { return deathCageRelativeLocations; }

    /**
     * Relative sacrifice point cells, one per death cage and in the same order. Always the same
     * size as {@link #getDeathCageRelativeLocations()} — the generator derives a point for any cage
     * the segment templates did not pair one with.
     */
    @NotNull public List<Vector> getSandSacrificeRelativeLocations() { return sandSacrificeRelativeLocations; }

    /** Relative base of the visual sand-timer column, or null if no segment defines a TIMER marker. */
    @Nullable public Vector getTimerBaseRelativeLocation() {
        return timerBaseRelativeLocation != null ? timerBaseRelativeLocation.clone() : null;
    }

    /** Relative cell the coin bank stands in, or null if no segment defines a BANK marker. */
    @Nullable public Vector getBankRelativeLocation() {
        return bankRelativeLocation != null ? bankRelativeLocation.clone() : null;
    }

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
