package com.clarkson.sot.dungeon;

import com.clarkson.sot.utils.Direction;
import com.clarkson.sot.utils.EntryPoint;
import com.sk89q.worldedit.WorldEditException;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Manages dungeon generation using a segment-based approach.
 */
public class DungeonManager {

    // All possible segment templates (rooms, corridors, corners, etc.).
    private final List<Segment> segments = new ArrayList<>();

    // Limits or constraints for generation
    private static final int MAX_SEGMENTS = 50;           // Stop once we place 50 segments
    private static final int MAX_TRIES_PER_ENTRANCE = 5;  // Limit attempts to find a valid segment for each entrance
    private static final int MAX_DISTANCE = 50;           // Distance from start location

    public DungeonManager() {
        initializeSegments();
    }

    /**
     * Generates a dungeon starting from a given location.
     * @param startRoomLocation The location where the dungeon's first segment (start room) will be placed.
     * @throws WorldEditException if the WorldEdit cloning fails.
     */
    public void generateDungeon(Location startRoomLocation) throws WorldEditException {

        // -------------------------------------------------------------
        // 1. Setup: Place the starting segment, track active entrances
        // -------------------------------------------------------------
        // For simplicity, assume index 0 is always your "start" segment
        Segment startSegmentTemplate = segments.get(0);

        // Clone (place) it at the given startRoomLocation
        startSegmentTemplate.cloneToLocation(startRoomLocation);

        // Compute bounding box corners in world space
        double width  = Math.abs(startSegmentTemplate.getBounds().getWidth());
        double height = Math.abs(startSegmentTemplate.getBounds().getHeight());
        double depth  = Math.abs(startSegmentTemplate.getBounds().getDepth());

        // Make the placed instance
        Location endCorner = new Location(
                Bukkit.getWorld("world"),
                startRoomLocation.getBlockX() + width,
                startRoomLocation.getBlockY() + height,
                startRoomLocation.getBlockZ() + depth
        );

        SegmentClone startClone = new SegmentClone(startSegmentTemplate, startRoomLocation, endCorner);
        List<SegmentClone> activeSegments = new ArrayList<>();
        activeSegments.add(startClone);

        // Collect the available entrances from the newly placed segment
        List<EntryPoint> activeEnds = new ArrayList<>(startClone.getEntryPoints());

        // The "remainingSegments" (excluding the start segment template if desired)
        List<Segment> remainingSegments = new ArrayList<>(segments);
        // Optionally remove the start segment if you only want it placed once:
        remainingSegments.remove(startSegmentTemplate);

        Random rand = new Random();

        // -------------------------------------------------------------
        // 2. Main Loop: Keep placing new segments while possible
        // -------------------------------------------------------------
        while (!activeEnds.isEmpty() && !remainingSegments.isEmpty() && activeSegments.size() < MAX_SEGMENTS) {

            // Pick a random Entrance from our pool
            int randomEndIndex = rand.nextInt(activeEnds.size());
            EntryPoint selectedEnd = activeEnds.get(randomEndIndex);
            System.out.println("[DEBUG] Chosen End: " + selectedEnd);

            // Based on the direction of this Entrance, find segment templates that have
            // an entrance facing the opposite direction (so they can connect).
            List<Segment> matchingSegments = findMatchingSegments(selectedEnd, remainingSegments);

            boolean segmentPlaced = false;
            int tries = 0;

            // Try multiple times to place a valid segment at this entrance
            while (!segmentPlaced && tries < MAX_TRIES_PER_ENTRANCE && !matchingSegments.isEmpty()) {
                tries++;

                // Pick a random segment from the matching list
                int randomSegmentIndex = rand.nextInt(matchingSegments.size());
                Segment selectedTemplate = matchingSegments.get(randomSegmentIndex);

                // Calculate where the new segment would start
                // (Using your custom findRelativeNwCorner or similar logic)
                Location newSegmentLocation = selectedTemplate.findRelativeNwCorner(selectedEnd);

                // Also compute the bounding box end corner
                double newWidth  = Math.abs(selectedTemplate.getBounds().getWidth());
                double newHeight = Math.abs(selectedTemplate.getBounds().getHeight());
                double newDepth  = Math.abs(selectedTemplate.getBounds().getDepth());

                Location newEndCorner = new Location(
                        Bukkit.getWorld("world"),
                        newSegmentLocation.getBlockX() + newWidth,
                        newSegmentLocation.getBlockY() + newHeight,
                        newSegmentLocation.getBlockZ() + newDepth
                );

                SegmentClone newClone = new SegmentClone(selectedTemplate, newSegmentLocation, newEndCorner);

                System.out.println("[DEBUG] Attempting to place: " + newClone);

                // 3. Overlap Check
                if (!doesOverlap(newClone, activeSegments)) {
                    System.out.println("[DEBUG] Passed overlap check.");

                    // 4. Distance Check (optional)
                    if (isWithinDistance(startRoomLocation, newClone.getStartLocation(), MAX_DISTANCE)) {
                        System.out.println("[DEBUG] Passed distance check.");

                        // Place it in the world (WorldEdit clone, etc.)
                        selectedTemplate.cloneByEntryPoint(selectedEnd);

                        // Add to our active segment list
                        activeSegments.add(newClone);

                        // Mark the entrance we used as "used"
                        // (Optional: You could store a boolean in the EntryPoint itself or remove it from the list)

                        // Add the new segment's "unused" entrances to activeEnds
                        // Exclude the entrance that aligns with `selectedEnd.getOppositeDirection()`
                        for (EntryPoint ep : newClone.getEntryPoints()) {
                            if (ep.getDirection() != selectedEnd.getOppositeDirection()) {
                                activeEnds.add(ep);
                            }
                        }

                        // Mark success
                        segmentPlaced = true;
                    } else {
                        System.out.println("[DEBUG] Failed distance check (> " + MAX_DISTANCE + " blocks).");
                    }
                } else {
                    System.out.println("[DEBUG] Failed overlap check.");
                }

                // Remove this template from the matching list so we don't pick it again
                // in the same attempt cycle (if it failed either overlap or distance).
                matchingSegments.remove(randomSegmentIndex);
            }

            // Whether placed or not, we remove this end from the pool
            // because we've tried at least once.
            activeEnds.remove(randomEndIndex);
        }

        // -------------------------------------------------------------
        // 3. (Optional) Post-Processing: Seal off open entrances, etc.
        // -------------------------------------------------------------
        closeUnusedEntrances(activeEnds);

        // If you have vaults or keys to place, you might run a check here:
        // ensure required segments got placed, otherwise repeat or fix up.

        System.out.println("[INFO] Dungeon generation finished. Segments placed: " + activeSegments.size());
    }

    /**
     * Checks if a new segment placement overlaps with any already placed segments.
     */
    private boolean doesOverlap(SegmentClone newClone, List<SegmentClone> activeSegments) {
        for (SegmentClone placedClone : activeSegments) {
            if (areasOverlap(newClone, placedClone)) {
                return true;
            }
        }
        return false;
    }

    /**
     * AABB overlap check for two placed segments in world space.
     */
    private boolean areasOverlap(SegmentClone segA, SegmentClone segB) {
        // Gather min & max corners for each segment's bounding box
        // Segment A
        double aMinX = Math.min(segA.getStartLocation().getX(), segA.getEndLocation().getX());
        double aMaxX = Math.max(segA.getStartLocation().getX(), segA.getEndLocation().getX());
        double aMinY = Math.min(segA.getStartLocation().getY(), segA.getEndLocation().getY());
        double aMaxY = Math.max(segA.getStartLocation().getY(), segA.getEndLocation().getY());
        double aMinZ = Math.min(segA.getStartLocation().getZ(), segA.getEndLocation().getZ());
        double aMaxZ = Math.max(segA.getStartLocation().getZ(), segA.getEndLocation().getZ());

        // Segment B
        double bMinX = Math.min(segB.getStartLocation().getX(), segB.getEndLocation().getX());
        double bMaxX = Math.max(segB.getStartLocation().getX(), segB.getEndLocation().getX());
        double bMinY = Math.min(segB.getStartLocation().getY(), segB.getEndLocation().getY());
        double bMaxY = Math.max(segB.getStartLocation().getY(), segB.getEndLocation().getY());
        double bMinZ = Math.min(segB.getStartLocation().getZ(), segB.getEndLocation().getZ());
        double bMaxZ = Math.max(segB.getStartLocation().getZ(), segB.getEndLocation().getZ());

        // Check overlap along all axes
        boolean overlapX = (aMinX <= bMaxX && aMaxX >= bMinX);
        boolean overlapY = (aMinY <= bMaxY && aMaxY >= bMinY);
        boolean overlapZ = (aMinZ <= bMaxZ && aMaxZ >= bMinZ);

        return (overlapX && overlapY && overlapZ);
    }

    /**
     * Finds all segment templates that have an entrance facing the opposite direction
     * of the given endPoint.
     */
    private List<Segment> findMatchingSegments(EntryPoint endPoint, List<Segment> allSegments) {
        Direction oppositeDir = endPoint.getOppositeDirection();
        return allSegments.stream()
                .filter(seg -> seg.hasEntryPointInDirection(oppositeDir))
                .collect(Collectors.toList());
    }

    /**
     * Simple check to limit how far from the start we can place segments.
     */
    private boolean isWithinDistance(Location start, Location segmentLocation, int maxDistance) {
        return (start.getWorld().equals(segmentLocation.getWorld())
                && start.distance(segmentLocation) <= maxDistance);
    }

    /**
     * Optional: Close or fill any entrances that remain unused at the end of generation.
     */
    private void closeUnusedEntrances(List<EntryPoint> unusedEnds) {
        for (EntryPoint ep : unusedEnds) {
            // For example: place a wall, locked door, or just ignore.
            // This is entirely up to your design preference.
            System.out.println("[DEBUG] Closing off unused entrance: " + ep);
        }
    }

    /**
     * Initialize your segment templates here.
     * Each Segment has:
     *  - name
     *  - segment type
     *  - list of entry points (local directions)
     *  - bounding box corners (start & end in world coords)
     */
    private void initializeSegments() {

        segments.add(new Segment(
                "StartRoom", SegmentType.START,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 46, 49, 55), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 62, 49, 67), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 45, 49, 81), Direction.SOUTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 33, 49, 69), Direction.WEST)
                        // Add other entry points if necessary
                ),
                new Location(Bukkit.getWorld("world"), 62, 48, 55),
                new Location(Bukkit.getWorld("world"), 33, 56, 81)
        ));

        segments.add(new Segment(
                "NSCorridor", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 166, 139, 209), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 166, 139, 221), Direction.SOUTH)
                ),
                new Location(Bukkit.getWorld("world"), 269, 138, 209),
                new Location(Bukkit.getWorld("world"), 163, 143, 221)
                )
        );

        segments.add(new Segment(
                "WECorridor", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 172, 139, 231), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 160, 139, 231), Direction.WEST)
                ),
                new Location(Bukkit.getWorld("world"), 172, 138, 228),
                new Location(Bukkit.getWorld("world"), 160, 143, 234)
                )
        );

        segments.add(new Segment(
                "LeftCornerNE", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 150, 139, 210), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 153, 139, 219), Direction.EAST)
                ),
                new Location(Bukkit.getWorld("world"), 153, 138, 210),
                new Location(Bukkit.getWorld("world"), 147, 143, 222)
                )
        );

        segments.add(new Segment(
                "LeftCornerES", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 157, 139, 231), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 148, 139, 234), Direction.SOUTH)
                ),
                new Location(Bukkit.getWorld("world"), 157, 138, 228),
                new Location(Bukkit.getWorld("world"), 145, 143, 234)
                )
        );

        segments.add(new Segment(
                "LeftCornerSW", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 147, 139, 243), Direction.WEST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 150, 139, 252), Direction.SOUTH)
                ),
                new Location(Bukkit.getWorld("world"), 153, 138, 240),
                new Location(Bukkit.getWorld("world"), 147, 143, 252)
                )
        );

        segments.add(new Segment(
                "LeftCornerWN", SegmentType.CORRIDOR,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 153, 139, 258), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 144, 139, 261), Direction.WEST)
                ),
                new Location(Bukkit.getWorld("world"), 156, 138, 258),
                new Location(Bukkit.getWorld("world"), 144, 143, 264)
                )
        );

        segments.add(new Segment(
                "SmallRoomNS", SegmentType.SMALL_ROOM,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 136, 136, 209), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 142, 139, 215), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 136, 139, 221), Direction.SOUTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 129, 139, 215), Direction.WEST)
                ),
                new Location(Bukkit.getWorld("world"), 142, 138, 209),
                new Location(Bukkit.getWorld("world"), 129, 143, 221)
                )
        );

        segments.add(new Segment(
                "SmallRoomWE", SegmentType.SMALL_ROOM,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 135, 139, 224), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 142, 139, 231), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 135, 139, 237), Direction.SOUTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 130, 139, 231), Direction.WEST)
                ),
                new Location(Bukkit.getWorld("world"), 142, 138, 224),
                new Location(Bukkit.getWorld("world"), 130, 143, 237)
                )
        );

        segments.add(new Segment(
                "StairsNS", SegmentType.STAIRS,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 121, 139, 209), Direction.NORTH),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 121, 145, 221), Direction.SOUTH)
                ),
                new Location(Bukkit.getWorld("world"), 124, 138, 209),
                new Location(Bukkit.getWorld("world"), 118, 148, 221)
                )
        );

        segments.add(new Segment(
                "StairsEW", SegmentType.STAIRS,
                Arrays.asList(
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 127, 139, 230), Direction.EAST),
                        new EntryPoint(new Location(Bukkit.getWorld("world"), 115, 145, 230), Direction.WEST)
                ),
                new Location(Bukkit.getWorld("world"), 127, 138, 227),
                new Location(Bukkit.getWorld("world"), 115, 148, 233)
                )
        );

        segments.add(new Segment(
                        "StairsSN", SegmentType.STAIRS,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 120, 139, 252), Direction.SOUTH),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 120, 145, 240), Direction.NORTH)
                        ),
                        new Location(Bukkit.getWorld("world"), 117, 138, 252),
                        new Location(Bukkit.getWorld("world"), 123, 148, 240)
                )
        );

        segments.add(new Segment(
                        "StairsWE", SegmentType.STAIRS,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 114, 139, 261), Direction.WEST),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 126, 145, 261), Direction.EAST)
                        ),
                        new Location(Bukkit.getWorld("world"), 114, 138, 258),
                        new Location(Bukkit.getWorld("world"), 126, 148, 264)
                )
        );

        segments.add(new Segment(
                        "RightCornerNW", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 109, 139, 209), Direction.NORTH),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 99, 139, 218), Direction.WEST)
                        ),
                        new Location(Bukkit.getWorld("world"), 112, 138, 209),
                        new Location(Bukkit.getWorld("world"), 99, 143, 221)
                )
        );

        segments.add(new Segment(
                        "RightCornerEN", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 112, 139, 234), Direction.EAST),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 103, 139, 224), Direction.NORTH)
                        ),
                        new Location(Bukkit.getWorld("world"), 112, 138, 237),
                        new Location(Bukkit.getWorld("world"), 100, 143, 224)
                )
        );

        segments.add(new Segment(
                        "RightCornerSE", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 102, 139, 252), Direction.SOUTH),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 112, 139, 243), Direction.EAST)
                        ),
                        new Location(Bukkit.getWorld("world"), 99, 138, 252),
                        new Location(Bukkit.getWorld("world"), 112, 143, 240)
                )
        );

        segments.add(new Segment(
                        "RightCornerWS", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 999, 139, 257), Direction.WEST),
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 102, 139, 252), Direction.SOUTH)
                        ),
                        new Location(Bukkit.getWorld("world"), 99, 138, 254),
                        new Location(Bukkit.getWorld("world"), 111, 143, 267)
                )
        );

        segments.add(new Segment(
                        "EndN", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 91, 139, 212), Direction.NORTH)
                        ),
                        new Location(Bukkit.getWorld("world"), 93, 138, 212),
                        new Location(Bukkit.getWorld("world"), 89, 142, 216)
                )
        );

        segments.add(new Segment(
                        "EndE", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 94, 139, 231), Direction.EAST)
                        ),
                        new Location(Bukkit.getWorld("world"), 94, 138, 233),
                        new Location(Bukkit.getWorld("world"), 90, 142, 233)
                )
        );

        segments.add(new Segment(
                        "EndS", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 90, 139, 248), Direction.SOUTH)
                        ),
                        new Location(Bukkit.getWorld("world"), 92, 138, 244),
                        new Location(Bukkit.getWorld("world"), 88, 142, 248)
                )
        );

        segments.add(new Segment(
                        "EndW", SegmentType.CORRIDOR,
                        Arrays.asList(
                                new EntryPoint(new Location(Bukkit.getWorld("world"), 87, 139, 260), Direction.WEST)
                        ),
                        new Location(Bukkit.getWorld("world"), 91, 138, 258),
                        new Location(Bukkit.getWorld("world"), 87, 142, 262)
                )
        );



        // Add more segments as needed...
    }

    // Other dungeon-related methods...
}

