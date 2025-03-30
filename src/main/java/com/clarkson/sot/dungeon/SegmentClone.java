package com.clarkson.sot.dungeon;

import org.bukkit.Location;

import com.clarkson.sot.utils.EntryPoint;

import java.util.ArrayList;
import java.util.List;

public class SegmentClone extends Segment {
    private final Segment originalSegment;

    public SegmentClone(Segment originalSegment, Location startLocation, Location endLocation) {
        this(originalSegment, startLocation, endLocation, computeNewEntryPoints(originalSegment, startLocation));
    }

    // This is an auxiliary constructor that takes the precomputed entryPoints.
    private SegmentClone(Segment originalSegment, Location startLocation, Location endLocation, List<EntryPoint> newEntryPoints) {
        super(originalSegment.getName(), originalSegment.getType(), newEntryPoints, startLocation, endLocation, originalSegment.getCoinSpawnLocations(), originalSegment.getCoinSpawnLocations(), originalSegment.getCoinSpawnLocations(), originalSegment.getTotalCoins());
        this.originalSegment = originalSegment;
    }

    private static List<EntryPoint> computeNewEntryPoints(Segment originalSegment, Location startLocation) {
        Location offset = startLocation.clone().subtract(originalSegment.getStartLocation());

        List<EntryPoint> newEntryPoints = new ArrayList<>();
        for (EntryPoint originalEntryPoint : originalSegment.getEntryPoints()) {
            // Create a new location for the entry point based on the offset
            Location newLocation = originalEntryPoint.getLocation().clone().add(offset);

            // Create a new EntryPoint with the new location and the same direction
            EntryPoint newEntryPoint = new EntryPoint(newLocation, originalEntryPoint.getDirection());

            newEntryPoints.add(newEntryPoint);
        }
        return newEntryPoints;
    }
    public Segment getOriginalSegment() {
        return originalSegment;
    }

    
}


