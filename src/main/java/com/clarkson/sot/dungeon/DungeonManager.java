package com.clarkson.sot.dungeon;

import com.clarkson.sot.utils.Direction;
import com.clarkson.sot.utils.EntryPoint;
import com.sk89q.worldedit.WorldEditException;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Manages dungeon generation using a segment-based approach.
 */
public class DungeonManager {

    private final List<Segment> segments = new ArrayList<>();
    private final Random random; // Random instance for deterministic generation
    private final long seed; // Seed for reproducibility
    private static final int MAX_TRIES_PER_ENTRANCE = 5; // Maximum attempts to place a segment per entrance
    private static final int MAX_DISTANCE = 100; // Maximum distance from the start room to place segments
    private static final int MAX_SEGMENTS = 50; // Maximum number of segments allowed in the dungeon

    public DungeonManager(long seed) {
        this.seed = seed;
        this.random = new Random(seed); // Initialize Random with the seed
    }

    public DungeonManager() {
        this(System.currentTimeMillis()); // Default to a random seed based on the current time
    }

    public long getSeed() {
        return seed;
    }

    /**
     * Generates a dungeon starting from a given location.
     * @param startRoomLocation The location where the dungeon's first segment (start room) will be placed.
     * @throws WorldEditException if the WorldEdit cloning fails.
     */
    public void generateDungeon(Location startRoomLocation) throws WorldEditException {
        // Setup: Place the starting segment
        Segment startSegmentTemplate = segments.get(0);
        startSegmentTemplate.cloneToLocation(startRoomLocation);

        // Compute bounding box corners in world space
        double width = Math.abs(startSegmentTemplate.getBounds().getWidth());
        double height = Math.abs(startSegmentTemplate.getBounds().getHeight());
        double depth = Math.abs(startSegmentTemplate.getBounds().getDepth());

        Location endCorner = new Location(
                Bukkit.getWorld("world"),
                startRoomLocation.getBlockX() + width,
                startRoomLocation.getBlockY() + height,
                startRoomLocation.getBlockZ() + depth
        );

        SegmentClone startClone = new SegmentClone(startSegmentTemplate, startRoomLocation, endCorner);
        List<SegmentClone> activeSegments = new ArrayList<>();
        activeSegments.add(startClone);

        List<EntryPoint> activeEnds = new ArrayList<>(startClone.getEntryPoints());
        List<Segment> remainingSegments = new ArrayList<>(segments);
        remainingSegments.remove(startSegmentTemplate);

        // Main loop: Place new segments
        while (!activeEnds.isEmpty() && !remainingSegments.isEmpty() && activeSegments.size() < MAX_SEGMENTS) {
                int randomEndIndex = random.nextInt(activeEnds.size());
                EntryPoint selectedEnd = activeEnds.get(randomEndIndex);

                List<Segment> matchingSegments = findMatchingSegments(selectedEnd, remainingSegments);
                boolean segmentPlaced = false;
                int tries = 0;

                while (!segmentPlaced && tries < MAX_TRIES_PER_ENTRANCE && !matchingSegments.isEmpty()) {
                tries++;
                int randomSegmentIndex = random.nextInt(matchingSegments.size());
                Segment selectedTemplate = matchingSegments.get(randomSegmentIndex);

                Location newSegmentLocation = selectedTemplate.findRelativeNwCorner(selectedEnd);
                double newWidth = Math.abs(selectedTemplate.getBounds().getWidth());
                double newHeight = Math.abs(selectedTemplate.getBounds().getHeight());
                double newDepth = Math.abs(selectedTemplate.getBounds().getDepth());

                Location newEndCorner = new Location(
                        Bukkit.getWorld("world"),
                        newSegmentLocation.getBlockX() + newWidth,
                        newSegmentLocation.getBlockY() + newHeight,
                        newSegmentLocation.getBlockZ() + newDepth
                );

                SegmentClone newClone = new SegmentClone(selectedTemplate, newSegmentLocation, newEndCorner);

                if (!doesOverlap(newClone, activeSegments) &&
                        isWithinDistance(startRoomLocation, newClone.getStartLocation(), MAX_DISTANCE)) {
                        selectedTemplate.cloneByEntryPoint(selectedEnd);
                        activeSegments.add(newClone);

                        for (EntryPoint ep : newClone.getEntryPoints()) {
                        if (ep.getDirection() != selectedEnd.getOppositeDirection()) {
                                activeEnds.add(ep);
                        }
                        }

                        segmentPlaced = true;
                }

                matchingSegments.remove(randomSegmentIndex);
                }

                activeEnds.remove(randomEndIndex);
        }

        closeUnusedEntrances(activeEnds);
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
}

