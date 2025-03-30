package com.clarkson.sot.dungeon;

// Local project imports
import com.clarkson.sot.utils.Direction; // Assuming your Direction enum/class
import com.clarkson.sot.utils.EntryPoint; // Original EntryPoint with absolute Location (for tracking open ends)
import com.clarkson.sot.utils.StructureLoader; // The loader for templates

// WorldEdit imports
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
import com.sk89q.worldedit.world.World; // WorldEdit World

// Bukkit imports
import org.bukkit.Location;
import org.bukkit.Material; // For closing entrances potentially
import org.bukkit.plugin.Plugin;

// Java imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*; // For List, ArrayList, Map, HashMap, Stack, Collections
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages dungeon generation using a segment-based approach with Depth-First Search.
 * Loads world-independent Segment templates and uses PlacedSegment instances during generation.
 */
public class DungeonManager {

    private final Plugin plugin;
    private final StructureLoader structureLoader;
    private final Map<String, Segment> segmentTemplates = new HashMap<>(); // Stores loaded templates by name
    private final Random random;
    private final long seed;

    // --- Generation Parameters ---
    private static final String START_ROOM_NAME = "start_room_1"; // Example name for the starting template
    private static final int MAX_TRIES_PER_ENTRANCE = 5; // Max attempts to find a valid segment for an exit
    private static final int MAX_DISTANCE_FROM_START = 150; // Max distance (Euclidean) from start room origin
    private static final int MAX_SEGMENTS = 30; // Max number of segments in the dungeon
    private static final String SCHEMATICS_SUBDIR = "schematics"; // Subdirectory where schematics are stored relative to plugin data folder

    public DungeonManager(Plugin plugin, long seed) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        // Initialize the loader, passing the plugin instance for logging
        this.structureLoader = new StructureLoader(plugin);
        this.seed = seed;
        this.random = new Random(seed);
        plugin.getLogger().info("DungeonManager initialized with seed: " + seed);
    }

    public DungeonManager(Plugin plugin) {
        this(plugin, System.currentTimeMillis()); // Default to a time-based seed
    }

    /**
     * Loads segment templates from JSON files in the specified directory.
     * Must be called before generating a dungeon.
     *
     * @param dataDir The directory containing the .json metadata files (e.g., plugin.getDataFolder()).
     * @return true if loading was successful and at least one template was loaded, false otherwise.
     */
    public boolean loadSegmentTemplates(File dataDir) {
        plugin.getLogger().info("Loading segment templates from: " + dataDir.getAbsolutePath());
        segmentTemplates.clear(); // Clear previous templates if reloading
        // Use the loader (World parameter is no longer needed for loading templates)
        List<Segment> loadedList = structureLoader.loadSegmentTemplates(dataDir);

        if (loadedList.isEmpty()) {
            plugin.getLogger().severe("No segment templates loaded from " + dataDir.getAbsolutePath() + ". Dungeon generation will fail.");
            return false;
        }

        // Store templates in a map for easy lookup by name
        for (Segment template : loadedList) {
            if (segmentTemplates.containsKey(template.getName())) {
                plugin.getLogger().warning("Duplicate segment template name found: '" + template.getName() + "'. Overwriting.");
            }
            segmentTemplates.put(template.getName(), template);
        }
        plugin.getLogger().info("Successfully loaded " + segmentTemplates.size() + " segment templates.");
        return !segmentTemplates.isEmpty();
    }


    public long getSeed() {
        return seed;
    }

    /**
     * Generates a dungeon using Depth-First Search starting from a given location.
     * Requires segment templates to be loaded first via loadSegmentTemplates().
     *
     * @param startRoomOrigin The location where the dungeon's first segment origin (min corner) will be placed.
     */
    public void generateDungeon(Location startRoomOrigin) {
        if (segmentTemplates.isEmpty()) {
            plugin.getLogger().severe("Cannot generate dungeon: No segment templates loaded. Call loadSegmentTemplates() first.");
            return;
        }
        if (startRoomOrigin == null || startRoomOrigin.getWorld() == null) {
             plugin.getLogger().severe("Cannot generate dungeon: Start room location or its world is null.");
             return;
        }

        // --- Setup ---
        Segment startTemplate = segmentTemplates.get(START_ROOM_NAME);
        if (startTemplate == null) {
            plugin.getLogger().severe("Cannot generate dungeon: Start room template '" + START_ROOM_NAME + "' not found.");
            return;
        }

        List<PlacedSegment> placedSegments = new ArrayList<>(); // Track placed segments
        // Stack for DFS: Stores absolute EntryPoints representing available exits to expand from
        Stack<EntryPoint> expansionStack = new Stack<>();

        // --- Place Start Room ---
        PlacedSegment startPlacedSegment = new PlacedSegment(startTemplate, startRoomOrigin);
        if (!pasteSegment(startPlacedSegment)) { // Use helper to paste schematic
            plugin.getLogger().severe("Failed to place starting room schematic. Aborting dungeon generation.");
            return;
        }
        placedSegments.add(startPlacedSegment);
        plugin.getLogger().info("Placed start room: " + startTemplate.getName() + " at " + startRoomOrigin.toVector());

        // Add initial exits from the start room to the stack (these have absolute locations)
        for (EntryPoint entryPoint : startPlacedSegment.getAbsoluteEntryPoints()) {
            expansionStack.push(entryPoint);
        }
        Collections.shuffle(expansionStack, random); // Randomize initial exploration order

        // --- Main DFS Loop ---
        int segmentsPlacedCount = 1; // Start room counts as 1
        while (!expansionStack.isEmpty() && segmentsPlacedCount < MAX_SEGMENTS) {
            EntryPoint currentExitPoint = expansionStack.pop(); // Get an absolute exit to expand from

            // Find templates that have an entry point matching the required connection direction
            Direction requiredDirection = currentExitPoint.getDirection().getOpposite();
            List<Segment> potentialTemplates = findMatchingTemplates(requiredDirection);
            Collections.shuffle(potentialTemplates, random); // Randomize connection attempts

            boolean connected = false;
            int tries = 0;
            // Try placing a segment from the potential list
            while (!connected && tries < MAX_TRIES_PER_ENTRANCE && !potentialTemplates.isEmpty()) {
                tries++;
                Segment candidateTemplate = potentialTemplates.remove(0); // Get next candidate

                // Find the specific entry point on the candidate template that matches the required direction
                Segment.RelativeEntryPoint candidateEntryPoint = candidateTemplate.findEntryPointByDirection(requiredDirection);
                if (candidateEntryPoint == null) {
                    plugin.getLogger().warning("Template '" + candidateTemplate.getName() + "' matched direction " + requiredDirection + " but couldn't find specific RelativeEntryPoint?");
                    continue; // Should not normally happen
                }

                // Calculate where the candidate's origin (min corner) should be placed in the world
                Location candidateOrigin = calculatePlacementOrigin(currentExitPoint, candidateEntryPoint);
                if (candidateOrigin == null) {
                     plugin.getLogger().warning("Could not calculate valid placement origin for " + candidateTemplate.getName() + " connecting to exit at " + currentExitPoint.getLocation().toVector());
                     continue; // Skip this candidate if calculation fails
                }

                // Create a potential PlacedSegment instance for validation checks
                PlacedSegment candidatePlaced = new PlacedSegment(candidateTemplate, candidateOrigin);

                // --- Validate Placement ---
                if (!doesOverlap(candidatePlaced, placedSegments) &&
                    isWithinDistance(startRoomOrigin, candidateOrigin, MAX_DISTANCE_FROM_START))
                {
                    // --- Placement is valid, paste the schematic ---
                    if (pasteSegment(candidatePlaced)) {
                        placedSegments.add(candidatePlaced);
                        segmentsPlacedCount++;
                        connected = true; // Successfully placed a segment here

                        // Add new (absolute) exits from the newly placed segment to the stack
                        List<EntryPoint> newExits = new ArrayList<>();
                        for(EntryPoint newEp : candidatePlaced.getAbsoluteEntryPoints()) {
                            // CRITICAL: Don't add the entrance we just connected *through* back to the stack
                            // Check if the new exit point's location and opposite direction match the current exit point
                             if (!areConnectingEntryPoints(newEp, currentExitPoint)) {
                                 newExits.add(newEp);
                             }
                        }
                        Collections.shuffle(newExits, random); // Shuffle exits before adding
                        for(EntryPoint newExit : newExits) {
                            expansionStack.push(newExit);
                        }

                        plugin.getLogger().info("Placed segment #" + segmentsPlacedCount + ": " + candidateTemplate.getName() + " at " + candidateOrigin.toVector());

                    } else {
                        plugin.getLogger().warning("Placement validation passed for " + candidateTemplate.getName() + ", but schematic pasting failed.");
                        // Don't mark as connected, allow trying other templates from the potential list
                    }
                } else {
                     // Log reason for failure (overlap or distance)? Can be verbose.
                     // plugin.getLogger().fine("Skipped placing " + candidateTemplate.getName() + ": Overlap=" + doesOverlap(candidatePlaced, placedSegments) + ", TooFar=" + !isWithinDistance(startRoomOrigin, candidateOrigin, MAX_DISTANCE_FROM_START));
                }
            } // End of connection attempts loop for this exit point

            if (!connected) {
                // If no segment could be connected to this exit after trying, close it off
                closeUnusedEntrance(currentExitPoint);
            }
        } // End of main DFS loop

        // Close any remaining exits on the stack if max segments limit was reached
        while(!expansionStack.isEmpty()) {
            closeUnusedEntrance(expansionStack.pop());
        }

        plugin.getLogger().info("Dungeon generation finished. Total segments placed: " + placedSegments.size());
    }

    /**
     * Calculates the required world origin (min corner) for a candidate segment
     * so that its entry point aligns correctly with the current exit point.
     */
    private Location calculatePlacementOrigin(EntryPoint currentExitPoint, Segment.RelativeEntryPoint candidateEntryPoint) {
        try {
            Location exitLoc = currentExitPoint.getLocation(); // Absolute location of the exit block
            Direction exitDir = currentExitPoint.getDirection(); // Direction the exit faces *out*

            // Target location for the candidate's entry point block should be one block away from the exit block
            Location targetEntryPointLoc = exitLoc.clone().add(exitDir.getBlockVector());

            // Get the relative position of the entry point within the candidate template
            BlockVector3 candidateEntryRelPos = candidateEntryPoint.getRelativePosition();

            // Calculate the candidate's origin by subtracting the entry point's relative offset
            // from the target absolute location where that entry point should end up.
            Location candidateOrigin = targetEntryPointLoc.clone().subtract(
                candidateEntryRelPos.x(),
                candidateEntryRelPos.y(),
                candidateEntryRelPos.z()
            );

            // Sanity check world
             if (!candidateOrigin.getWorld().equals(exitLoc.getWorld())) {
                  plugin.getLogger().severe("World mismatch during origin calculation!");
                  return null;
             }

            return candidateOrigin;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Error calculating placement origin", e);
             return null;
        }
    }


    /**
     * Pastes the schematic associated with a PlacedSegment into the world at its origin.
     */
    private boolean pasteSegment(PlacedSegment placedSegment) {
        Segment template = placedSegment.getSegmentTemplate();
        Location origin = placedSegment.getWorldOrigin(); // Paste destination is the segment's origin
        File schematicFile = template.getSchematicFile(plugin.getDataFolder(), SCHEMATICS_SUBDIR);

        if (schematicFile == null || !schematicFile.exists() || !schematicFile.isFile()) {
            plugin.getLogger().severe("Schematic file not found or invalid for template '" + template.getName() + "' at: " + (schematicFile != null ? schematicFile.getPath() : "null"));
            return false;
        }

        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
             plugin.getLogger().severe("Could not determine schematic format for file: " + schematicFile.getName());
             return false;
        }

        // --- Load Clipboard ---
        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {
            clipboard = reader.read();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "IOException reading schematic file: " + schematicFile.getName(), e);
            return false;
        } catch (Exception e) { // Catch potential WorldEdit format errors during read
             plugin.getLogger().log(Level.SEVERE, "Error loading clipboard from schematic: " + schematicFile.getName(), e);
             return false;
        }

        if (clipboard == null) {
             plugin.getLogger().severe("Loaded null clipboard from schematic: " + schematicFile.getName());
             return false;
        }

        // --- Paste Clipboard ---
        try {
            World weWorld = BukkitAdapter.adapt(origin.getWorld());
            try (com.sk89q.worldedit.EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                // Create paste operation
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BukkitAdapter.asBlockVector(origin)) // Paste TO the calculated origin
                        .ignoreAirBlocks(false) // Paste everything
                        // .copyEntities(true) // Enable if needed and schematics support it
                        .build();
                Operations.complete(operation); // Execute paste
                return true;
            }
        } catch (WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "WorldEditException pasting schematic '" + template.getName() + "' at " + origin.toVector(), e);
            return false;
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "Unexpected error pasting schematic '" + template.getName() + "'", e);
             return false;
        }
    }


    /**
     * Checks if a new segment placement overlaps with any already placed segments using AABB collision.
     */
    private boolean doesOverlap(PlacedSegment newSegment, List<PlacedSegment> activeSegments) {
        for (PlacedSegment placed : activeSegments) {
            if (!newSegment.getWorld().equals(placed.getWorld())) continue; // Sanity check
            // Use Area's intersects method (assuming it performs AABB check)
            if (newSegment.getWorldBounds().intersects(placed.getWorldBounds())) {
                // plugin.getLogger().fine("Overlap detected: " + newSegment.getName() + " overlaps " + placed.getName());
                return true;
            }
        }
        return false;
    }

    /**
     * Finds all loaded segment templates that have an entrance facing the specified direction.
     */
    private List<Segment> findMatchingTemplates(Direction requiredDirection) {
        return segmentTemplates.values().stream()
                .filter(template -> template.hasEntryPointInDirection(requiredDirection))
                .collect(Collectors.toList());
    }

    /**
     * Checks if two entry points represent the connection between two segments.
     * They should be at adjacent blocks and face opposite directions.
     */
    private boolean areConnectingEntryPoints(EntryPoint ep1, EntryPoint ep2) {
        if (ep1 == null || ep2 == null || ep1.getLocation() == null || ep2.getLocation() == null || ep1.getDirection() == null || ep2.getDirection() == null) {
            return false;
        }
        // Check for opposite directions
        if (!ep1.getDirection().equals(ep2.getDirection().getOpposite())) {
            return false;
        }
        // Check if locations are adjacent based on direction
        Location expectedLoc2 = ep1.getLocation().clone().add(ep1.getDirection().getBlockVector());
        // Compare block coordinates only
        return expectedLoc2.getBlockX() == ep2.getLocation().getBlockX() &&
               expectedLoc2.getBlockY() == ep2.getLocation().getBlockY() &&
               expectedLoc2.getBlockZ() == ep2.getLocation().getBlockZ();
    }


    /**
     * Simple check to limit how far from the start dungeon origin we can place segments.
     */
    private boolean isWithinDistance(Location startOrigin, Location segmentOrigin, int maxDistance) {
        if (!startOrigin.getWorld().equals(segmentOrigin.getWorld())) return false;
        return startOrigin.distanceSquared(segmentOrigin) <= (double) maxDistance * maxDistance;
    }

    /**
     * Placeholder: Close or fill any entrances that remain unused at the end of generation.
     */
    private void closeUnusedEntrance(EntryPoint unusedEnd) {
        if (unusedEnd == null || unusedEnd.getLocation() == null || unusedEnd.getDirection() == null) return;
        plugin.getLogger().info("[Dungeon] Closing off unused entrance at " + unusedEnd.getLocation().toVector() + " facing " + unusedEnd.getDirection());
        try {
             // Place block one step IN the direction the entrance faces
             Location blockLoc = unusedEnd.getLocation().clone().add(unusedEnd.getDirection().getBlockVector());
             blockLoc.getBlock().setType(Material.STONE_BRICKS); // Example closing material
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,"Failed to place closing block for unused entrance.", e);
        }
    }
}
