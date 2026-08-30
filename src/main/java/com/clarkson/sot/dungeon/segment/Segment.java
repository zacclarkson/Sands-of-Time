package com.clarkson.sot.dungeon.segment;

import com.clarkson.sot.dungeon.VaultColor;

import com.sk89q.worldedit.math.BlockVector3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * World-independent template for a dungeon segment. All positions are relative
 * to the segment's minimum corner (origin).
 */
public class Segment {

    // --- Core Identification & Structure ---
    private final String name;
    private final SegmentType type;
    private final String schematicFileName;
    private final BlockVector3 size;
    private final List<RelativeEntryPoint> entryPoints;

    // --- Feature Spawn Locations (Relative) ---
    private final List<BlockVector3> sandSpawnLocations;
    private final List<BlockVector3> itemSpawnLocations;
    private final List<BlockVector3> coinSpawnLocations;
    private final List<BlockVector3> sandSacrificeLocations;
    /**
     * Sand trade points: chests out in the dungeon that buy depth-scaled coins for sand. Unlike
     * {@link #sandSacrificeLocations} these are gathered from <em>every</em> segment rather than the
     * HUB winning outright — being out in the branches is the whole point of them.
     */
    private final List<BlockVector3> sandTradeLocations;
    private final List<BlockVector3> mobSpawnerLocations;

    // --- Vault / Key Metadata ---
    private final int totalCoins;
    @Nullable private final VaultColor containedVault;
    @Nullable private final VaultColor containedVaultKey;
    @Nullable private final BlockVector3 vaultLocationOffset;
    @Nullable private final BlockVector3 keyLocationOffset;

    // --- Door / Gate / Lever ---
    /** Vault door opening bounds, null if this segment has no vault door. */
    @Nullable private final SegmentBound vaultDoorBound;
    /** All gate openings in this segment (may be empty). */
    private final List<SegmentBound> gates;
    /** Lever position; must be present when {@code gates} is non-empty. */
    @Nullable private final BlockVector3 leverOffset;

    // --- Safe Exit ---
    /** The block players interact with to escape the dungeon; null if this segment has no exit. */
    @Nullable private final BlockVector3 safeExitOffset;
    /**
     * The 2D safe-exit portal opening (nether portal), null if undefined. When present,
     * {@link #safeExitOffset} is a representative cell of this bound (its min) kept for the legacy
     * point-based escape path until walk-through escape is implemented.
     */
    @Nullable private final SegmentBound safeExitBound;

    // --- Hub features ---
    /** Coin-bank cell (an ENDER_CHEST is built here at runtime); null if this segment has no bank. */
    @Nullable private final BlockVector3 bankOffset;
    /** Death/respawn cage positions (max 4; revive points auto-derived at runtime). */
    private final List<BlockVector3> deathCageOffsets;
    /** Interact points where players deposit collected sand onto the timer. */
    private final List<BlockVector3> sandTimerOffsets;
    /** Base of the visual sand-timer column (hub); null if this segment has no timer. */
    @Nullable private final BlockVector3 timerOffset;
    /** Per-player spawn positions in the hub; players are spread across these at game start. */
    private final List<BlockVector3> playerSpawnOffsets;

    /**
     * Full constructor. Called by SaveSegmentCommand and StructureLoader.
     */
    public Segment(
            @NotNull  String name,
            @Nullable SegmentType type,
            @NotNull  String schematicFileName,
            @NotNull  BlockVector3 size,
            @NotNull  List<RelativeEntryPoint> entryPoints,
            @NotNull  List<BlockVector3> sandSpawnLocations,
            @NotNull  List<BlockVector3> itemSpawnLocations,
            @NotNull  List<BlockVector3> coinSpawnLocations,
            int totalCoins,
            @Nullable VaultColor containedVault,
            @Nullable VaultColor containedVaultKey,
            @Nullable BlockVector3 vaultLocationOffset,
            @Nullable BlockVector3 keyLocationOffset,
            // New fields
            @Nullable SegmentBound vaultDoorBound,
            @NotNull  List<SegmentBound> gates,
            @Nullable BlockVector3 leverOffset,
            @NotNull  List<BlockVector3> sandSacrificeLocations,
            @NotNull  List<BlockVector3> mobSpawnerLocations,
            @Nullable BlockVector3 safeExitOffset,
            // Hub features
            @Nullable BlockVector3 bankOffset,
            @NotNull  List<BlockVector3> deathCageOffsets,
            @Nullable SegmentBound safeExitBound,
            @NotNull  List<BlockVector3> sandTimerOffsets,
            @Nullable BlockVector3 timerOffset,
            @NotNull  List<BlockVector3> playerSpawnOffsets,
            @NotNull  List<BlockVector3> sandTradeLocations
    ) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(schematicFileName, "schematicFileName");
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Segment name cannot be empty");
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0)
            throw new IllegalArgumentException("Segment dimensions must be positive");

        this.name                   = name;
        this.type                   = type;
        this.schematicFileName      = schematicFileName;
        this.size                   = size;
        this.entryPoints            = new ArrayList<>(entryPoints);
        this.sandSpawnLocations     = new ArrayList<>(sandSpawnLocations);
        this.itemSpawnLocations     = new ArrayList<>(itemSpawnLocations);
        this.coinSpawnLocations     = new ArrayList<>(coinSpawnLocations);
        this.totalCoins             = totalCoins;
        this.containedVault         = containedVault;
        this.containedVaultKey      = containedVaultKey;
        this.vaultLocationOffset    = vaultLocationOffset;
        this.keyLocationOffset      = keyLocationOffset;
        this.vaultDoorBound         = vaultDoorBound;
        this.gates                  = new ArrayList<>(gates);
        this.leverOffset            = leverOffset;
        this.sandSacrificeLocations = new ArrayList<>(sandSacrificeLocations);
        this.mobSpawnerLocations    = new ArrayList<>(mobSpawnerLocations);
        this.safeExitOffset         = safeExitOffset;
        this.safeExitBound          = safeExitBound;
        this.bankOffset             = bankOffset;
        this.deathCageOffsets       = new ArrayList<>(deathCageOffsets);
        this.sandTimerOffsets       = new ArrayList<>(sandTimerOffsets);
        this.timerOffset            = timerOffset;
        this.playerSpawnOffsets     = new ArrayList<>(playerSpawnOffsets);
        this.sandTradeLocations     = new ArrayList<>(sandTradeLocations);
    }

    // --- Core getters ---
    @NotNull  public String getName()               { return name; }
    @Nullable public SegmentType getType()          { return type; }
    @NotNull  public String getSchematicFileName()  { return schematicFileName; }
    @NotNull  public BlockVector3 getSize()         { return size; }
    @NotNull  public List<RelativeEntryPoint> getEntryPoints() {
        return Collections.unmodifiableList(entryPoints);
    }

    // --- Spawn location getters ---
    @NotNull public List<BlockVector3> getSandSpawnLocations()     { return Collections.unmodifiableList(sandSpawnLocations); }
    @NotNull public List<BlockVector3> getItemSpawnLocations()     { return Collections.unmodifiableList(itemSpawnLocations); }
    @NotNull public List<BlockVector3> getCoinSpawnLocations()     { return Collections.unmodifiableList(coinSpawnLocations); }
    @NotNull public List<BlockVector3> getSandSacrificeLocations() { return Collections.unmodifiableList(sandSacrificeLocations); }
    @NotNull public List<BlockVector3> getSandTradeLocations()     { return Collections.unmodifiableList(sandTradeLocations); }
    @NotNull public List<BlockVector3> getMobSpawnerLocations()    { return Collections.unmodifiableList(mobSpawnerLocations); }

    // --- Vault / key getters ---
    public int getTotalCoins() { return totalCoins; }
    @Nullable public VaultColor getContainedVault()    { return containedVault; }
    @Nullable public VaultColor getContainedVaultKey() { return containedVaultKey; }

    @Nullable
    public BlockVector3 getVaultOffset() {
        return (containedVault != null) ? vaultLocationOffset : null;
    }

    @Nullable
    public BlockVector3 getKeyOffset() {
        return (containedVaultKey != null) ? keyLocationOffset : null;
    }

    // --- Door / gate / lever getters ---
    @Nullable public SegmentBound getVaultDoorBound()  { return vaultDoorBound; }
    @NotNull  public List<SegmentBound> getGates()     { return Collections.unmodifiableList(gates); }
    @Nullable public BlockVector3 getLeverOffset()     { return leverOffset; }

    // --- Safe exit getter ---
    @Nullable public BlockVector3 getSafeExitOffset()  { return safeExitOffset; }
    @Nullable public SegmentBound getSafeExitBound()   { return safeExitBound; }

    // --- Hub feature getters ---
    @Nullable public BlockVector3 getBankOffset()             { return bankOffset; }
    @NotNull  public List<BlockVector3> getDeathCageOffsets() { return Collections.unmodifiableList(deathCageOffsets); }
    @NotNull  public List<BlockVector3> getSandTimerOffsets() { return Collections.unmodifiableList(sandTimerOffsets); }
    @NotNull  public List<BlockVector3> getPlayerSpawnOffsets() { return Collections.unmodifiableList(playerSpawnOffsets); }
    @Nullable public BlockVector3 getTimerOffset()            { return timerOffset; }

    // --- Entry-point helpers ---
    public boolean hasEntryPointInDirection(@NotNull Direction dir) {
        Objects.requireNonNull(dir);
        for (RelativeEntryPoint ep : entryPoints) {
            if (ep.getDirection() == dir) return true;
        }
        return false;
    }

    @Nullable
    public RelativeEntryPoint findEntryPointByDirection(@NotNull Direction direction) {
        Objects.requireNonNull(direction);
        for (RelativeEntryPoint ep : entryPoints) {
            if (ep.getDirection() == direction) return ep;
        }
        return null;
    }

    @Nullable
    public File getSchematicFile(@NotNull File baseDataFolder, @NotNull String schematicSubDir) {
        Objects.requireNonNull(baseDataFolder);
        Objects.requireNonNull(schematicSubDir);
        if (!baseDataFolder.isDirectory()) return null;
        return new File(new File(baseDataFolder, schematicSubDir), schematicFileName);
    }

    @Override
    public String toString() {
        return "Segment{name='" + name + "', type=" + type
                + ", vault=" + containedVault + ", key=" + containedVaultKey
                + ", gates=" + gates.size() + '}';
    }

    // -------------------------------------------------------------------------
    // Inner class: RelativeEntryPoint
    // -------------------------------------------------------------------------

    public static class RelativeEntryPoint {
        private final BlockVector3 relativePosition;
        private final Direction direction;

        public RelativeEntryPoint(@NotNull BlockVector3 relativePosition, @NotNull Direction direction) {
            this.relativePosition = Objects.requireNonNull(relativePosition);
            this.direction        = Objects.requireNonNull(direction);
        }

        @NotNull public BlockVector3 getRelativePosition() { return relativePosition; }
        @NotNull public Direction getDirection()            { return direction; }
        @NotNull public Direction getOppositeDirection()    { return direction.getOpposite(); }

        @Override
        public String toString() {
            return "RelativeEntryPoint{pos=" + relativePosition + ", dir=" + direction + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelativeEntryPoint)) return false;
            RelativeEntryPoint that = (RelativeEntryPoint) o;
            return Objects.equals(relativePosition, that.relativePosition) && direction == that.direction;
        }

        @Override
        public int hashCode() {
            return Objects.hash(relativePosition, direction);
        }
    }
}
