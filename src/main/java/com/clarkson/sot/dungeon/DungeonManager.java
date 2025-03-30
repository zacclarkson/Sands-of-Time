package com.clarkson.sot.dungeon;

// Local project imports
import com.clarkson.sot.utils.Direction; // Ensure this has getBlockVector() and getOpposite()
import com.clarkson.sot.utils.EntryPoint; // Absolute location EntryPoint
import com.clarkson.sot.utils.StructureLoader;
import com.clarkson.sot.entities.Area; // Ensure this has a working intersects() method for AABB checks

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
import org.bukkit.block.Block;
import org.bukkit.block.Chest; // For item spawning example
import org.bukkit.inventory.Inventory; // For item spawning example
import org.bukkit.inventory.ItemStack; // For item spawning example
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
    // Ensure this enum exists and matches the values used (GOLD, RED, GREEN, BLUE, NONE)
    public enum VaultType { GOLD, RED, GREEN, BLUE, NONE }

    // --- Dependencies & State ---
    private final Plugin plugin;
    private final StructureLoader structureLoader;
    // Stores loaded templates by name. Ensure Segment class has getVaultType(), getKeyForVault(), isPuzzleRoom() getters.
    private final Map<String, Segment> segmentTemplates = new HashMap<>();
    private final Random random;
    private final long seed;

    // --- Generation State (reset per generation) ---
    private final Set<VaultType> vaultsPlaced = EnumSet.noneOf(VaultType.class);
    private final Set<VaultType> keysPlaced = EnumSet.noneOf(VaultType.class);
    private final List<PlacedSegment> placedSegments = new ArrayList<>();
    private final Stack<DfsState> expansionStack = new Stack<>(); // DFS stack

    // --- Generation Parameters ---
    // IMPORTANT: Ensure a template named "start_room_1" exists and is loaded.
    private static final String START_ROOM_NAME = "start_room_1";
    private static final int MAX_TRIES_PER_ENTRANCE = 5;
    private static final int MAX_DISTANCE_FROM_START = 200;
    private static final int MAX_SEGMENTS = 50;
    private static final String SCHEMATICS_SUBDIR = "schematics";

    // Depth Rules
    private static final Map<VaultType, Integer> MAX_DEPTH_MAP = Map.of(
        VaultType.NONE, 2,
        VaultType.GREEN, 3,
        VaultType.BLUE, 7,
        VaultType.RED, 10,
        VaultType.GOLD, 13
    );
     private static final Map<VaultType, Integer> MIN_DEPTH_VAULT_MAP = Map.of(
         VaultType.GREEN, 3,
         VaultType.BLUE, 5,
         VaultType.RED, 7,
         VaultType.GOLD, 10
     );


    // Internal state for DFS steps
    private record DfsState(EntryPoint exitPoint, int depth, VaultType pathColor) {}

    // Constructor and Loader
    public DungeonManager(Plugin plugin, long seed) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.structureLoader = new StructureLoader(plugin); // Assumes StructureLoader is correctly implemented
        this.seed = seed;
        this.random = new Random(seed);
        plugin.getLogger().info("DungeonManager initialized with seed: " + seed);
    }
     public DungeonManager(Plugin plugin) { this(plugin, System.currentTimeMillis()); }

     /**
      * Loads segment templates. Ensure StructureLoader loads vaultType, keyForVault, isPuzzleRoom fields.
      */
     public boolean loadSegmentTemplates(File dataDir) {
        plugin.getLogger().info("Loading segment templates from: " + dataDir.getAbsolutePath());
        segmentTemplates.clear();
        List<Segment> loadedList = structureLoader.loadSegmentTemplates(dataDir); // Assumes loader is correct
        if (loadedList.isEmpty()) {
            plugin.getLogger().severe("No segment templates loaded. Dungeon generation will fail.");
            return false;
        }
        loadedList.forEach(template -> segmentTemplates.put(template.getName(), template));
        plugin.getLogger().info("Successfully loaded " + segmentTemplates.size() + " segment templates.");
        // IMPORTANT: Verify that loaded templates include necessary metadata (vault, key, puzzle flags)
        // Example check (optional):
        // if (!segmentTemplates.containsKey(START_ROOM_NAME)) {
        //     plugin.getLogger().severe("CRITICAL: Start room template '" + START_ROOM_NAME + "' is missing!");
        // }
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
        random.setSeed(seed);

        // --- Validate ---
        if (segmentTemplates.isEmpty()) {
            plugin.getLogger().severe("Cannot generate dungeon: No segment templates loaded.");
            return;
        }
        if (startRoomOrigin == null || startRoomOrigin.getWorld() == null) {
             plugin.getLogger().severe("Cannot generate dungeon: Start room location or its world is null.");
             return;
        }

        // --- Setup Start Room ---
        Segment startTemplate = segmentTemplates.get(START_ROOM_NAME);
        if (startTemplate == null) {
             plugin.getLogger().severe("Cannot generate dungeon: Start room template '" + START_ROOM_NAME + "' not found.");
             return;
        }

        PlacedSegment startPlacedSegment = new PlacedSegment(startTemplate, startRoomOrigin);
        if (!pasteSegment(startPlacedSegment)) {
            plugin.getLogger().severe("Failed to place starting room schematic. Aborting dungeon generation.");
            return;
        }
        placedSegments.add(startPlacedSegment);
        // Populate features for start room too, if any
        populateSegmentFeatures(startPlacedSegment);
        plugin.getLogger().info("Placed start room: " + startTemplate.getName());

        // --- Designate Initial Paths & Populate Stack ---
        List<EntryPoint> startExits = startPlacedSegment.getAbsoluteEntryPoints();
        Collections.shuffle(startExits, random);
        // Ensure enough colors for available exits, or handle fewer exits gracefully
        List<VaultType> colorsToAssign = new ArrayList<>(List.of(VaultType.GOLD, VaultType.RED, VaultType.BLUE, VaultType.GREEN));

        for (EntryPoint exit : startExits) {
            VaultType assignedColor = VaultType.NONE;
            if (!colorsToAssign.isEmpty()) {
                assignedColor = colorsToAssign.remove(0);
                plugin.getLogger().info("Designating path from exit " + exit.getLocation().toVector() + " as " + assignedColor);
            }
            expansionStack.push(new DfsState(exit, 1, assignedColor));
        }
        Collections.shuffle(expansionStack, random);

        // --- Main DFS Loop ---
        int segmentsPlacedCount = 1;
        while (!expansionStack.isEmpty() && segmentsPlacedCount < MAX_SEGMENTS) {
            DfsState currentState = expansionStack.pop();
            EntryPoint currentExit = currentState.exitPoint();
            int currentDepth = currentState.depth();
            VaultType currentPathColor = currentState.pathColor();

            // --- Check Depth Limit ---
            int maxDepth = MAX_DEPTH_MAP.getOrDefault(currentPathColor, 2);
            if (currentDepth >= maxDepth) {
                closeUnusedEntrance(currentExit);
                continue;
            }

            // --- Find and Prioritize Candidate Templates ---
            Direction requiredDir = currentExit.getDirection().getOpposite();
            List<Segment> potentialTemplates = findMatchingTemplates(requiredDir);
            // Ensure Segment class has the methods getVaultType(), getKeyForVault(), isPuzzleRoom()
            List<Segment> orderedCandidates = prioritizeCandidates(potentialTemplates, currentPathColor, currentDepth);

            // --- Attempt Placement ---
            boolean connected = false;
            int tries = 0;
            while (!connected && tries < MAX_TRIES_PER_ENTRANCE && !orderedCandidates.isEmpty()) {
                tries++;
                Segment candidateTemplate = orderedCandidates.remove(0);

                Segment.RelativeEntryPoint candidateEntryPoint = candidateTemplate.findEntryPointByDirection(requiredDir);
                if (candidateEntryPoint == null) continue;

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
                        VaultType placedVault = candidateTemplate.getVaultType();
                        VaultType placedKey = candidateTemplate.getKeyForVault();
                        if (placedVault != VaultType.NONE) {
                            vaultsPlaced.add(placedVault);
                            plugin.getLogger().info("Placed VAULT: " + placedVault);
                        }
                        if (placedKey != VaultType.NONE) {
                            keysPlaced.add(placedKey);
                             plugin.getLogger().info("Placed KEY for: " + placedKey);
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
                            // Pruning for Normal Paths
                             if (candidateTemplate.isPuzzleRoom() || currentDepth >= 1) {
                                 plugin.getLogger().fine("Pruning branches from normal path segment: " + candidateTemplate.getName());
                                 // Close these exits immediately instead of adding to stack
                                 newExits.forEach(this::closeUnusedEntrance);
                             } else {
                                 // Allow only one random branch from the first normal segment
                                 Collections.shuffle(newExits, random);
                                 expansionStack.push(new DfsState(newExits.get(0), currentDepth + 1, VaultType.NONE));
                                 // Close the other exits from this segment
                                 for (int i = 1; i < newExits.size(); i++) {
                                     closeUnusedEntrance(newExits.get(i));
                                 }
                             }
                        } else if (!newExits.isEmpty()) {
                            // Colored paths: Add all exits, maintaining color
                            Collections.shuffle(newExits, random);
                            for (EntryPoint newExit : newExits) {
                                // Only push if the vault for this color hasn't been placed yet,
                                // or if this segment IS the vault (allowing exits from vault room?)
                                if (!vaultsPlaced.contains(currentPathColor) || candidateTemplate.getVaultType() == currentPathColor) {
                                    expansionStack.push(new DfsState(newExit, currentDepth + 1, currentPathColor));
                                } else {
                                    // Vault already placed on this path, close further exits
                                    closeUnusedEntrance(newExit);
                                }
                            }
                        }
                        // Shuffle stack after potential adds
                        Collections.shuffle(expansionStack, random);

                        plugin.getLogger().info("Placed segment #" + segmentsPlacedCount + ": " + candidateTemplate.getName() + " (Path: " + currentPathColor + ", Depth: " + (currentDepth+1) + ")");

                    } else {
                        plugin.getLogger().warning("Placement validation passed for " + candidateTemplate.getName() + ", but schematic pasting failed.");
                    }
                } else {
                     plugin.getLogger().fine("Skipped placing " + candidateTemplate.getName() + ": Overlap=" + doesOverlap(candidatePlaced, placedSegments) + ", TooFar=" + !isWithinDistance(startRoomOrigin, candidateOrigin, MAX_DISTANCE_FROM_START));
                }
            } // End attempts loop

            if (!connected) {
                closeUnusedEntrance(currentExit);
            }
        } // End DFS loop

        // Final cleanup
        while (!expansionStack.isEmpty()) { closeUnusedEntrance(expansionStack.pop()); }
        plugin.getLogger().info("Dungeon generation finished. Total segments placed: " + placedSegments.size());
        // Log missing vaults/keys
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
        List<Segment> priorityPath = new ArrayList<>(); // Segments specifically for colored paths?
        List<Segment> priorityPuzzle = new ArrayList<>();
        List<Segment> normal = new ArrayList<>();

        int nextDepth = currentDepth + 1;

        for (Segment template : candidates) {
            VaultType templateVault = template.getVaultType();
            VaultType templateKey = template.getKeyForVault();

            // --- Vault Placement ---
            if (templateVault != VaultType.NONE) {
                if (templateVault == pathColor && // Must match path color
                    nextDepth >= MIN_DEPTH_VAULT_MAP.getOrDefault(pathColor, Integer.MAX_VALUE) && // Must meet min depth
                    !vaultsPlaced.contains(templateVault)) // Must not be placed yet
                {
                    priorityVault.add(template); continue;
                } else {
                    continue; // Don't place wrong vault, duplicate, or too early
                }
            }

            // --- Key Placement ---
             if (templateKey != VaultType.NONE) {
                 if (!keysPlaced.contains(templateKey)) { // Must not be placed yet
                     priorityKey.add(template); continue; // Keys can go anywhere not yet placed
                 } else {
                     continue; // Don't place duplicate keys
                 }
             }

            // --- Pathway/Puzzle/Normal ---
            // TODO: Refine pathway segment identification. Assuming non-special for now.
            // Maybe add a SegmentType.PATHWAY_COLOR or check name pattern?
            if (pathColor != VaultType.NONE) {
                 priorityPath.add(template); // On colored path, prefer pathway segments first
            } else {
                // On normal path, prefer puzzle rooms
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
        ordered.addAll(priorityVault); // Vault for this path is highest priority if conditions met
        ordered.addAll(priorityKey);   // Keys are next highest
        if (pathColor != VaultType.NONE) { // On colored path
            ordered.addAll(priorityPath); // Then pathway segments
            ordered.addAll(priorityPuzzle); // Then puzzles (less common on main path?)
            ordered.addAll(normal);        // Then normal filler
        } else { // On normal path
             ordered.addAll(priorityPuzzle); // Puzzles first
             ordered.addAll(normal);        // Then normal filler
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

                plugin.getLogger().info("Attempting to place sand in vault: " + template.getName());
                for (BlockVector3 relPos : shuffledSpawns) {
                    if (sandPlaced >= maxSand) break;
                    try {
                        Location absLoc = placedSegment.getAbsoluteLocation(relPos);
                        Block block = absLoc.getBlock();
                        // Only place sand if the spot is empty (air) or liquid
                        if (block.getType().isAir() || block.isLiquid()) {
                             block.setType(Material.SAND); // Consider SAND or SOUL_SAND?
                             sandPlaced++;
                             plugin.getLogger().fine("Placed sand at " + absLoc.toVector());
                        } else {
                            plugin.getLogger().fine("Skipped placing sand at " + absLoc.toVector() + ", block not replaceable: " + block.getType());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error placing sand at relative pos " + relPos + " for segment " + template.getName(), e);
                    }
                }
                 plugin.getLogger().info("Placed " + sandPlaced + "/" + maxSand + " sand blocks in vault: " + template.getName());
            } else {
                 plugin.getLogger().warning("Vault segment " + template.getName() + " has no sand spawn locations defined.");
            }
        }

        // --- Item Spawning ---
        List<BlockVector3> relativeItemSpawns = template.getItemSpawnLocations();
        if (relativeItemSpawns != null && !relativeItemSpawns.isEmpty()) {
             plugin.getLogger().info("Processing " + relativeItemSpawns.size() + " item spawn locations for " + template.getName());
             for (BlockVector3 relPos : relativeItemSpawns) {
                 Location absLoc = placedSegment.getAbsoluteLocation(relPos);
                 plugin.getLogger().fine("Attempting item spawn at " + absLoc.toVector() + " for " + template.getName());

                 // --- START: Item Spawning Logic Implementation ---
                 try {
                     // Example: Place a chest with a placeholder item (diamond)
                     Block block = absLoc.getBlock();
                     if (block.getType().isAir() || !block.getType().isSolid()) { // Check if replaceable
                         block.setType(Material.CHEST);
                         if (block.getState() instanceof Chest) {
                             Chest chest = (Chest) block.getState();
                             Inventory inv = chest.getBlockInventory();

                             // TODO: Determine actual loot based on rules (path color, depth, room type, key/vault status etc.)
                             // This requires a loot table system or hardcoded logic.
                             ItemStack lootItem;
                             VaultType keyType = template.getKeyForVault();
                             if (keyType != VaultType.NONE) {
                                 // This is a key room, place the corresponding key
                                 lootItem = createKeyItem(keyType); // Need createKeyItem helper method
                             } else {
                                 // Placeholder loot (e.g., a diamond)
                                 lootItem = new ItemStack(Material.DIAMOND, 1);
                                 // Add more complex loot table logic here based on context
                             }

                             if (lootItem != null) {
                                 // Place item in a random slot (or specific slots)
                                 inv.setItem(random.nextInt(inv.getSize()), lootItem);
                                 plugin.getLogger().fine("Placed chest with item " + lootItem.getType() + " at " + absLoc.toVector());
                             } else {
                                 plugin.getLogger().warning("Loot item was null for spawn at " + absLoc.toVector());
                             }
                         } else {
                              plugin.getLogger().warning("Placed CHEST material, but block state is not a Chest at " + absLoc.toVector());
                         }
                     } else {
                          plugin.getLogger().warning("Skipped placing chest at " + absLoc.toVector() + ", block not replaceable: " + block.getType());
                     }
                 } catch (Exception e) {
                      plugin.getLogger().log(Level.WARNING, "Error during item spawning at " + absLoc.toVector(), e);
                 }
                 // --- END: Item Spawning Logic Implementation ---
             }
        }
    }

    /**
     * Helper method to create a placeholder key item.
     * TODO: Customize appearance, lore, NBT tags as needed.
     */
    private ItemStack createKeyItem(VaultType vaultType) {
        Material material;
        String name;
        switch (vaultType) {
            case GOLD: material = Material.GOLD_NUGGET; name = "§6Gold Vault Key"; break; // Example
            case RED: material = Material.REDSTONE; name = "§cRed Vault Key"; break;
            case GREEN: material = Material.EMERALD; name = "§aGreen Vault Key"; break;
            case BLUE: material = Material.LAPIS_LAZULI; name = "§9Blue Vault Key"; break;
            default: return null; // No key for NONE
        }
        ItemStack key = new ItemStack(material, 1);
        // TODO: Add custom model data, lore, NBT tags to make keys unique/functional
        // ItemMeta meta = key.getItemMeta();
        // if (meta != null) {
        //    meta.setDisplayName(name);
        //    meta.setLore(List.of("Unlocks the " + vaultType + " Vault"));
        //    // meta.setCustomModelData(12345); // Example
        //    key.setItemMeta(meta);
        // }
        return key;
    }


    // --- Helper methods ---
    // [Implementations for calculatePlacementOrigin, pasteSegment, doesOverlap, findMatchingTemplates, areConnectingEntryPoints, isWithinDistance, closeUnusedEntrance]
    // (Copied from previous version - verify they are still correct)

    private Location calculatePlacementOrigin(EntryPoint currentExitPoint, Segment.RelativeEntryPoint candidateEntryPoint) {
        try {
            Location exitLoc = currentExitPoint.getLocation();
            Direction exitDir = currentExitPoint.getDirection();
            Location targetEntryPointLoc = exitLoc.clone().add(exitDir.getBlockVector()); // Use Direction's vector method
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
            plugin.getLogger().severe("Schematic file not found: " + (schematicFile != null ? schematicFile.getPath() : "null") + " for template " + template.getName()); return false;
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
                        .ignoreAirBlocks(false) // Usually false for dungeons
                        .build();
                Operations.complete(operation);
                return true;
            }
        } catch (WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "WorldEditException pasting: " + template.getName() + " at " + origin.toVector(), e); return false;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Unexpected error pasting: " + template.getName() + " at " + origin.toVector(), e); return false;
        }
    }

    private boolean doesOverlap(PlacedSegment newSegment, List<PlacedSegment> activeSegments) {
        for (PlacedSegment placed : activeSegments) {
            if (!newSegment.getWorld().equals(placed.getWorld())) continue;
            // IMPORTANT: Ensure your Area class has a working intersects(Area other) method for AABB check
            if (newSegment.getWorldBounds().intersects(placed.getWorldBounds())) {
                return true;
            }
        }
        return false;
    }

    private List<Segment> findMatchingTemplates(Direction requiredDirection) {
        // Ensure Segment has hasEntryPointInDirection(Direction)
        return segmentTemplates.values().stream()
                .filter(template -> template.hasEntryPointInDirection(requiredDirection))
                .collect(Collectors.toList());
    }

    private boolean areConnectingEntryPoints(EntryPoint ep1, EntryPoint ep2) {
        // Ensure Direction has getOpposite() and getBlockVector()
        if (ep1 == null || ep2 == null || ep1.getLocation() == null || ep2.getLocation() == null || ep1.getDirection() == null || ep2.getDirection() == null) return false;
        if (!ep1.getDirection().equals(ep2.getDirection().getOpposite())) return false;
        Location expectedLoc2 = ep1.getLocation().clone().add(ep1.getDirection().getBlockVector());
        // Compare block coordinates
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
              // Place block one step IN the direction the entrance faces (inside the segment)
              Location blockLoc = unusedEnd.getLocation(); //.clone().add(unusedEnd.getDirection().getBlockVector()); // Place *at* the entrance marker? Or one block out? Let's try *at*.
              blockLoc.getBlock().setType(Material.STONE_BRICKS); // Example closing material
         } catch (Exception e) {
             plugin.getLogger().log(Level.WARNING,"Failed to place closing block for unused entrance.", e);
         }
     }

}
