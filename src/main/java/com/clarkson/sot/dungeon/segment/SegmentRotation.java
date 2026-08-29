package com.clarkson.sot.dungeon.segment;

import com.sk89q.worldedit.math.BlockVector3;

/**
 * Y-axis rotation of segment-relative geometry in 90° steps.
 * <p>
 * One step is a single {@code AffineTransform().rotateY(90)} (WorldEdit's convention), applied about the
 * segment's own origin and then normalised back into a non-negative footprint {@code [0, rotatedSize-1]}.
 * The same transform drives the schematic paste in {@code DungeonManager}, so rotated blocks and rotated
 * marker offsets land on the same cells (pinned by {@code SegmentRotationTest}).
 */
public final class SegmentRotation {

    private SegmentRotation() {}

    /** Normalises an arbitrary step count to 0..3. */
    public static int norm(int steps) {
        return ((steps % 4) + 4) % 4;
    }

    /** The footprint size after rotation (90°/270° swap X and Z). */
    public static BlockVector3 rotateSize(BlockVector3 size, int steps) {
        return (norm(steps) % 2 == 0)
                ? size
                : BlockVector3.at(size.z(), size.y(), size.x());
    }

    /**
     * Rotates a relative offset into the rotated footprint. Matches one WorldEdit {@code rotateY(90)}
     * step: raw {@code (x,z) -> (z,-x)}, then shift so the footprint's min corner stays at 0. Y is
     * unaffected.
     */
    public static BlockVector3 rotatePoint(BlockVector3 p, int steps, BlockVector3 size) {
        int s = norm(steps);
        int x = p.x(), z = p.z();
        int sx = size.x(), sz = size.z();
        for (int i = 0; i < s; i++) {
            int nx = z;
            int nz = sx - 1 - x;
            x = nx;
            z = nz;
            int tmp = sx; sx = sz; sz = tmp; // footprint swaps each step
        }
        return BlockVector3.at(x, p.y(), z);
    }

    /** Rotates a cardinal direction the same way as {@link #rotatePoint}. UP/DOWN are unaffected. */
    public static Direction rotateDirection(Direction dir, int steps) {
        if (dir == Direction.UP || dir == Direction.DOWN) return dir;
        Direction d = dir;
        for (int i = 0; i < norm(steps); i++) {
            switch (d) {
                case NORTH: d = Direction.WEST;  break;
                case WEST:  d = Direction.SOUTH; break;
                case SOUTH: d = Direction.EAST;  break;
                case EAST:  d = Direction.NORTH; break;
                default: break;
            }
        }
        return d;
    }

    /** Rotates a bound by rotating both corners and re-deriving min/max. */
    public static SegmentBound rotateBound(SegmentBound bound, int steps, BlockVector3 size) {
        BlockVector3 a = rotatePoint(bound.getMin(), steps, size);
        BlockVector3 b = rotatePoint(bound.getMax(), steps, size);
        BlockVector3 min = BlockVector3.at(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        BlockVector3 max = BlockVector3.at(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        return new SegmentBound(min, max);
    }
}
