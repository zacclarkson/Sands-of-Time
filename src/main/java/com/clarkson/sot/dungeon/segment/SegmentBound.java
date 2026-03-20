package com.clarkson.sot.dungeon.segment;

import com.sk89q.worldedit.math.BlockVector3;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a 2D bounded opening within a segment (vault door or gate).
 * Corners are stored as relative offsets from the segment's minimum corner.
 */
public class SegmentBound {

    private final BlockVector3 relativeMin;
    private final BlockVector3 relativeMax;

    public SegmentBound(@NotNull BlockVector3 relativeMin, @NotNull BlockVector3 relativeMax) {
        this.relativeMin = relativeMin;
        this.relativeMax = relativeMax;
    }

    @NotNull
    public BlockVector3 getRelativeMin() { return relativeMin; }

    @NotNull
    public BlockVector3 getRelativeMax() { return relativeMax; }

    @Override
    public String toString() {
        return "SegmentBound{min=" + relativeMin + ", max=" + relativeMax + "}";
    }
}
