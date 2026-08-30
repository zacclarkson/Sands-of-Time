package com.clarkson.sot.dungeon;

// --- Imports (Ensure all needed imports are present) ---
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.dungeon.segment.SegmentRotation;
import com.clarkson.sot.dungeon.segment.Direction; // Assuming this is the correct package
import com.clarkson.sot.utils.StructureLoader;
import com.clarkson.sot.entities.Area; // May be needed for collision detection

// WorldEdit imports
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.util.Vector; // Using Bukkit Vector for blueprint relative locations

// Bukkit imports
import org.bukkit.Location; // Needed for Area and PlacedSegment context
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Java imports
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Manages dungeon blueprint generation using Depth-First Search (DFS).
 * Creates a relative layout (DungeonBlueprint) based on loaded Segment templates.
 * Includes logic for colored branches, vaults, keys, and depth rules.
 *
 * This file defines the method signatures without implementation.
 */
public class DungeonGenerator {

    // --- Fields ---

    private final Plugin plugin;
    private final StructureLoader structureLoader;
    private List<Segment> availableSegments; // Templates loaded from files
    private Random random; // non-final so tests can seed it for determinism
    private static final int MAX_DEPTH = 12; // Deepest vault (gold) maxes at 10, leaving headroom.
    private static final int MAX_TOTAL_SEGMENTS = 120; // Safety cap; a full dungeon fans out well under this.
    // Track placed vaults/keys during generation
    private Set<VaultColor> keysPlacedInDFS;
    private Set<VaultColor> vaultsPlacedInDFS;
    /**
     * Connections the DFS actually made, in blueprint-relative space. Each becomes a rusty-key
     * door; entry points absent from this list opened onto nothing and get sealed instead.
     */
    private List<Doorway> doorwaysInDFS;
    // --- Per-generateDungeonLayout warning state ---
    // generateDungeonLayout retries attemptGeneration many times, and every attempt re-checks the
    // same loaded templates. Warnings that describe the templates on disk rather than the individual
    // attempt are byte-identical on every retry, so they are keyed here and logged once per
    // generateDungeonLayout call (this set is cleared at the top of it).
    private final Set<String> warnedOncePerGeneration = new HashSet<>();
    // Unmet layout requirement -> how many attempts it was missing from. Unlike the above, these are
    // genuinely per-attempt (a layout can fail validation on one attempt and pass on the next), so
    // they are logged at fine level and summarised once if every attempt fails.
    private final Map<String, Integer> validationFailureCounts = new LinkedHashMap<>();

    // --- Generation Configuration ---
    // Depth ranges (min/max inclusive) for vaults. Every max MUST be < MAX_DEPTH so the forced
    // placement at range.max can actually fire (DFS stops placing once depth >= MAX_DEPTH). Ordered
    // shallow -> deep: green nearest the hub, gold furthest.
    private static final Map<VaultColor, MinMax> VAULT_DEPTH_RANGES = Map.of(
        VaultColor.GREEN, new MinMax(2, 4),
        VaultColor.BLUE,  new MinMax(3, 6),
        VaultColor.RED,   new MinMax(5, 8),
        VaultColor.GOLD,  new MinMax(7, 10)
    );
    // Depth ranges for keys (blue key is hub metadata, handled separately). Kept generally shallower
    // than the matching vault so a key is findable, and all within MAX_DEPTH.
     private static final Map<VaultColor, MinMax> KEY_DEPTH_RANGES = Map.of(
         VaultColor.RED,   new MinMax(2, 6),
         VaultColor.GREEN, new MinMax(3, 7),
         VaultColor.GOLD,  new MinMax(5, 9)
     );

    // Placement probabilities (0.0 to 1.0). Vault and key placement share the same scheme: a normal
    // chance within range, a higher chance one below the max, and a forced placement at/after the max.
    private static final double VAULT_SPAWN_CHANCE_NORMAL = 0.20;
    private static final double VAULT_SPAWN_CHANCE_HIGH   = 0.50;
    private static final double KEY_SPAWN_CHANCE_NORMAL   = 0.30;
    private static final double KEY_SPAWN_CHANCE_HIGH      = 0.60;


    // --- Constructor ---

    /**
     * Constructor for DungeonGenerator.
     *
     * @param plugin The main plugin instance.
     */
    public DungeonGenerator(@NotNull Plugin plugin) {
        // Implementation omitted
        this.plugin = plugin;
        this.structureLoader = new StructureLoader(plugin);
        this.availableSegments = new ArrayList<>();
        this.random = new Random();
        this.keysPlacedInDFS = new HashSet<>();
        this.vaultsPlacedInDFS = new HashSet<>();
        this.doorwaysInDFS = new ArrayList<>();
        // throw new UnsupportedOperationException("Constructor implementation not provided."); // Remove throw if implementing
    }

    // --- Test hooks (package-private) ---
    /** Injects segment templates directly, bypassing disk loading. For unit tests only. */
    void setAvailableSegmentsForTest(@NotNull List<Segment> segments) {
        this.availableSegments = new ArrayList<>(segments);
    }

    /** Seeds the RNG so a generation run is deterministic. For unit tests only. */
    void setRandomSeedForTest(long seed) {
        this.random = new Random(seed);
    }

    // --- Public API Methods ---

/**
     * Loads segment templates from JSON files in the specified data directory.
     * Populates the internal list of available segments.
     *
     * @param dataFolder The plugin's data folder where segment JSON files reside.
     * @return true if templates were loaded successfully and are valid (including finding a HUB), false otherwise.
     */
    public boolean loadSegmentTemplates(@NotNull File dataFolder) {
        plugin.getLogger().info("Loading dungeon segment templates from: " + dataFolder.getPath());

        // Ensure data folder exists (StructureLoader also checks, but good practice)
        if (!dataFolder.exists()) {
             plugin.getLogger().warning("Plugin data folder does not exist, cannot load segments: " + dataFolder.getPath());
             // Attempt to create it? Or rely on Bukkit/saveResource to do it?
             // dataFolder.mkdirs(); // Optionally create it here
             return false; // Cannot load if folder doesn't exist
        }
         if (!dataFolder.isDirectory()) {
              plugin.getLogger().severe("Specified data folder is not a directory: " + dataFolder.getPath());
              return false;
         }

        // Use the StructureLoader to load templates from JSON files in the data folder
        this.availableSegments = structureLoader.loadSegmentTemplates(dataFolder);

        // Check if loading was successful and if essential segments exist
        if (this.availableSegments == null || this.availableSegments.isEmpty()) {
            // StructureLoader logs details, but we add a summary warning here
            plugin.getLogger().warning("No dungeon segment templates were loaded successfully from " + dataFolder.getPath());
            this.availableSegments = new ArrayList<>(); // Ensure list is not null
            // Decide if this is fatal - likely yes if no segments load
            return false;
        }

        // Validate that at least one hub segment exists
        if (findHubTemplate() == null) {
             plugin.getLogger().severe("CRITICAL: No segment template with type 'HUB' found! Dungeon generation requires a Hub segment.");
             return false; // Cannot generate without a hub
        }

        plugin.getLogger().info("Successfully loaded " + this.availableSegments.size() + " segment templates.");
        return true;
    }

    /** Number of segment templates currently loaded (for reload feedback). */
    public int getLoadedSegmentCount() {
        return (availableSegments == null) ? 0 : availableSegments.size();
    }

    /** True if a HUB template is currently loaded (dungeon generation needs one). */
    public boolean hasHubTemplate() {
        return findHubTemplate() != null;
    }
    /**
     * Generates the dungeon layout blueprint using DFS. This is the main entry point
     * for creating the relative structure of the dungeon before it's instantiated.
     * Attempts to generate distinct branches for vaults and validates the result.
     * May retry generation a few times if validation fails.
     *
     * @return A DungeonBlueprint object representing the relative layout, or null if generation fails or validation fails after retries.
     */
    @Nullable
    public DungeonBlueprint generateDungeonLayout() {
        int maxRetries = 20; // Blueprint stage does no world I/O, so retries are cheap.
        // Fresh warning state per call: template-level warnings fire once, and the validation
        // tally starts from zero so the failure summary describes this call only.
        warnedOncePerGeneration.clear();
        validationFailureCounts.clear();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            plugin.getLogger().fine("Starting dungeon layout generation attempt " + attempt + "/" + maxRetries + "...");
            DungeonBlueprint blueprint = attemptGeneration();
            if (blueprint != null) {
                plugin.getLogger().info("Dungeon layout generated successfully on attempt " + attempt + "/" + maxRetries);
                return blueprint; // Success
            }
            plugin.getLogger().fine("Dungeon generation attempt " + attempt + " failed validation or generation. Retrying...");
        }
        plugin.getLogger().severe("Failed to generate a valid dungeon layout after " + maxRetries + " attempts.");
        if (!validationFailureCounts.isEmpty()) {
            String summary = validationFailureCounts.entrySet().stream()
                    .map(entry -> entry.getKey() + " (missing on " + entry.getValue() + "/" + maxRetries + " attempts)")
                    .collect(Collectors.joining(", "));
            plugin.getLogger().severe("Unmet layout requirements across all attempts: " + summary);
        }
        return null; // Failed after all retries
    }

    /**
     * Logs a warning the first time the given key is seen within one generateDungeonLayout call.
     * Used for conditions that are a property of the loaded segment templates rather than of an
     * individual attempt, which would otherwise be repeated verbatim once per retry.
     *
     * @param key     Identity of the condition; repeats within a call are suppressed.
     * @param message The warning text to log on the first occurrence.
     */
    private void warnOncePerGeneration(@NotNull String key, @NotNull String message) {
        if (warnedOncePerGeneration.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    /**
     * Attempts a single run of the dungeon generation process.
     * Called by generateDungeonLayout.
     * @return A potentially valid DungeonBlueprint, or null if generation fails internally.
     */
    @Nullable
    private DungeonBlueprint attemptGeneration() {
        // --- Initialization for this attempt ---
        List<PlacedSegment> placedSegments = new ArrayList<>();
        Set<BlockVector3> occupiedOrigins = new HashSet<>();
        Map<VaultColor, Vector> vaultMarkerRelativeLocations = new HashMap<>();
        Map<VaultColor, Vector> keySpawnRelativeLocations = new HashMap<>();
        List<Vector> sandSpawnRelativeLocations = new ArrayList<>();
        List<Vector> coinSpawnRelativeLocations = new ArrayList<>();
        List<Vector> itemSpawnRelativeLocations = new ArrayList<>();
        Vector hubRelativeLocation = null;
        // Reset placed trackers for this attempt
        keysPlacedInDFS.clear(); // Tracks Red, Green, Gold keys placed by DFS
        vaultsPlacedInDFS.clear(); // Tracks Blue, Red, Green, Gold vaults placed by DFS
        doorwaysInDFS.clear(); // Tracks the connections this attempt actually made

        // --- Pre-checks ---
        if (availableSegments.isEmpty()) { /* ... error log ... */ return null; }
        Segment hubTemplate = findHubTemplate();
        if (hubTemplate == null) { /* ... error log ... */ return null; }

        // --- Place Hub ---
        BlockVector3 hubOriginBV3 = BlockVector3.ZERO;
        Location relativeHubOriginLoc = new Location(null, hubOriginBV3.x(), hubOriginBV3.y(), hubOriginBV3.z());
        PlacedSegment hubPlacedSegment = new PlacedSegment(hubTemplate, relativeHubOriginLoc, 0);
        placedSegments.add(hubPlacedSegment);
        occupiedOrigins.add(hubOriginBV3);
        hubRelativeLocation = new Vector(hubOriginBV3.x(), hubOriginBV3.y(), hubOriginBV3.z());
        // NOTE: Do NOT assume Hub contains Blue Vault. Hub contains Blue Key location metadata.
        // The actual Blue Key item is placed by VaultManager later.
        // Vaults (including Blue) must be placed by the DFS in other segments.


        // --- Start DFS from every hub exit ---
        // Vault/key placement is opportunistic (any branch can host any not-yet-placed vault/key when it
        // reaches the right depth via a connecting direction), so no branch is tied to a specific color.
        List<RelativeEntryPoint> hubExits = new ArrayList<>(hubPlacedSegment.getRotatedEntryPoints());
        Collections.shuffle(hubExits, random);

        for (RelativeEntryPoint hubEntryPoint : hubExits) {
            generatePathRecursive(hubPlacedSegment, hubEntryPoint, placedSegments, occupiedOrigins, 1);
        }


        // --- Post-DFS: Consolidate, Calculate Bounds, Validate ---
        if (placedSegments.size() <= 1) { /* ... log warning ... */ return null; }

        // Consolidate features (this populates the maps based on placed segments)
        consolidateFeatureLocations(placedSegments, vaultMarkerRelativeLocations, keySpawnRelativeLocations, sandSpawnRelativeLocations, coinSpawnRelativeLocations, itemSpawnRelativeLocations);

        Vector safeExitRelativeLocation = selectSafeExitRelativeLocation(placedSegments);
        if (safeExitRelativeLocation == null) {
            warnOncePerGeneration("no-safe-exit",
                    "No SAFE_EXIT marker in any segment template; escaping will fall back to "
                    + "the hub location. Add a SAFE_EXIT marker to your HUB segment and re-save it.");
        }

        // Calculate Bounds
        Vector relativeMinVec = calculateRelativeMinBounds(placedSegments);
        Vector relativeMaxVec = calculateRelativeMaxBounds(placedSegments);
        Location relMinLoc = new Location(null, relativeMinVec.getX(), relativeMinVec.getY(), relativeMinVec.getZ());
        Location relMaxLoc = new Location(null, relativeMaxVec.getX(), relativeMaxVec.getY(), relativeMaxVec.getZ());
        Area blueprintBounds = new Area(relMinLoc, relMaxLoc);

        // --- Validate Required Vaults & Keys ---
        // Validation relies on consolidateFeatureLocations having correctly populated the maps
        // BLUE is checked separately below: it lives on the hub, and a hub template saved before the
        // KEY_SPAWN marker existed should still generate a (partly incomplete) dungeon.
        VaultColor[] requiredKeys = {VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD};
        VaultColor[] requiredVaults = {VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD}; // All 4 vaults must be placed by DFS

        List<String> missingRequirements = new ArrayList<>();
        for (VaultColor requiredColor : requiredVaults) {
            if (!vaultMarkerRelativeLocations.containsKey(requiredColor)) {
                missingRequirements.add("vault marker for " + requiredColor);
            }
        }
        for (VaultColor requiredColor : requiredKeys) {
            if (!keySpawnRelativeLocations.containsKey(requiredColor)) {
                missingRequirements.add("key spawn for " + requiredColor);
            }
        }
        // The blue key belongs on the hub (see GAME_RULES.md), but a hub template saved without a
        // KEY_SPAWN marker carries none. Warn rather than fail: a dungeon missing one vault beats
        // no dungeon at all. Add a BLUE KEY_SPAWN marker to the hub and re-save it to fix.
        if (!keySpawnRelativeLocations.containsKey(VaultColor.BLUE)) {
            warnOncePerGeneration("no-blue-key",
                    "No BLUE key spawn in any segment template; the blue vault will "
                    + "not be openable. Add a BLUE KEY_SPAWN marker to your HUB segment and re-save it.");
        }

        if (!missingRequirements.isEmpty()) {
            // Per-attempt, so kept off the console: a layout can fail validation on one attempt and
            // pass on the next. generateDungeonLayout logs one summary if every attempt fails.
            plugin.getLogger().fine("Validation failed for this attempt; missing " + String.join(", ", missingRequirements));
            missingRequirements.forEach(requirement -> validationFailureCounts.merge(requirement, 1, Integer::sum));
            return null; // Validation failed for this attempt
        }

        Vector timerBaseRelativeLocation = selectTimerBaseRelativeLocation(placedSegments);
        List<Vector> playerSpawnRelativeLocations = selectPlayerSpawnRelativeLocations(placedSegments);
        List<Vector> sandTimerRelativeLocations = selectSandTimerRelativeLocations(placedSegments);
        if (sandTimerRelativeLocations.isEmpty()) {
            plugin.getLogger().warning("No TIMER_DEPOSIT marker on any segment template: collected sand "
                    + "cannot be spent on the timer. Add TIMER_DEPOSIT markers to the HUB segment and re-save it.");
        }

        List<Doorway> doorways = new ArrayList<>(doorwaysInDFS);
        List<Doorway> unusedOpenings = findUnusedOpenings(placedSegments, doorways);
        plugin.getLogger().info("Layout has " + doorways.size() + " doorways and "
                + unusedOpenings.size() + " unattached openings to seal.");

        // --- Create and Return Blueprint ---
        return new DungeonBlueprint(
                placedSegments, hubRelativeLocation, vaultMarkerRelativeLocations, keySpawnRelativeLocations,
                sandSpawnRelativeLocations, coinSpawnRelativeLocations, itemSpawnRelativeLocations,
                blueprintBounds, safeExitRelativeLocation, timerBaseRelativeLocation,
                playerSpawnRelativeLocations, sandTimerRelativeLocations, doorways, unusedOpenings
        );
    }


    // --- Private DFS and Helper Methods ---
    /**
     * Recursive Depth-First Search function to generate dungeon paths.
     * Selects, places, and connects segments, then calls itself for new exits.
     *
     * @param currentSegment    The segment instance (in the blueprint) we are currently extending from.
     * @param connectionPoint   The entry point on currentSegment we are connecting *from*.
     * @param placedSegments    (In/Out) List of all segments placed so far in the blueprint.
     * @param occupiedOrigins   (In/Out) Set of BlockVector3 relative origins already occupied.
     * @param currentDepth      The current depth (number of segments) from the hub segment.
     */
    private void generatePathRecursive(
            @NotNull PlacedSegment currentSegment,
            @NotNull RelativeEntryPoint connectionPoint,
            @NotNull List<PlacedSegment> placedSegments,
            @NotNull Set<BlockVector3> occupiedOrigins,
            int currentDepth) {

        // --- Base Cases / Termination Conditions ---
        if (currentDepth >= MAX_DEPTH) {
            return; // Reached max depth for this branch
        }
        if (placedSegments.size() >= MAX_TOTAL_SEGMENTS) {
            // Optional: Log warning if hitting total segment limit frequently
            return; // Reached overall dungeon size limit
        }

        // --- Select Next Segment (template + rotation) ---
        Direction requiredDirection = connectionPoint.getDirection().getOpposite();
        Placement next = selectNextSegment(requiredDirection, currentDepth);

        // If no suitable segment found, this path ends (backtrack)
        if (next == null) {
            return;
        }

        // --- Calculate Placement (using the ROTATED entry that faces requiredDirection) ---
        RelativeEntryPoint nextEntryPoint = rotatedEntryByDirection(next.template, next.steps, requiredDirection);
        if (nextEntryPoint == null) {
             plugin.getLogger().warning("Segment " + next.template.getName() + " selected but missing required entry point " + requiredDirection + " after rotation. Stopping branch.");
             return; // Should not happen if selectNextSegment filters correctly
        }
        BlockVector3 currentSegmentOrigin = BlockVector3.at(
            currentSegment.getWorldOrigin().toVector().getX(),
            currentSegment.getWorldOrigin().toVector().getY(),
            currentSegment.getWorldOrigin().toVector().getZ()
        ); // Relative origin
        BlockVector3 nextSegmentOrigin = calculatePlacementOrigin(currentSegmentOrigin, connectionPoint, nextEntryPoint);

        // --- Check Collision (rotated footprint) ---
        if (checkCollision(nextSegmentOrigin, next.template, next.steps, occupiedOrigins, placedSegments)) {
            return; // Collision detected, stop this branch
        }

        // --- Place Segment ---
        Location relativeNextOriginLoc = new Location(null, nextSegmentOrigin.x(), nextSegmentOrigin.y(), nextSegmentOrigin.z());
        PlacedSegment nextPlacedSegment = new PlacedSegment(next.template, relativeNextOriginLoc, currentDepth, next.steps);

        placedSegments.add(nextPlacedSegment);
        occupiedOrigins.add(nextSegmentOrigin);

        // Record the connection we just made. Both segments meet on this one cell (see
        // calculatePlacementOrigin), so one Doorway covers the shared opening from either side.
        BlockVector3 doorwayCell = currentSegmentOrigin.add(connectionPoint.getRelativePosition());
        doorwaysInDFS.add(new Doorway(
                new Vector(doorwayCell.x(), doorwayCell.y(), doorwayCell.z()),
                connectionPoint.getDirection()));

        // --- Update Global Placed Vaults/Keys Tracking (colour is rotation-independent) ---
        VaultColor placedVault = next.template.getContainedVault();
        if (placedVault != null && vaultsPlacedInDFS.add(placedVault)) {
            plugin.getLogger().info("Placed " + placedVault + " vault segment (" + next.template.getName() + ") at depth " + currentDepth);
        }
        VaultColor placedKey = next.template.getContainedVaultKey();
        if (placedKey != null && placedKey != VaultColor.BLUE && keysPlacedInDFS.add(placedKey)) {
            plugin.getLogger().info("Placed " + placedKey + " key segment (" + next.template.getName() + ") at depth " + currentDepth);
        }

        // --- Recurse over the new segment's ROTATED outgoing exits ---
        List<RelativeEntryPoint> outgoingExits = new ArrayList<>(nextPlacedSegment.getRotatedEntryPoints());
        Collections.shuffle(outgoingExits, random);
        for (RelativeEntryPoint outgoingEntryPoint : outgoingExits) {
            // Don't go back through the entry point we just came from
            if (outgoingEntryPoint.getDirection() != requiredDirection) {
                generatePathRecursive(nextPlacedSegment, outgoingEntryPoint, placedSegments, occupiedOrigins, currentDepth + 1);
            }
        }
    }

    /**
     * Every rotated entry point across {@code placedSegments} that no connection was made through.
     *
     * <p>Segment templates carve their doorways as open 3x4 holes, and the DFS attaches a
     * neighbour to only some of them -- the hub template alone declares nine. The leftovers open
     * onto nothing, so they are returned here for {@code DoorManager} to seal as plain wall rather
     * than dress as a door that costs a rusty key and leads nowhere.
     *
     * @param placedSegments The segments of a finished layout, with blueprint-relative origins.
     * @param doorways       The connections the DFS made.
     * @return One Doorway per unattached opening, in blueprint-relative space.
     */
    @NotNull
    static List<Doorway> findUnusedOpenings(@NotNull List<PlacedSegment> placedSegments,
                                            @NotNull List<Doorway> doorways) {
        // Keyed on BlockVector3, not Bukkit Vector: these are whole-block cells, and BlockVector3
        // has exact integer equals/hashCode where Vector compares doubles with an epsilon.
        Set<BlockVector3> connected = new HashSet<>();
        for (Doorway doorway : doorways) {
            Vector pos = doorway.getRelativePosition();
            connected.add(BlockVector3.at(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
        }

        List<Doorway> unused = new ArrayList<>();
        for (PlacedSegment placedSegment : placedSegments) {
            Vector segmentOrigin = placedSegment.getWorldOrigin().toVector();
            for (RelativeEntryPoint ep : placedSegment.getRotatedEntryPoints()) {
                BlockVector3 pos = ep.getRelativePosition();
                Vector cell = segmentOrigin.clone().add(new Vector(pos.x(), pos.y(), pos.z()));
                BlockVector3 key = BlockVector3.at(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
                if (!connected.contains(key)) {
                    unused.add(new Doorway(cell, ep.getDirection()));
                }
            }
        }
        return unused;
    }

    /** A chosen segment template plus the Y rotation (0..3) to apply when placing it. */
    private static final class Placement {
        final Segment template;
        final int steps;
        Placement(Segment template, int steps) { this.template = template; this.steps = steps; }
    }

    /** The rotated entry of {@code template} (under {@code steps}) that faces {@code dir}, or null. */
    @Nullable
    private RelativeEntryPoint rotatedEntryByDirection(@NotNull Segment template, int steps, @NotNull Direction dir) {
        BlockVector3 size = template.getSize();
        for (RelativeEntryPoint ep : template.getEntryPoints()) {
            if (SegmentRotation.rotateDirection(ep.getDirection(), steps) == dir) {
                return new RelativeEntryPoint(
                        SegmentRotation.rotatePoint(ep.getRelativePosition(), steps, size),
                        dir);
            }
        }
        return null;
    }

    /**
     * Finds the first segment template with type HUB.
     *
     * @return The hub Segment template, or null if none is found.
     */
    @Nullable
    private Segment findHubTemplate() {
        if (availableSegments == null || availableSegments.isEmpty()) {
            return null;
        }
        for (Segment segment : availableSegments) {
            // Check type, ensuring type is not null
            if (segment.getType() != null && segment.getType() == SegmentType.HUB) {
                return segment;
            }
        }
        return null; // No segment with type HUB found
    }

    /**
     * Selects a suitable segment template to connect to the current path.
     * <p>
     * Placement is <b>opportunistic and branch-agnostic</b>: any not-yet-placed vault or key whose depth
     * range includes {@code currentDepth} is placed on whichever branch reaches it first via a connecting
     * direction (forced at the range max, higher chance just below it, a normal chance within range).
     * Vault/key segments are otherwise kept out of the filler pool so they only enter through this
     * prioritized path. Returns null when nothing connects here (the branch dead-ends and DFS backtracks).
     *
     * @param requiredDirection The direction the new segment needs an entry point for (opposite of the connection).
     * @param currentDepth      The current depth in the dungeon, used for depth-gated vault/key placement.
     * @return A suitable {template, rotation} placement, or null if none is found.
     */
    @Nullable
    private Placement selectNextSegment(
            @NotNull Direction requiredDirection,
            int currentDepth
            ) {

        // 1. All {template, rotation} placements that can physically connect here — a segment fits if
        //    SOME rotation gives it an entry facing requiredDirection.
        List<Placement> candidates = new ArrayList<>();
        for (Segment s : availableSegments) {
            if (s.getType() == SegmentType.HUB) continue; // Cannot place another hub
            for (int steps = 0; steps < 4; steps++) {
                if (rotatedEntryByDirection(s, steps, requiredDirection) != null) {
                    candidates.add(new Placement(s, steps));
                }
            }
        }
        if (candidates.isEmpty()) {
            return null; // Nothing can connect, even rotated
        }

        // 2. Opportunistic vault placement — any missing vault whose range includes this depth.
        for (VaultColor color : VaultColor.values()) {
            if (vaultsPlacedInDFS.contains(color)) continue;
            MinMax range = VAULT_DEPTH_RANGES.get(color);
            if (range == null || currentDepth < range.min || currentDepth > range.max) continue;
            List<Placement> vaultCandidates = candidates.stream()
                    .filter(p -> p.template.getContainedVault() == color)
                    .collect(Collectors.toList());
            if (vaultCandidates.isEmpty()) continue;
            if (shouldPlace(currentDepth, range, VAULT_SPAWN_CHANCE_NORMAL, VAULT_SPAWN_CHANCE_HIGH)) {
                plugin.getLogger().finest("Placing " + color + " vault at depth " + currentDepth);
                return vaultCandidates.get(random.nextInt(vaultCandidates.size()));
            }
        }

        // 3. Opportunistic key placement — RED/GREEN/GOLD (blue key is hub metadata, placed separately).
        for (VaultColor color : new VaultColor[]{VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD}) {
            if (keysPlacedInDFS.contains(color)) continue;
            MinMax range = KEY_DEPTH_RANGES.get(color);
            if (range == null || currentDepth < range.min || currentDepth > range.max) continue;
            List<Placement> keyCandidates = candidates.stream()
                    .filter(p -> p.template.getContainedVaultKey() == color)
                    .collect(Collectors.toList());
            if (keyCandidates.isEmpty()) continue;
            if (shouldPlace(currentDepth, range, KEY_SPAWN_CHANCE_NORMAL, KEY_SPAWN_CHANCE_HIGH)) {
                // Prefer a PUZZLE room if one exists (future-proof); today key rooms are plain END rooms.
                List<Placement> puzzle = keyCandidates.stream()
                        .filter(p -> p.template.getType() == SegmentType.PUZZLE)
                        .collect(Collectors.toList());
                List<Placement> pick = puzzle.isEmpty() ? keyCandidates : puzzle;
                plugin.getLogger().finest("Placing " + color + " key at depth " + currentDepth);
                return pick.get(random.nextInt(pick.size()));
            }
        }

        // 4. Filler pool: never a vault, and never a non-blue key (those enter only via 2/3 above).
        candidates.removeIf(p -> p.template.getContainedVault() != null);
        candidates.removeIf(p -> p.template.getContainedVaultKey() != null && p.template.getContainedVaultKey() != VaultColor.BLUE);
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Depth-gated placement decision shared by vaults and keys: forced at (or past) the range max, a
     * higher chance one depth below the max, and a normal chance elsewhere within range.
     */
    private boolean shouldPlace(int currentDepth, @NotNull MinMax range, double normalChance, double highChance) {
        if (currentDepth >= range.max) return true;
        if (currentDepth == range.max - 1) return random.nextDouble() < highChance;
        return random.nextDouble() < normalChance;
    }

    @NotNull
    private BlockVector3 calculatePlacementOrigin(
            @NotNull BlockVector3 currentSegmentOrigin,
            @NotNull RelativeEntryPoint connectionFrom,
            @NotNull RelativeEntryPoint connectionTo) {
        // Get the relative positions from the entry points
        BlockVector3 currentConnectionRelativePos = connectionFrom.getRelativePosition();
        BlockVector3 nextConnectionRelativePos = connectionTo.getRelativePosition();

        // Calculate the absolute position of the connection point in the blueprint's relative space
        BlockVector3 absoluteConnectionPoint = currentSegmentOrigin.add(currentConnectionRelativePos);

        // Calculate the origin of the new segment by subtracting its connection point's relative position
        // from the absolute connection point. This aligns connectionTo with absoluteConnectionPoint.
        return absoluteConnectionPoint.subtract(nextConnectionRelativePos);
    }

    /**
     * Checks if placing a segment with the given template at the potential origin would cause a collision
     * with any already placed segments using bounding box intersection. Also performs a quick origin check.
     *
     * @param potentialOrigin   The potential relative origin (BlockVector3) for the new segment.
     * @param newSegmentTemplate The template of the segment to be placed.
     * @param occupiedOrigins   The set of already occupied relative origins (for fast check).
     * @param placedSegments    The list of segments already placed (for bounding box checks).
     * @return true if a collision is detected, false otherwise.
     */
    private boolean checkCollision(
            @NotNull BlockVector3 potentialOrigin,
            @NotNull Segment newSegmentTemplate,
            int rotationSteps,
            @NotNull Set<BlockVector3> occupiedOrigins,
            @NotNull List<PlacedSegment> placedSegments) {

        // 1. Basic Origin Check (Fast Exit)
        if (occupiedOrigins.contains(potentialOrigin)) {
             // plugin.getLogger().finest("Collision detected (Origin): " + potentialOrigin); // Debug logging
             return true; // Another segment already starts exactly here
        }

        // 2. Advanced Bounding Box Check (More Accurate, Slower)
        // Connected segments meet at a shared doorway plane: the connecting entries sit on each
        // segment's boundary face (e.g. a SOUTH entry at z=size-1 aligned with the neighbour's NORTH
        // entry at z=0), so the two boxes touch on that plane by design. Use a STRICT overlap test so
        // face-touching is allowed and only real volumetric overlap (interiors intersecting) counts as
        // a collision — Area.intersects is inclusive and would reject every legitimate connection.
        Area potentialBounds = calculatePotentialBounds(newSegmentTemplate, rotationSteps, potentialOrigin);
        Location pMin = potentialBounds.getMinPoint();
        Location pMax = potentialBounds.getMaxPoint();
        for (PlacedSegment existingSegment : placedSegments) {
            Area existingBounds = existingSegment.getWorldBounds();
            Location eMin = existingBounds.getMinPoint();
            Location eMax = existingBounds.getMaxPoint();

            boolean overlap =
                    pMin.getX() < eMax.getX() && pMax.getX() > eMin.getX() &&
                    pMin.getY() < eMax.getY() && pMax.getY() > eMin.getY() &&
                    pMin.getZ() < eMax.getZ() && pMax.getZ() > eMin.getZ();
            if (overlap) {
                 plugin.getLogger().finest("Collision detected (Bounds): " + newSegmentTemplate.getName()
                     + " at " + potentialOrigin + " overlaps " + existingSegment.getName()
                     + " at " + existingSegment.getWorldOrigin().toVector());
                 return true; // Interiors overlap
            }
        }

        // If no collision detected by either check
        return false;
    }

    /**
     * Calculates the potential bounding Area (using relative coordinates) for a segment if placed at a given origin.
     * Needed for advanced collision detection. The Locations in the returned Area will have a null world.
     *
     * @param segmentTemplate The segment template.
     * @param relativeOrigin  The relative origin (BlockVector3) where the segment would be placed.
     * @return An Area object representing the relative bounds.
     */
    @NotNull
    private Area calculatePotentialBounds(@NotNull Segment segmentTemplate, int rotationSteps, @NotNull BlockVector3 relativeOrigin) {
        // Rotated footprint (90°/270° swap X and Z).
        BlockVector3 size = SegmentRotation.rotateSize(segmentTemplate.getSize(), rotationSteps);
        if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            // Handle invalid size, maybe return a zero-size area at origin or throw exception
            plugin.getLogger().warning("Calculating potential bounds for segment " + segmentTemplate.getName() + " with invalid size: " + size);
            Location zeroLoc = new Location(null, relativeOrigin.x(), relativeOrigin.y(), relativeOrigin.z());
            return new Area(zeroLoc, zeroLoc); // Zero-size area
        }

        // Min corner is the relative origin itself
        BlockVector3 minCornerBV3 = relativeOrigin;
        // Max corner is relative origin + size - 1 (inclusive)
        BlockVector3 maxCornerBV3 = relativeOrigin.add(size.subtract(1, 1, 1));

        // Convert to relative Locations (null world)
        Location relMinLoc = new Location(null, minCornerBV3.x(), minCornerBV3.y(), minCornerBV3.z());
        Location relMaxLoc = new Location(null, maxCornerBV3.x(), maxCornerBV3.y(), maxCornerBV3.z());

        // Create and return the Area
        return new Area(relMinLoc, relMaxLoc);
    }

     /**
      * Calculates the minimum relative bounds vector based on placed segments.
      * Iterates through all segments and finds the lowest X, Y, and Z coordinates
      * reached by any segment's origin corner.
      * Called after DFS is complete.
      * @param placedSegments The final list of placed segments in the blueprint.
      * @return The minimum corner Vector relative to the blueprint origin.
      */
     @NotNull
    private Vector calculateRelativeMinBounds(@NotNull List<PlacedSegment> placedSegments) {
        // Handle empty list case, although attemptGeneration should prevent this
        if (placedSegments == null || placedSegments.isEmpty()) {
             plugin.getLogger().warning("calculateRelativeMinBounds called with empty segment list.");
             return new Vector(0, 0, 0); // Or throw exception
        }

        // Initialize min coordinates to the largest possible double value
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        // Iterate through each placed segment
        for (PlacedSegment segment : placedSegments) {
            // Get the relative origin vector of this segment (world is null)
            // This vector represents the minimum corner of this segment
            Vector origin = segment.getWorldOrigin().toVector();

            // Update the overall minimums if this segment's origin is lower
            minX = Math.min(minX, origin.getX());
            minY = Math.min(minY, origin.getY());
            minZ = Math.min(minZ, origin.getZ());
        }

        // Return the vector representing the overall minimum corner
        return new Vector(minX, minY, minZ);
    }

    /**
     * Calculates the maximum relative bounds vector based on placed segments.
     * Iterates through all segments and finds the highest X, Y, and Z coordinates
     * reached by any segment's corner.
     * Called after DFS is complete.
     * @param placedSegments The final list of placed segments in the blueprint.
     * @return The maximum corner Vector relative to the blueprint origin.
     */
    @NotNull
    private Vector calculateRelativeMaxBounds(@NotNull List<PlacedSegment> placedSegments) {
        // Handle empty list case, although attemptGeneration should prevent this
        if (placedSegments == null || placedSegments.isEmpty()) {
             plugin.getLogger().warning("calculateRelativeMaxBounds called with empty segment list.");
             return new Vector(0, 0, 0); // Or throw exception
        }

        // Initialize max coordinates to the smallest possible double value
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        // Iterate through each placed segment
        for (PlacedSegment segment : placedSegments) {
            // Get the relative origin vector of this segment (world is null)
            Vector origin = segment.getWorldOrigin().toVector();
            // Get the size of the segment template
            BlockVector3 size = segment.getSegmentTemplate().getSize();

            // Calculate the maximum corner coordinates for this segment
            // Remember size includes the origin block, so add size-1 to origin coord
            double segMaxX = origin.getX() + size.x() - 1;
            double segMaxY = origin.getY() + size.y() - 1;
            double segMaxZ = origin.getZ() + size.z() - 1;

            // Update the overall maximums if this segment extends further
            maxX = Math.max(maxX, segMaxX);
            maxY = Math.max(maxY, segMaxY);
            maxZ = Math.max(maxZ, segMaxZ);
        }

        // Return the vector representing the overall maximum corner
        return new Vector(maxX, maxY, maxZ);
    }


    /**
     * Picks the blueprint-relative safe exit from the placed segments.
     *
     * <p>A marker on a {@link SegmentType#HUB} template wins over one on any other segment, since the
     * exit belongs in the hub by convention. Among equally-ranked segments the first found wins, the
     * same first-wins rule {@link #consolidateFeatureLocations} applies to vault and key markers.
     *
     * @param placedSegments The segments making up the blueprint.
     * @return The relative safe exit location, or null if no template defines one.
     */
    @Nullable
    static Vector selectSafeExitRelativeLocation(@NotNull List<PlacedSegment> placedSegments) {
        Vector best = null;
        boolean bestFromHub = false;

        for (PlacedSegment placedSegment : placedSegments) {
            Segment template = placedSegment.getSegmentTemplate();
            if (template == null) continue;
            BlockVector3 offset = template.getSafeExitOffset();
            if (offset == null) continue;

            boolean fromHub = template.getType() == SegmentType.HUB;
            if (best != null && !(fromHub && !bestFromHub)) continue;

            BlockVector3 rot = placedSegment.getRotatedOffset(offset);
            best = placedSegment.getWorldOrigin().toVector().clone()
                    .add(new Vector(rot.x(), rot.y(), rot.z()));
            bestFromHub = fromHub;
        }
        return best;
    }

    /**
     * Picks the blueprint-relative base of the visual sand-timer column (the {@code TIMER} marker), with
     * a HUB template winning over any other segment. Null if no template defines one.
     */
    @Nullable
    static Vector selectTimerBaseRelativeLocation(@NotNull List<PlacedSegment> placedSegments) {
        Vector best = null;
        boolean bestFromHub = false;
        for (PlacedSegment placedSegment : placedSegments) {
            Segment template = placedSegment.getSegmentTemplate();
            if (template == null) continue;
            BlockVector3 offset = template.getTimerOffset();
            if (offset == null) continue;

            boolean fromHub = template.getType() == SegmentType.HUB;
            if (best != null && !(fromHub && !bestFromHub)) continue;

            BlockVector3 rot = placedSegment.getRotatedOffset(offset);
            best = placedSegment.getWorldOrigin().toVector().clone()
                    .add(new Vector(rot.x(), rot.y(), rot.z()));
            bestFromHub = fromHub;
        }
        return best;
    }

    /**
     * Collects the blueprint-relative per-player spawn points from {@code PLAYER_SPAWN} markers.
     * A HUB template's spawns win: if any HUB defines spawns those are used exclusively; otherwise
     * every segment's spawns are gathered. Empty when no template defines any (callers fall back to
     * the hub location).
     */
    @NotNull
    static List<Vector> selectPlayerSpawnRelativeLocations(@NotNull List<PlacedSegment> placedSegments) {
        List<Vector> hubSpawns = new ArrayList<>();
        List<Vector> otherSpawns = new ArrayList<>();
        for (PlacedSegment placedSegment : placedSegments) {
            Segment template = placedSegment.getSegmentTemplate();
            if (template == null) continue;
            boolean fromHub = template.getType() == SegmentType.HUB;
            Vector origin = placedSegment.getWorldOrigin().toVector();
            for (BlockVector3 offset : template.getPlayerSpawnOffsets()) {
                BlockVector3 rot = placedSegment.getRotatedOffset(offset);
                Vector abs = origin.clone().add(new Vector(rot.x(), rot.y(), rot.z()));
                (fromHub ? hubSpawns : otherSpawns).add(abs);
            }
        }
        return !hubSpawns.isEmpty() ? hubSpawns : otherSpawns;
    }

    /**
     * Collects the blueprint-relative sand deposit points from {@code TIMER_DEPOSIT} markers — the cells
     * players place carried sand into to feed their team's timer. Like the player spawns, a HUB template's
     * points win outright: if any HUB defines deposit points those are used exclusively, otherwise every
     * segment's are gathered. Empty when no template defines any, in which case a team simply has nowhere
     * to spend sand (generation still succeeds; {@link com.clarkson.sot.utils.SandManager} logs it).
     */
    @NotNull
    static List<Vector> selectSandTimerRelativeLocations(@NotNull List<PlacedSegment> placedSegments) {
        List<Vector> hubDeposits = new ArrayList<>();
        List<Vector> otherDeposits = new ArrayList<>();
        for (PlacedSegment placedSegment : placedSegments) {
            Segment template = placedSegment.getSegmentTemplate();
            if (template == null) continue;
            boolean fromHub = template.getType() == SegmentType.HUB;
            Vector origin = placedSegment.getWorldOrigin().toVector();
            for (BlockVector3 offset : template.getSandTimerOffsets()) {
                BlockVector3 rot = placedSegment.getRotatedOffset(offset);
                Vector abs = origin.clone().add(new Vector(rot.x(), rot.y(), rot.z()));
                (fromHub ? hubDeposits : otherDeposits).add(abs);
            }
        }
        return !hubDeposits.isEmpty() ? hubDeposits : otherDeposits;
    }

    /**
     * Iterates through all placed segments in the completed blueprint layout and consolidates
     * the relative locations of all defined features (vaults, keys, spawns) into the final maps/lists
     * used to construct the DungeonBlueprint object. Converts relative BlockVector3 offsets to relative Bukkit Vectors.
     * Called after DFS is complete.
     *
     * @param placedSegments             The final list of PlacedSegment objects in the blueprint.
     * @param vaultMarkerRelativeLocations (Out) Map to populate with vault color -> relative vault marker location (Vector).
     * @param keySpawnRelativeLocations    (Out) Map to populate with vault color -> relative key spawn location (Vector).
     * @param sandSpawnRelativeLocations   (Out) List to populate with relative sand spawn locations (Vector).
     * @param coinSpawnRelativeLocations   (Out) List to populate with relative coin spawn locations (Vector).
     * @param itemSpawnRelativeLocations   (Out) List to populate with relative item spawn locations (Vector).
     */
    private void consolidateFeatureLocations(
            @NotNull List<PlacedSegment> placedSegments,
            @NotNull Map<VaultColor, Vector> vaultMarkerRelativeLocations, // Map to populate
            @NotNull Map<VaultColor, Vector> keySpawnRelativeLocations,    // Map to populate
            @NotNull List<Vector> sandSpawnRelativeLocations,             // List to populate
            @NotNull List<Vector> coinSpawnRelativeLocations,             // List to populate
            @NotNull List<Vector> itemSpawnRelativeLocations              // List to populate
            ) {

        // Clear output collections before populating
        vaultMarkerRelativeLocations.clear();
        keySpawnRelativeLocations.clear();
        sandSpawnRelativeLocations.clear();
        coinSpawnRelativeLocations.clear();
        itemSpawnRelativeLocations.clear();

        plugin.getLogger().fine("Consolidating feature locations from " + placedSegments.size() + " placed segments...");

        // Iterate through each segment placed in the blueprint
        for (PlacedSegment placedSegment : placedSegments) {
            Segment template = placedSegment.getSegmentTemplate();
            // Get the origin of this segment RELATIVE to the blueprint's 0,0,0
            Vector segmentRelativeOrigin = placedSegment.getWorldOrigin().toVector();

            // --- Consolidate Vault Marker ---
            VaultColor vaultColor = template.getContainedVault();
            BlockVector3 vaultOffset = template.getVaultOffset(); // Offset relative to segment origin
            if (vaultColor != null && vaultOffset != null) {
                // Calculate final relative position: Segment Origin + rotated offset
                Vector vaultRelativePos = addRotated(segmentRelativeOrigin, placedSegment, vaultOffset);
                // Only add if this color hasn't been placed yet (first one found wins)
                if (vaultMarkerRelativeLocations.putIfAbsent(vaultColor, vaultRelativePos) == null) {
                     plugin.getLogger().finer("Consolidated " + vaultColor + " vault marker location: " + vaultRelativePos);
                } else {
                     warnOncePerGeneration("dup-vault:" + vaultColor + ":" + template.getName(),
                             "Duplicate vault marker found for color " + vaultColor + " in segment " + template.getName() + ". Keeping first one found.");
                }
            }

            // --- Consolidate Key Spawn ---
            VaultColor keyColor = template.getContainedVaultKey();
            BlockVector3 keyOffset = template.getKeyOffset(); // Offset relative to segment origin
            // BLUE is consolidated like every other colour. It used to be skipped here on the
            // assumption that VaultManager placed it relative to the hub, but no such code ever
            // existed, so the blue key never spawned and the blue vault could not be opened. The
            // hub is itself a placed segment, so a BLUE key marker on it flows through this path.
            if (keyColor != null && keyOffset != null) {
                // Calculate final relative position: Segment Origin + rotated offset
                Vector keyRelativePos = addRotated(segmentRelativeOrigin, placedSegment, keyOffset);
                // Only add if this color hasn't been placed yet
                if (keySpawnRelativeLocations.putIfAbsent(keyColor, keyRelativePos) == null) {
                    plugin.getLogger().finer("Consolidated " + keyColor + " key spawn location: " + keyRelativePos);
                } else {
                     warnOncePerGeneration("dup-key:" + keyColor + ":" + template.getName(),
                             "Duplicate key spawn found for color " + keyColor + " in segment " + template.getName() + ". Keeping first one found.");
                }
            }

            // --- Consolidate Sand Spawns ---
            List<BlockVector3> sandOffsets = template.getSandSpawnLocations();
            if (sandOffsets != null) {
                for (BlockVector3 offset : sandOffsets) {
                    if (offset != null) {
                        sandSpawnRelativeLocations.add(addRotated(segmentRelativeOrigin, placedSegment, offset));
                    }
                }
            }

            // --- Consolidate Coin Spawns ---
            List<BlockVector3> coinOffsets = template.getCoinSpawnLocations();
             if (coinOffsets != null) {
                for (BlockVector3 offset : coinOffsets) {
                     if (offset != null) {
                         coinSpawnRelativeLocations.add(addRotated(segmentRelativeOrigin, placedSegment, offset));
                     }
                 }
             }

            // --- Consolidate Item Spawns ---
            List<BlockVector3> itemOffsets = template.getItemSpawnLocations();
             if (itemOffsets != null) {
                for (BlockVector3 offset : itemOffsets) {
                     if (offset != null) {
                         itemSpawnRelativeLocations.add(addRotated(segmentRelativeOrigin, placedSegment, offset));
                     }
                 }
             }
        }
        plugin.getLogger().fine("Feature consolidation complete.");
    }

    /** origin + the placement's rotated version of a template offset, as a Bukkit Vector. */
    private static Vector addRotated(@NotNull Vector origin, @NotNull PlacedSegment placed, @NotNull BlockVector3 offset) {
        BlockVector3 r = placed.getRotatedOffset(offset);
        return origin.clone().add(new Vector(r.x(), r.y(), r.z()));
    }

    // Helper class for depth ranges
    private static class MinMax {
        final int min;
        final int max;
        MinMax(int min, int max) { this.min = min; this.max = max; }
    }
}
