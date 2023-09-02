package com.clarkson.sot;

import com.sk89q.worldedit.WorldEditException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;

import java.lang.foreign.SegmentScope;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DungeonManager {
    private List<Segment> segments;

    public DungeonManager() {
        segments = new ArrayList<>();
        initializeSegments();
    }
    public void generateDungeon(Location startRoomLocation) throws WorldEditException {
        // Step 1: Setup
        List<SegmentClone> activeSegments = new ArrayList<>();
        Segment startSegment = segments.get(0);
        startSegment.cloneToLocation(startRoomLocation);

        double width = Math.abs(startSegment.getBounds().getWidth());
        double height = Math.abs(startSegment.getBounds().getHeight());
        double depth = Math.abs(startSegment.getBounds().getDepth());

        SegmentClone startClone = new SegmentClone(startSegment, startRoomLocation, new Location(Bukkit.getWorld("world"),
                width + startRoomLocation.getBlockX(),
                height + startRoomLocation.getBlockY(),
                depth + startRoomLocation.getBlockZ()));
        activeSegments.add(startClone);
        List<EntryPoint> activeEnds = new ArrayList<>(startClone.getEntryPoints());

        System.out.println("Printing active Entry points: ");
        for (EntryPoint entryPoint : activeEnds) {
            System.out.println(entryPoint);
        }

        List<Segment> possibleSegments = new ArrayList<>(segments); // Assuming `segments` contains all possible segments
        possibleSegments.remove(0);
        Random rand = new Random();

        // Step 2: Random Selection
        while (!activeEnds.isEmpty() && !possibleSegments.isEmpty()) {
            int randomEndIndex = rand.nextInt(activeEnds.size());
            EntryPoint selectedEnd = activeEnds.get(randomEndIndex);
            System.out.println("Selected end: ");
            System.out.println(selectedEnd);

            List<Segment> matchingSegments = findMatchingSegments(selectedEnd, possibleSegments);

            if (matchingSegments.isEmpty()) {
                activeEnds.remove(randomEndIndex);
                continue;
            }

            while (!matchingSegments.isEmpty()) {
                int randomSegmentIndex = rand.nextInt(matchingSegments.size());
                Segment selectedSegment = matchingSegments.get(randomSegmentIndex);
                SegmentClone generatingSegment = new SegmentClone(selectedSegment, selectedSegment.findRelativeNwCorner(selectedEnd), new Location(Bukkit.getWorld("world"),
                        selectedSegment.getBounds().getWidth() + selectedSegment.findRelativeNwCorner(selectedEnd).getBlockX(),
                        selectedSegment.getBounds().getHeight() + selectedSegment.findRelativeNwCorner(selectedEnd).getBlockY(),
                        selectedSegment.getBounds().getDepth() + selectedSegment.findRelativeNwCorner(selectedEnd).getBlockZ()));

                System.out.println("Generating segment: ");
                System.out.println(generatingSegment);
                // Use cloneToArea method to handle the cloning logic


                // Step 3: Overlapping Check
                if (!doesOverlap(generatingSegment.getStartLocation(), generatingSegment, activeSegments)) {
                    System.out.println("passed Overlap check!");

                    // Step 4: Max Distance Check
                    if (isWithinDistance(startRoomLocation, generatingSegment.getStartLocation(), 50)) {
                        System.out.println("passed max distance check!");
                        activeSegments.add(generatingSegment);
                        selectedSegment.cloneByEntryPoint(selectedEnd);

                        // Add the remaining entry points of the new segment to activeEnds (excluding the one we just matched)
                        System.out.println("adding extra entrypoints: ");
                        for (EntryPoint ep : generatingSegment.getEntryPoints()) {
                            if (ep.getDirection() != selectedEnd.getOppositeDirection()) {
                                activeEnds.add(ep);
                                System.out.println(ep);
                            }
                        }
                    }
                    System.out.println("failed max distance check!");
                    break;
                } else {
                    System.out.println("failed Overlap check!");
                    matchingSegments.remove(randomSegmentIndex);
                }
            }
            activeEnds.remove(randomEndIndex);

            System.out.println("Printing active Entry points: ");
            for (EntryPoint entryPoint : activeEnds) {
                System.out.println(entryPoint);
            }
        }
    }


    private boolean doesOverlap(Location newLocation, Segment newSegment, List<SegmentClone> activeSegments) {
        for (Segment activeSegment : activeSegments) {
            if (areasOverlap(newLocation, newSegment, activeSegment)) {
                return true;
            }
        }
        return false;
    }

    private boolean areasOverlap(Location newLocation, Segment newSegment, Segment otherSegment) {
        // Assuming your Segment has getWidth(), getHeight(), and getDepth() methods to get the size.
        // Adjust this logic as per the methods you have in Segment class to get the dimensions.
        double newMinX = newLocation.getX();
        double newMinY = newLocation.getY();
        double newMinZ = newLocation.getZ();

        double newMaxX = newMinX + newSegment.getBounds().getWidth();
        double newMaxY = newMinY + newSegment.getBounds().getHeight();
        double newMaxZ = newMinZ + newSegment.getBounds().getDepth();

        double otherMinX = otherSegment.getStartLocation().getX();
        double otherMinY = otherSegment.getStartLocation().getY();
        double otherMinZ = otherSegment.getStartLocation().getZ();

        double otherMaxX = otherMinX + otherSegment.getBounds().getWidth();
        double otherMaxY = otherMinY + otherSegment.getBounds().getHeight();
        double otherMaxZ = otherMinZ + otherSegment.getBounds().getDepth();

        return (newMinX <= otherMaxX && newMaxX >= otherMinX) &&
                (newMinY <= otherMaxY && newMaxY >= otherMinY) &&
                (newMinZ <= otherMaxZ && newMaxZ >= otherMinZ);
    }


    private List<Segment> findMatchingSegments(EntryPoint endPoint, List<Segment> segments) {
        Direction oppositeDir = endPoint.getOppositeDirection();
        return segments.stream()
                .filter(seg -> seg.hasEntryPointInDirection(oppositeDir))
                .collect(Collectors.toList());
    }


    private boolean isWithinDistance(Location start, Location segmentLocation, int maxDistance) {
        return start.distance(segmentLocation) <= maxDistance;
    }


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

