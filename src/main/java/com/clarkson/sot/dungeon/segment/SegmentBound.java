package com.clarkson.sot.dungeon.segment;

import com.sk89q.worldedit.math.BlockVector3;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a 2D rectangular bound (e.g. vault door opening, gate opening) stored
 * as relative offsets from the segment origin. Both corners are inclusive.
 */
public class SegmentBound {

    private final BlockVector3 min;
    private final BlockVector3 max;

    /**
     * Constructs a SegmentBound from two corners. Min/max are automatically derived.
     *
     * @param corner1 First corner (relative to segment origin).
     * @param corner2 Second corner (relative to segment origin).
     */
    public SegmentBound(@NotNull BlockVector3 corner1, @NotNull BlockVector3 corner2) {
        this.min = BlockVector3.at(
            Math.min(corner1.x(), corner2.x()),
            Math.min(corner1.y(), corner2.y()),
            Math.min(corner1.z(), corner2.z())
        );
        this.max = BlockVector3.at(
            Math.max(corner1.x(), corner2.x()),
            Math.max(corner1.y(), corner2.y()),
            Math.max(corner1.z(), corner2.z())
        );
    }

    @NotNull
    public BlockVector3 getMin() { return min; }

    @NotNull
    public BlockVector3 getMax() { return max; }

    @Override
    public String toString() {
        return "SegmentBound{min=" + min + ", max=" + max + "}";
    }
}
