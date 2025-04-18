package com.clarkson.sot.dungeon;

// --- Imports (Ensure all needed imports are present) ---
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;
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
    private final Random random;
    private static final int MAX_DEPTH = 10; // Example
    private static final int MAX_TOTAL_SEGMENTS = 50; // Example
    // Track placed vaults/keys during generation
    private Set<VaultColor> keysPlacedInDFS;
    private Set<VaultColor> vaultsPlacedInDFS;

    // --- Generation Configuration ---
    // Example Depth Ranges for Vaults (Min inclusive, Max inclusive)
    private static final Map<VaultColor, MinMax> VAULT_DEPTH_RANGES = Map.of(
        VaultColor.GREEN, new MinMax(3, 6),    // Green near hub
        VaultColor.BLUE,  new MinMax(7, 10),
        VaultColor.RED,   new MinMax(10, 14),
        VaultColor.GOLD,  new MinMax(13, 15)   // Gold furthest
    );
    // Example Depth Ranges for Keys
     private static final Map<VaultColor, MinMax> KEY_DEPTH_RANGES = Map.of(
         // Blue key handled separately
         VaultColor.GREEN, new MinMax(5, 9),
         VaultColor.RED,   new MinMax(4, 8), // Puzzle room for Red Key
         VaultColor.GOLD,  new MinMax(8, 12) // Lava parkour for Gold Key
     );

    // Probabilities (0.0 to 1.0)
    private static final double VAULT_SPAWN_CHANCE_NORMAL = 0.20; // 20% chance within range (but before last 2 depths)
    private static final double VAULT_SPAWN_CHANCE_HIGH = 0.50;  // 50% chance at depth MAX-1
    // Similar probabilities could be defined for keys


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
        // throw new UnsupportedOperationException("Constructor implementation not provided."); // Remove throw if implementing
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
        int maxRetries = 5; // Example retry limit
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            plugin.getLogger().info("Starting dungeon layout generation attempt " + attempt + "/" + maxRetries + "...");
            DungeonBlueprint blueprint = attemptGeneration();
            if (blueprint != null) {
                plugin.getLogger().info("Dungeon layout generated successfully on attempt " + attempt);
                return blueprint; // Success
            }
            plugin.getLogger().warning("Dungeon generation attempt " + attempt + " failed validation or generation. Retrying...");
        }
        plugin.getLogger().severe("Failed to generate a valid dungeon layout after " + maxRetries + " attempts.");
        return null; // Failed after all retries
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


        // --- Start DFS for each Colored Branch + Key Branches ---
        List<RelativeEntryPoint> hubExits = new ArrayList<>(hubTemplate.getEntryPoints());
        Collections.shuffle(hubExits, random);

        // Define branches needed: 3 Vaults (R, G, Gold) + potentially dedicated key branches or general filler
        VaultColor[] targetVaults = {VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD, VaultColor.BLUE};
        // Maybe add BLUE here if you want a dedicated Blue Vault branch? Or let it be placed opportunistically?
        // Let's assume for now we need dedicated branches for R, G, Gold vaults.
        int vaultBranchIndex = 0;
        List<VaultColor> branchesToGenerate = new ArrayList<>(Arrays.asList(targetVaults));
        // Consider adding null entries to branchesToGenerate if you want non-vault specific paths for keys

        for (RelativeEntryPoint hubEntryPoint : hubExits) {
            VaultColor branchColor = null;
            if (vaultBranchIndex < branchesToGenerate.size()) {
                branchColor = branchesToGenerate.get(vaultBranchIndex++);
                plugin.getLogger().info("Starting DFS from hub exit " + hubEntryPoint.getDirection() + " for branch: " + branchColor);
            } else {
                plugin.getLogger().info("Starting DFS from hub exit " + hubEntryPoint.getDirection() + " for general/key path (targetBranchColor=null).");
            }
            generatePathRecursive(hubPlacedSegment, hubEntryPoint, placedSegments, occupiedOrigins, 1, branchColor);
        }


        // --- Post-DFS: Consolidate, Calculate Bounds, Validate ---
        if (placedSegments.size() <= 1) { /* ... log warning ... */ return null; }

        // Consolidate features (this populates the maps based on placed segments)
        consolidateFeatureLocations(placedSegments, vaultMarkerRelativeLocations, keySpawnRelativeLocations, sandSpawnRelativeLocations, coinSpawnRelativeLocations, itemSpawnRelativeLocations);

        // Calculate Bounds
        Vector relativeMinVec = calculateRelativeMinBounds(placedSegments);
        Vector relativeMaxVec = calculateRelativeMaxBounds(placedSegments);
        Location relMinLoc = new Location(null, relativeMinVec.getX(), relativeMinVec.getY(), relativeMinVec.getZ());
        Location relMaxLoc = new Location(null, relativeMaxVec.getX(), relativeMaxVec.getY(), relativeMaxVec.getZ());
        Area blueprintBounds = new Area(relMinLoc, relMaxLoc);

        // --- Validate Required Vaults & Keys ---
        // Validation relies on consolidateFeatureLocations having correctly populated the maps
        boolean valid = true;
        VaultColor[] requiredKeys = {VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD}; // Blue key is not placed by DFS
        VaultColor[] requiredVaults = {VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD}; // All 4 vaults must be placed by DFS

        for (VaultColor requiredColor : requiredVaults) {
            if (!vaultMarkerRelativeLocations.containsKey(requiredColor)) {
                plugin.getLogger().warning("Validation Failed: Missing vault marker location for color: " + requiredColor);
                valid = false;
            }
        }
        for (VaultColor requiredColor : requiredKeys) {
            if (!keySpawnRelativeLocations.containsKey(requiredColor)) {
                plugin.getLogger().warning("Validation Failed: Missing key spawn location for color: " + requiredColor);
                valid = false;
            }
        }

        if (!valid) {
            return null; // Validation failed for this attempt
        }

        // --- Create and Return Blueprint ---
        return new DungeonBlueprint(
                placedSegments, hubRelativeLocation, vaultMarkerRelativeLocations, keySpawnRelativeLocations,
                sandSpawnRelativeLocations, coinSpawnRelativeLocations, itemSpawnRelativeLocations,
                blueprintBounds
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
     * @param targetBranchColor The VaultColor this branch is intended to lead to, or null if it's a general/key path.
     */
    private void generatePathRecursive(
            @NotNull PlacedSegment currentSegment,
            @NotNull RelativeEntryPoint connectionPoint,
            @NotNull List<PlacedSegment> placedSegments,
            @NotNull Set<BlockVector3> occupiedOrigins,
            int currentDepth,
            @Nullable VaultColor targetBranchColor) {

        // --- Base Cases / Termination Conditions ---
        if (currentDepth >= MAX_DEPTH) {
            return; // Reached max depth for this branch
        }
        if (placedSegments.size() >= MAX_TOTAL_SEGMENTS) {
            // Optional: Log warning if hitting total segment limit frequently
            return; // Reached overall dungeon size limit
        }

        // --- Select Next Segment Template ---
        Direction requiredDirection = connectionPoint.getDirection().getOpposite();
        Segment nextSegmentTemplate = selectNextSegment(
            currentSegment.getSegmentTemplate(),
            requiredDirection,
            currentDepth,
            targetBranchColor
        );

        // If no suitable segment found, this path ends (backtrack)
        if (nextSegmentTemplate == null) {
            // plugin.getLogger().finest("DFS dead end at depth " + currentDepth + " from " + currentSegment.getName() + " facing " + connectionPoint.getDirection());
            return;
        }

        // --- Calculate Placement ---
        RelativeEntryPoint nextEntryPoint = nextSegmentTemplate.findEntryPointByDirection(requiredDirection);
        if (nextEntryPoint == null) {
             plugin.getLogger().warning("Segment " + nextSegmentTemplate.getName() + " selected but missing required entry point " + requiredDirection + ". Stopping branch.");
             return; // Should not happen if selectNextSegment filters correctly
        }
        BlockVector3 currentSegmentOrigin = BlockVector3.at(
            currentSegment.getWorldOrigin().toVector().getX(),
            currentSegment.getWorldOrigin().toVector().getY(),
            currentSegment.getWorldOrigin().toVector().getZ()
        ); // Relative origin
        BlockVector3 nextSegmentOrigin = calculatePlacementOrigin(currentSegmentOrigin, connectionPoint, nextEntryPoint);

        // --- Check Collision ---
        if (checkCollision(nextSegmentOrigin, nextSegmentTemplate, occupiedOrigins, placedSegments)) {
            // plugin.getLogger().finest("DFS collision detected for " + nextSegmentTemplate.getName() + " at " + nextSegmentOrigin + ". Stopping branch.");
            return; // Collision detected, stop this branch
        }

        // --- Place Segment ---
        Location relativeNextOriginLoc = new Location(null, nextSegmentOrigin.x(), nextSegmentOrigin.y(), nextSegmentOrigin.z());
        PlacedSegment nextPlacedSegment = new PlacedSegment(nextSegmentTemplate, relativeNextOriginLoc, currentDepth); // Pass currentDepth

        placedSegments.add(nextPlacedSegment);
        occupiedOrigins.add(nextSegmentOrigin);
        // plugin.getLogger().finer("DFS placed segment " + nextSegmentTemplate.getName() + " at relative origin " + nextSegmentOrigin + " (Depth: " + currentDepth + ", Branch: "+ targetBranchColor + ")");

        // --- Update Global Placed Vaults/Keys Tracking ---
        VaultColor placedVault = nextSegmentTemplate.getContainedVault();
        if (placedVault != null) {
            if (vaultsPlacedInDFS.add(placedVault)) { // .add() returns true if the element was not already present
                 plugin.getLogger().info("Placed " + placedVault + " vault segment (" + nextSegmentTemplate.getName() + ") at depth " + currentDepth);
            }
        }
        VaultColor placedKey = nextSegmentTemplate.getContainedVaultKey();
         // We don't track the blue key this way as it's placed specially
        if (placedKey != null && placedKey != VaultColor.BLUE) {
             if (keysPlacedInDFS.add(placedKey)) {
                  plugin.getLogger().info("Placed " + placedKey + " key segment (" + nextSegmentTemplate.getName() + ") at depth " + currentDepth);
             }
        }


        // --- Recursive Calls for New Segment's Outgoing Connections ---
        // Shuffle exits to add more randomness to path exploration order
        List<RelativeEntryPoint> outgoingExits = new ArrayList<>(nextSegmentTemplate.getEntryPoints());
        Collections.shuffle(outgoingExits, random);

        for (RelativeEntryPoint outgoingEntryPoint : outgoingExits) {
            // Don't go back through the entry point we just came from
            if (outgoingEntryPoint.getDirection() != requiredDirection) {
                // Pass the targetBranchColor down the same branch
                generatePathRecursive(nextPlacedSegment, outgoingEntryPoint, placedSegments, occupiedOrigins, currentDepth + 1, targetBranchColor);
            }
        }
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
     * Selects a suitable segment template to connect to the current path based on various criteria,
     * including the target vault color for the current branch and depth-based probabilities.
     *
     * @param previousSegment   The template of the segment being connected *from*.
     * @param requiredDirection The direction the new segment needs an entry point for (opposite of the connection).
     * @param currentDepth      The current depth in the dungeon, used for applying depth-based rules.
     * @param targetBranchColor The VaultColor this branch is intended to lead to, or null. Used for prioritizing segments.
     * @return A suitable Segment template randomly chosen from valid candidates, or null if no suitable segment is found.
     */
    @Nullable
    private Segment selectNextSegment(
            @NotNull Segment previousSegment,
            @NotNull Direction requiredDirection,
            int currentDepth,
            @Nullable VaultColor targetBranchColor
            ) {

        // 1. Filter basic candidates (must connect, not hub, not immediate backtrack maybe)
        List<Segment> candidates = availableSegments.stream()
                .filter(s -> s.getType() != SegmentType.HUB) // Cannot place another hub
                .filter(s -> s.hasEntryPointInDirection(requiredDirection)) // Must have correct entry point
                // Optional: Prevent placing the exact same segment type immediately?
                // .filter(s -> s.getType() != previousSegment.getType() || s.getType() == SegmentType.CORRIDOR)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null; // No segments can physically connect
        }

        // 2. Prioritize Vault Placement if on a Vault Branch
        if (targetBranchColor != null && !vaultsPlacedInDFS.contains(targetBranchColor)) {
            MinMax range = VAULT_DEPTH_RANGES.get(targetBranchColor);
            if (range != null && currentDepth >= range.min && currentDepth <= range.max) {
                // Find segments containing the target vault
                 List<Segment> vaultCandidates = candidates.stream()
                         .filter(s -> s.getContainedVault() == targetBranchColor)
                         .collect(Collectors.toList());

                 if (!vaultCandidates.isEmpty()) {
                     boolean shouldPlaceVault = false;
                     if (currentDepth == range.max) {
                         shouldPlaceVault = true; // Force placement at max depth
                         plugin.getLogger().finest("Attempting forced vault placement for " + targetBranchColor + " at depth " + currentDepth);
                     } else if (currentDepth == range.max - 1 && random.nextDouble() < VAULT_SPAWN_CHANCE_HIGH) {
                         shouldPlaceVault = true; // High chance at depth max-1
                         plugin.getLogger().finest("High chance vault roll succeeded for " + targetBranchColor + " at depth " + currentDepth);
                     } else if (random.nextDouble() < VAULT_SPAWN_CHANCE_NORMAL) {
                         shouldPlaceVault = true; // Normal chance within range
                         plugin.getLogger().finest("Normal chance vault roll succeeded for " + targetBranchColor + " at depth " + currentDepth);
                     }

                     if (shouldPlaceVault) {
                         // Return a random segment from the vault candidates
                         return vaultCandidates.get(random.nextInt(vaultCandidates.size()));
                     }
                 } else {
                     // Log if we are forced to place but have no candidates
                     if (currentDepth == range.max) {
                          plugin.getLogger().warning("Forced vault placement failed for " + targetBranchColor + " at depth " + currentDepth + ": No suitable vault segments found!");
                          // This branch will likely fail validation later, DFS will backtrack naturally if possible
                     }
                 }
            }
            // If vault placement wasn't triggered/possible, remove vault segments for this color from general pool
            // to avoid placing it too early or accidentally.
            final VaultColor finalTarget = targetBranchColor; // Needed for lambda
             candidates.removeIf(s -> s.getContainedVault() == finalTarget);
        }

         // 3. Prioritize Key Placement (if not on a vault branch or vault not placed)
         // Example: Prioritize Red Key in Puzzle Room
         if (!keysPlacedInDFS.contains(VaultColor.RED)) {
             MinMax range = KEY_DEPTH_RANGES.get(VaultColor.RED);
             if (range != null && currentDepth >= range.min && currentDepth <= range.max) {
                 List<Segment> keyCandidates = candidates.stream()
                         .filter(s -> s.getType() == SegmentType.PUZZLE && s.getContainedVaultKey() == VaultColor.RED)
                         .collect(Collectors.toList());
                 if (!keyCandidates.isEmpty()) {
                     // Add probability logic if desired, or just place if found
                     plugin.getLogger().finest("Prioritizing Red Key placement at depth " + currentDepth);
                     return keyCandidates.get(random.nextInt(keyCandidates.size()));
                 }
             }
         }
         // Example: Prioritize Gold Key in Lava Parkour
         if (!keysPlacedInDFS.contains(VaultColor.GOLD)) {
              MinMax range = KEY_DEPTH_RANGES.get(VaultColor.GOLD);
              if (range != null && currentDepth >= range.min && currentDepth <= range.max) {
                  List<Segment> keyCandidates = candidates.stream()
                          .filter(s -> s.getType() == SegmentType.LAVA_PARKOUR && s.getContainedVaultKey() == VaultColor.GOLD)
                          .collect(Collectors.toList());
                  if (!keyCandidates.isEmpty()) {
                      plugin.getLogger().finest("Prioritizing Gold Key placement at depth " + currentDepth);
                      return keyCandidates.get(random.nextInt(keyCandidates.size()));
                  }
              }
          }
         // Add similar logic for Green Key if needed


        // 4. Filter out segments containing *any* vault or key if they shouldn't be placed now
         candidates.removeIf(s -> s.getContainedVault() != null && (targetBranchColor == null || s.getContainedVault() != targetBranchColor)); // Remove vaults not matching branch target
         candidates.removeIf(s -> s.getContainedVaultKey() != null && s.getContainedVaultKey() != VaultColor.BLUE); // Remove segments containing R,G,Gold keys unless prioritized above

        // 5. Final Selection from remaining candidates
        if (candidates.isEmpty()) {
            return null; // No suitable non-vault/non-key segment found
        }
        // Choose randomly from the suitable remaining candidates
        return candidates.get(random.nextInt(candidates.size()));
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
            @NotNull Set<BlockVector3> occupiedOrigins,
            @NotNull List<PlacedSegment> placedSegments) {

        // 1. Basic Origin Check (Fast Exit)
        if (occupiedOrigins.contains(potentialOrigin)) {
             // plugin.getLogger().finest("Collision detected (Origin): " + potentialOrigin); // Debug logging
             return true; // Another segment already starts exactly here
        }

        // 2. Advanced Bounding Box Check (More Accurate, Slower)
        Area potentialBounds = calculatePotentialBounds(newSegmentTemplate, potentialOrigin);
        // Check against all previously placed segments
        for (PlacedSegment existingSegment : placedSegments) {
            // Get the relative bounds of the existing segment
            // PlacedSegment.getWorldBounds() returns an Area with relative Locations (null world) in this blueprint context
            Area existingBounds = existingSegment.getWorldBounds();

            // Check if the potential new bounds intersect with the existing bounds
            if (potentialBounds.intersects(existingBounds)) { // Assumes Area.intersects works correctly with relative coords
                 plugin.getLogger().finest("Collision detected (Bounds): " + newSegmentTemplate.getName()
                     + " at " + potentialOrigin + " intersects with " + existingSegment.getName()
                     + " at " + existingSegment.getWorldOrigin().toVector()); // Debug logging
                 return true; // Volumes overlap
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
    private Area calculatePotentialBounds(@NotNull Segment segmentTemplate, @NotNull BlockVector3 relativeOrigin) {
        // Get segment size
        BlockVector3 size = segmentTemplate.getSize();
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
                // Calculate final relative position: Segment Origin + Offset
                Vector vaultRelativePos = segmentRelativeOrigin.clone().add(new Vector(vaultOffset.x(), vaultOffset.y(), vaultOffset.z()));
                // Only add if this color hasn't been placed yet (first one found wins)
                if (vaultMarkerRelativeLocations.putIfAbsent(vaultColor, vaultRelativePos) == null) {
                     plugin.getLogger().finer("Consolidated " + vaultColor + " vault marker location: " + vaultRelativePos);
                } else {
                     plugin.getLogger().warning("Duplicate vault marker found for color " + vaultColor + " in segment " + template.getName() + ". Keeping first one found.");
                }
            }

            // --- Consolidate Key Spawn ---
            VaultColor keyColor = template.getContainedVaultKey();
            BlockVector3 keyOffset = template.getKeyOffset(); // Offset relative to segment origin
             // Ignore Blue Key consolidation - handled separately by VaultManager relative to Hub
            if (keyColor != null && keyOffset != null && keyColor != VaultColor.BLUE) {
                // Calculate final relative position: Segment Origin + Offset
                Vector keyRelativePos = segmentRelativeOrigin.clone().add(new Vector(keyOffset.x(), keyOffset.y(), keyOffset.z()));
                // Only add if this color hasn't been placed yet
                if (keySpawnRelativeLocations.putIfAbsent(keyColor, keyRelativePos) == null) {
                    plugin.getLogger().finer("Consolidated " + keyColor + " key spawn location: " + keyRelativePos);
                } else {
                     plugin.getLogger().warning("Duplicate key spawn found for color " + keyColor + " in segment " + template.getName() + ". Keeping first one found.");
                }
            }

            // --- Consolidate Sand Spawns ---
            List<BlockVector3> sandOffsets = template.getSandSpawnLocations();
            if (sandOffsets != null) {
                for (BlockVector3 offset : sandOffsets) {
                    if (offset != null) {
                        sandSpawnRelativeLocations.add(segmentRelativeOrigin.clone().add(new Vector(offset.x(), offset.y(), offset.z())));
                    }
                }
            }

            // --- Consolidate Coin Spawns ---
            List<BlockVector3> coinOffsets = template.getCoinSpawnLocations();
             if (coinOffsets != null) {
                for (BlockVector3 offset : coinOffsets) {
                     if (offset != null) {
                         coinSpawnRelativeLocations.add(segmentRelativeOrigin.clone().add(new Vector(offset.x(), offset.y(), offset.z())));
                     }
                 }
             }

            // --- Consolidate Item Spawns ---
            List<BlockVector3> itemOffsets = template.getItemSpawnLocations();
             if (itemOffsets != null) {
                for (BlockVector3 offset : itemOffsets) {
                     if (offset != null) {
                         itemSpawnRelativeLocations.add(segmentRelativeOrigin.clone().add(new Vector(offset.x(), offset.y(), offset.z())));
                     }
                 }
             }
        }
        plugin.getLogger().fine("Feature consolidation complete.");
    }

    // Helper class for depth ranges
    private static class MinMax {
        final int min;
        final int max;
        MinMax(int min, int max) { this.min = min; this.max = max; }
    }
}
