package com.clarkson.sot.dungeon;

// Local project imports
import com.clarkson.sot.utils.Direction;
import com.clarkson.sot.utils.EntryPoint; // Absolute location EntryPoint
import com.clarkson.sot.utils.StructureLoader;

// WorldEdit imports (as before)
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.EditSession;
import com.sk89q.worldedit.world.World; // WorldEdit World

// Bukkit imports
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block; // For placing sand
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

// Java imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages dungeon generation using DFS with colored pathways, vaults, keys, and depth rules.
 */
public class DungeonManager {

    // --- Enums ---
    public enum VaultType { GOLD, RED, GREEN, BLUE, NONE }

    // --- Dependencies & State ---
    private final Plugin plugin;
    private final StructureLoader structureLoader;
    private final Map<String, Segment> segmentTemplates = new HashMap<>();
    private final Random random;
    private final long seed;

    // --- Generation State (reset per generation) ---
    private final Set<VaultType> vaultsPlaced = EnumSet.noneOf(VaultType.class);
    private final Set<VaultType> keysPlaced = EnumSet.noneOf(VaultType.class);
    private final List<PlacedSegment> placedSegments = new ArrayList<>();
    private final Stack<DfsState> expansionStack = new Stack<>(); // DFS stack

    // --- Generation Parameters ---
    private static final String START_ROOM_NAME = "start_room_1";
    private static final int MAX_TRIES_PER_ENTRANCE = 5;
    private static final int MAX_DISTANCE_FROM_START = 200; // Increased distance maybe
    private static final int MAX_SEGMENTS = 50; // Overall max
    private static final String SCHEMATICS_SUBDIR = "schematics";

    // Depth Rules (Min depth can be added for vaults/keys if desired)
    private static final Map<VaultType, Integer> MAX_DEPTH_MAP = Map.of(
        VaultType.NONE, 2,   // Normal/Puzzle branches
        VaultType.GREEN, 3,
        VaultType.BLUE, 7,
        VaultType.RED, 10,
        VaultType.GOLD, 13
    );
     private static final Map<VaultType, Integer> MIN_DEPTH_VAULT_MAP = Map.of(
         // Vaults shouldn't appear too early
         VaultType.GREEN, 3,
         VaultType.BLUE, 5,
         VaultType.RED, 7,
         VaultType.GOLD, 10
     );


    // Internal state for DFS steps
    private record DfsState(EntryPoint exitPoint, int depth, VaultType pathColor) {}

    // Constructor and Loader (similar to before)
    public DungeonManager(Plugin plugin, long seed) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.structureLoader = new StructureLoader(plugin);
        this.seed = seed;
        this.random = new Random(seed);
        plugin.getLogger().info("DungeonManager initialized with seed: " + seed);
    }
     public DungeonManager(Plugin plugin) { this(plugin, System.currentTimeMillis()); }
     public boolean loadSegmentTemplates(File dataDir) { /* ... same as before ... */
        plugin.getLogger().info("Loading segment templates from: " + dataDir.getAbsolutePath());
        segmentTemplates.clear();
        List<Segment> loadedList = structureLoader.loadSegmentTemplates(dataDir);
        if (loadedList.isEmpty()) {
            plugin.getLogger().severe("No segment templates loaded. Dungeon generation will fail.");
            return false;
        }
        loadedList.forEach(template -> segmentTemplates.put(template.getName(), template));
        plugin.getLogger().info("Successfully loaded " + segmentTemplates.size() + " segment templates.");
        return !segmentTemplates.isEmpty();
    }
     public long getSeed() { return seed; }


    /**
     * Generates a dungeon with colored pathways, vaults, and keys.
     */
    public void generateDungeon(Location startRoomOrigin) {
        // --- Reset State ---
        vaultsPlaced.clear();
        keysPlaced.clear();
        placedSegments.clear();
        expansionStack.clear();
        random.setSeed(seed); // Reset random for reproducibility

        // --- Validate ---
        if (segmentTemplates.isEmpty()) { /* ... log error ... */ return; }
        if (startRoomOrigin == null || startRoomOrigin.getWorld() == null) { /* ... log error ... */ return; }

        // --- Setup Start Room ---
        Segment startTemplate = segmentTemplates.get(START_ROOM_NAME);
        if (startTemplate == null) { /* ... log error ... */ return; }

        PlacedSegment startPlacedSegment = new PlacedSegment(startTemplate, startRoomOrigin);
        if (!pasteSegment(startPlacedSegment)) { /* ... log error ... */ return; }
        placedSegments.add(startPlacedSegment);
        plugin.getLogger().info("Placed start room: " + startTemplate.getName());

        // --- Designate Initial Paths & Populate Stack ---
        List<EntryPoint> startExits = startPlacedSegment.getAbsoluteEntryPoints();
        Collections.shuffle(startExits, random);
        List<VaultType> colorsToAssign = new ArrayList<>(List.of(VaultType.GOLD, VaultType.RED, VaultType.BLUE, VaultType.GREEN));

        for (EntryPoint exit : startExits) {
            VaultType assignedColor = VaultType.NONE;
            if (!colorsToAssign.isEmpty()) {
                // Assign a color to this path if available
                assignedColor = colorsToAssign.remove(0); // Assign colors in order of list
                plugin.getLogger().info("Designating path from exit " + exit.getLocation().toVector() + " as " + assignedColor);
            }
            expansionStack.push(new DfsState(exit, 1, assignedColor)); // Depth 1, assign color or NONE
        }
        // Shuffle stack again to mix colored/normal paths initial exploration order
        Collections.shuffle(expansionStack, random);

        // --- Main DFS Loop ---
        int segmentsPlacedCount = 1;
        while (!expansionStack.isEmpty() && segmentsPlacedCount < MAX_SEGMENTS) {
            DfsState currentState = expansionStack.pop();
            EntryPoint currentExit = currentState.exitPoint();
            int currentDepth = currentState.depth();
            VaultType currentPathColor = currentState.pathColor();

            // --- Check Depth Limit ---
            int maxDepth = MAX_DEPTH_MAP.getOrDefault(currentPathColor, 2); // Default to normal path depth
            if (currentDepth >= maxDepth) {
                closeUnusedEntrance(currentExit);
                continue; // Stop exploring this path
            }

            // --- Find and Prioritize Candidate Templates ---
            Direction requiredDir = currentExit.getDirection().getOpposite();
            List<Segment> potentialTemplates = findMatchingTemplates(requiredDir);
            List<Segment> orderedCandidates = prioritizeCandidates(potentialTemplates, currentPathColor, currentDepth);

            // --- Attempt Placement ---
            boolean connected = false;
            int tries = 0;
            while (!connected && tries < MAX_TRIES_PER_ENTRANCE && !orderedCandidates.isEmpty()) {
                tries++;
                Segment candidateTemplate = orderedCandidates.remove(0); // Try highest priority first

                Segment.RelativeEntryPoint candidateEntryPoint = candidateTemplate.findEntryPointByDirection(requiredDir);
                if (candidateEntryPoint == null) continue; // Should not happen

                Location candidateOrigin = calculatePlacementOrigin(currentExit, candidateEntryPoint);
                if (candidateOrigin == null) continue;

                PlacedSegment candidatePlaced = new PlacedSegment(candidateTemplate, candidateOrigin);

                // --- Validate Placement ---
                if (!doesOverlap(candidatePlaced, placedSegments) &&
                    isWithinDistance(startRoomOrigin, candidateOrigin, MAX_DISTANCE_FROM_START))
                {
                    // --- Place Valid Segment ---
                    if (pasteSegment(candidatePlaced)) {
                        placedSegments.add(candidatePlaced);
                        segmentsPlacedCount++;
                        connected = true;

                        // Update placed vaults/keys state
                        if (candidateTemplate.getVaultType() != VaultType.NONE) {
                            vaultsPlaced.add(candidateTemplate.getVaultType());
                            plugin.getLogger().info("Placed VAULT: " + candidateTemplate.getVaultType());
                        }
                        if (candidateTemplate.getKeyForVault() != VaultType.NONE) {
                            keysPlaced.add(candidateTemplate.getKeyForVault());
                             plugin.getLogger().info("Placed KEY for: " + candidateTemplate.getKeyForVault());
                        }

                        // Populate features (sand, items)
                        populateSegmentFeatures(candidatePlaced);

                        // --- Add New Exits to Stack (Apply Pruning) ---
                        List<EntryPoint> newExits = new ArrayList<>();
                        for (EntryPoint newEp : candidatePlaced.getAbsoluteEntryPoints()) {
                            if (!areConnectingEntryPoints(newEp, currentExit)) {
                                newExits.add(newEp);
                            }
                        }

                        // ** Branch Pruning **
                        if (currentPathColor == VaultType.NONE && !newExits.isEmpty()) {
                            // Only allow normal paths to branch once (or not at all if puzzle?)
                             if (candidateTemplate.isPuzzleRoom() || currentDepth >= 1) { // Limit depth 1 or puzzle rooms
                                 // Don't add any exits from shallow normal paths/puzzles
                                 plugin.getLogger().fine("Pruning branches from normal path segment: " + candidateTemplate.getName());
                             } else {
                                 // Allow one random branch from first normal segment
                                 Collections.shuffle(newExits, random);
                                 expansionStack.push(new DfsState(newExits.get(0), currentDepth + 1, VaultType.NONE));
                             }
                        } else if (!newExits.isEmpty()) {
                            // Colored paths: Add all exits, maintaining color
                            Collections.shuffle(newExits, random);
                            for (EntryPoint newExit : newExits) {
                                expansionStack.push(new DfsState(newExit, currentDepth + 1, currentPathColor));
                            }
                        }
                        // Shuffle stack after adding to mix exploration order
                        Collections.shuffle(expansionStack, random);

                        plugin.getLogger().info("Placed segment #" + segmentsPlacedCount + ": " + candidateTemplate.getName() + " (Path: " + currentPathColor + ", Depth: " + (currentDepth+1) + ")");

                    } else { /* Paste failed, log warning */ }
                } else { /* Overlap or distance fail, log fine */ }
            } // End attempts loop

            if (!connected) {
                closeUnusedEntrance(currentExit);
            }
        } // End DFS loop

        // Final cleanup
        while (!expansionStack.isEmpty()) { closeUnusedEntrance(expansionStack.pop()); }
        plugin.getLogger().info("Dungeon generation finished. Total segments placed: " + placedSegments.size());
        // Log missing vaults/keys?
        for (VaultType vt : VaultType.values()) {
            if (vt != VaultType.NONE) {
                if (!vaultsPlaced.contains(vt)) plugin.getLogger().warning("Vault NOT placed: " + vt);
                if (!keysPlaced.contains(vt)) plugin.getLogger().warning("Key NOT placed for: " + vt);
            }
        }
    }

    /**
     * Orders candidate templates based on current path color, depth, and game rules.
     */
    private List<Segment> prioritizeCandidates(List<Segment> candidates, VaultType pathColor, int currentDepth) {
        List<Segment> priorityVault = new ArrayList<>();
        List<Segment> priorityKey = new ArrayList<>();
        List<Segment> priorityPath = new ArrayList<>();
        List<Segment> priorityPuzzle = new ArrayList<>();
        List<Segment> normal = new ArrayList<>();

        int nextDepth = currentDepth + 1;

        for (Segment template : candidates) {
            VaultType templateVault = template.getVaultType();
            VaultType templateKey = template.getKeyForVault();

            // --- Vault Placement Logic ---
            if (templateVault != VaultType.NONE) {
                // Is it the *correct* vault for this path? Is depth sufficient? Is it already placed?
                if (templateVault == pathColor &&
                    nextDepth >= MIN_DEPTH_VAULT_MAP.getOrDefault(pathColor, Integer.MAX_VALUE) &&
                    !vaultsPlaced.contains(templateVault))
                {
                    priorityVault.add(template); continue; // Highest priority
                } else {
                    continue; // Don't place wrong vault or if already placed or too early
                }
            }

            // --- Key Placement Logic ---
             if (templateKey != VaultType.NONE) {
                 // Has this key already been placed?
                 if (!keysPlaced.contains(templateKey)) {
                     // Allow keys anywhere, but maybe prioritize on non-matching colored paths or normal paths?
                     priorityKey.add(template); continue;
                 } else {
                     continue; // Don't place duplicate keys
                 }
             }

            // --- Pathway/Puzzle/Normal Logic ---
            if (pathColor != VaultType.NONE) {
                // On a colored path, prefer matching pathway segments
                // (Need a way to identify pathway segments - assume non-special for now)
                 priorityPath.add(template); // Treat normal segments as pathway segments for now
            } else {
                // On a normal path, prefer puzzle rooms
                if (template.isPuzzleRoom()) {
                    priorityPuzzle.add(template);
                } else {
                    normal.add(template);
                }
            }
        }

        // Shuffle within priorities and combine
        Collections.shuffle(priorityVault, random);
        Collections.shuffle(priorityKey, random);
        Collections.shuffle(priorityPath, random);
        Collections.shuffle(priorityPuzzle, random);
        Collections.shuffle(normal, random);

        List<Segment> ordered = new ArrayList<>();
        ordered.addAll(priorityVault);
        ordered.addAll(priorityKey);
        // On colored path, place path segments before puzzles/normal
        if (pathColor != VaultType.NONE) {
            ordered.addAll(priorityPath);
            ordered.addAll(priorityPuzzle); // Puzzles less likely on main path?
            ordered.addAll(normal);
        } else { // On normal path, place puzzles before normal
             ordered.addAll(priorityPuzzle);
             ordered.addAll(normal);
        }

        return ordered;
    }

    /**
     * Populates features like sand and items after a segment is pasted.
     */
    private void populateSegmentFeatures(PlacedSegment placedSegment) {
        Segment template = placedSegment.getSegmentTemplate();

        // --- Sand Spawning (for Vaults) ---
        if (template.getVaultType() != VaultType.NONE) {
            List<BlockVector3> relativeSandSpawns = template.getSandSpawnLocations();
            if (relativeSandSpawns != null && !relativeSandSpawns.isEmpty()) {
                int sandPlaced = 0;
                int maxSand = 5; // Max sand blocks for this vault room
                List<BlockVector3> shuffledSpawns = new ArrayList<>(relativeSandSpawns);
                Collections.shuffle(shuffledSpawns, random);

                for (BlockVector3 relPos : shuffledSpawns) {
                    if (sandPlaced >= maxSand) break;
                    try {
                        Location absLoc = placedSegment.getAbsoluteLocation(relPos);
                        Block block = absLoc.getBlock();
                        // Check if block is replaceable (e.g., air) before placing sand
                        if (block.getType().isAir() || block.isLiquid() || !block.getType().isSolid()) {
                             block.setType(Material.SAND); // Or SOUL_SAND etc.
                             sandPlaced++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error placing sand at relative pos " + relPos + " for segment " + template.getName(), e);
                    }
                }
                 plugin.getLogger().info("Placed " + sandPlaced + " sand blocks in vault: " + template.getName());
            }
        }

        // --- Item Spawning ---
        List<BlockVector3> relativeItemSpawns = template.getItemSpawnLocations();
        if (relativeItemSpawns != null && !relativeItemSpawns.isEmpty()) {
             plugin.getLogger().info("Processing " + relativeItemSpawns.size() + " item spawn locations for " + template.getName());
             for (BlockVector3 relPos : relativeItemSpawns) {
                 Location absLoc = placedSegment.getAbsoluteLocation(relPos);
                 // TODO: Implement Item Spawning Logic
                 // - Check if block at absLoc is suitable (e.g., AIR for item drop, CHEST for inventory)
                 // - Determine loot based on template type, path color, depth, etc.
                 // - Place item/chest/entity
                 // Example: Place a chest
                 // absLoc.getBlock().setType(Material.CHEST);
                 // Chest chest = (Chest) absLoc.getBlock().getState();
                 // Inventory inv = chest.getBlockInventory();
                 // inv.setItem(random.nextInt(inv.getSize()), new ItemStack(Material.DIAMOND)); // Example item
                 plugin.getLogger().fine("Item spawn placeholder at " + absLoc.toVector() + " for " + template.getName());
             }
        }
    }


    // --- Helper methods (calculatePlacementOrigin, pasteSegment, doesOverlap, findMatchingTemplates, areConnectingEntryPoints, isWithinDistance, closeUnusedEntrance) ---
    // (Keep these methods largely the same as in the previous version, ensuring they use the correct classes like PlacedSegment, EntryPoint, Direction etc.)
    // Minor adjustments might be needed based on exact class definitions.

    // [Include the implementations for the helper methods from the previous version here, verifying parameters and logic]
    // calculatePlacementOrigin - Seems okay
    // pasteSegment - Seems okay
    // doesOverlap - Needs PlacedSegment's getWorldBounds() which uses Area. Ensure Area.intersects works.
    // findMatchingTemplates - Seems okay
    // areConnectingEntryPoints - Seems okay
    // isWithinDistance - Seems okay
    // closeUnusedEntrance - Seems okay

    // [Pasting helper methods from previous version for completeness]
    private Location calculatePlacementOrigin(EntryPoint currentExitPoint, Segment.RelativeEntryPoint candidateEntryPoint) {
        try {
            Location exitLoc = currentExitPoint.getLocation();
            Direction exitDir = currentExitPoint.getDirection();
            Location targetEntryPointLoc = exitLoc.clone().add(exitDir.getBlockVector());
            BlockVector3 candidateEntryRelPos = candidateEntryPoint.getRelativePosition();
            Location candidateOrigin = targetEntryPointLoc.clone().subtract(
                candidateEntryRelPos.getX(), candidateEntryRelPos.getY(), candidateEntryRelPos.getZ()
            );
            if (!candidateOrigin.getWorld().equals(exitLoc.getWorld())) {
                plugin.getLogger().severe("World mismatch during origin calculation!"); return null;
            }
            return candidateOrigin;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error calculating placement origin", e); return null;
        }
    }

    private boolean pasteSegment(PlacedSegment placedSegment) {
        Segment template = placedSegment.getSegmentTemplate();
        Location origin = placedSegment.getWorldOrigin();
        File schematicFile = template.getSchematicFile(plugin.getDataFolder(), SCHEMATICS_SUBDIR);
        if (schematicFile == null || !schematicFile.exists() || !schematicFile.isFile()) {
            plugin.getLogger().severe("Schematic file not found: " + (schematicFile != null ? schematicFile.getPath() : "null")); return false;
        }
        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
             plugin.getLogger().severe("Unknown schematic format: " + schematicFile.getName()); return false;
        }
        try (FileInputStream fis = new FileInputStream(schematicFile); ClipboardReader reader = format.getReader(fis)) {
            clipboard = reader.read();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "IOException reading schematic: " + schematicFile.getName(), e); return false;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error loading clipboard: " + schematicFile.getName(), e); return false;
        }
        if (clipboard == null) {
             plugin.getLogger().severe("Loaded null clipboard: " + schematicFile.getName()); return false;
        }
        try {
            World weWorld = BukkitAdapter.adapt(origin.getWorld());
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BukkitAdapter.asBlockVector(origin))
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
                return true;
            }
        } catch (WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "WorldEditException pasting: " + template.getName(), e); return false;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Unexpected error pasting: " + template.getName(), e); return false;
        }
    }

    private boolean doesOverlap(PlacedSegment newSegment, List<PlacedSegment> activeSegments) {
        for (PlacedSegment placed : activeSegments) {
            if (!newSegment.getWorld().equals(placed.getWorld())) continue;
            // Ensure Area class has a working intersects method for AABB check
            if (newSegment.getWorldBounds().intersects(placed.getWorldBounds())) {
                return true;
            }
        }
        return false;
    }

    private List<Segment> findMatchingTemplates(Direction requiredDirection) {
        return segmentTemplates.values().stream()
                .filter(template -> template.hasEntryPointInDirection(requiredDirection))
                .collect(Collectors.toList());
    }

    private boolean areConnectingEntryPoints(EntryPoint ep1, EntryPoint ep2) {
        if (ep1 == null || ep2 == null || ep1.getLocation() == null || ep2.getLocation() == null || ep1.getDirection() == null || ep2.getDirection() == null) return false;
        if (!ep1.getDirection().equals(ep2.getDirection().getOpposite())) return false;
        Location expectedLoc2 = ep1.getLocation().clone().add(ep1.getDirection().getBlockVector());
        return expectedLoc2.getBlockX() == ep2.getLocation().getBlockX() &&
               expectedLoc2.getBlockY() == ep2.getLocation().getBlockY() &&
               expectedLoc2.getBlockZ() == ep2.getLocation().getBlockZ();
    }

    private boolean isWithinDistance(Location startOrigin, Location segmentOrigin, int maxDistance) {
        if (!startOrigin.getWorld().equals(segmentOrigin.getWorld())) return false;
        return startOrigin.distanceSquared(segmentOrigin) <= (double) maxDistance * maxDistance;
    }

    private void closeUnusedEntrance(EntryPoint unusedEnd) {
         if (unusedEnd == null || unusedEnd.getLocation() == null || unusedEnd.getDirection() == null) return;
         plugin.getLogger().info("[Dungeon] Closing off unused entrance at " + unusedEnd.getLocation().toVector() + " facing " + unusedEnd.getDirection());
         try {
              Location blockLoc = unusedEnd.getLocation().clone().add(unusedEnd.getDirection().getBlockVector());
              blockLoc.getBlock().setType(Material.STONE_BRICKS); // Example
         } catch (Exception e) { /* log warning */ }
     }

}
