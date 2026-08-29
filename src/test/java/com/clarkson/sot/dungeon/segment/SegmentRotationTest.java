package com.clarkson.sot.dungeon.segment;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link SegmentRotation} to WorldEdit's {@code AffineTransform.rotateY} (the transform used for the
 * real schematic paste), so rotated marker offsets land on the same cells as the rotated blocks.
 */
class SegmentRotationTest {

    private static final BlockVector3 SIZE = BlockVector3.at(7, 4, 3); // non-cube so X<->Z swaps show up

    @Test
    void fourRotationsAreIdentity() {
        for (BlockVector3 p : corners(SIZE)) {
            assertEquals(p, SegmentRotation.rotatePoint(p, 4, SIZE), "point 4x");
        }
        assertEquals(SIZE, SegmentRotation.rotateSize(SIZE, 4));
        for (Direction d : Direction.values()) {
            assertEquals(d, SegmentRotation.rotateDirection(d, 4), "dir 4x " + d);
        }
    }

    @Test
    void directionCycleMatchesRotateY90() {
        assertEquals(Direction.WEST, SegmentRotation.rotateDirection(Direction.NORTH, 1));
        assertEquals(Direction.SOUTH, SegmentRotation.rotateDirection(Direction.WEST, 1));
        assertEquals(Direction.EAST, SegmentRotation.rotateDirection(Direction.SOUTH, 1));
        assertEquals(Direction.NORTH, SegmentRotation.rotateDirection(Direction.EAST, 1));
        assertEquals(Direction.UP, SegmentRotation.rotateDirection(Direction.UP, 3));
    }

    @Test
    void rotatedPointsStayInsideRotatedFootprint() {
        for (int steps = 0; steps < 4; steps++) {
            BlockVector3 rs = SegmentRotation.rotateSize(SIZE, steps);
            for (BlockVector3 p : allCells(SIZE)) {
                BlockVector3 r = SegmentRotation.rotatePoint(p, steps, SIZE);
                assertTrue(r.x() >= 0 && r.x() < rs.x() && r.z() >= 0 && r.z() < rs.z(),
                        "in footprint: " + p + " step " + steps + " -> " + r + " size " + rs);
            }
        }
    }

    @Test
    void agreesWithWorldEditRotateY() {
        for (int steps = 0; steps < 4; steps++) {
            AffineTransform t = new AffineTransform().rotateY(90.0 * steps);
            // Min corner of the transformed footprint, used to normalise back to non-negative coords.
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (BlockVector3 c : corners(SIZE)) {
                Vector3 v = t.apply(Vector3.at(c.x(), c.y(), c.z()));
                minX = Math.min(minX, (int) Math.round(v.x()));
                minZ = Math.min(minZ, (int) Math.round(v.z()));
            }
            for (BlockVector3 p : allCells(SIZE)) {
                Vector3 v = t.apply(Vector3.at(p.x(), p.y(), p.z()));
                BlockVector3 expected = BlockVector3.at(
                        (int) Math.round(v.x()) - minX, p.y(), (int) Math.round(v.z()) - minZ);
                assertEquals(expected, SegmentRotation.rotatePoint(p, steps, SIZE),
                        "WE agreement at " + p + " step " + steps);
            }
        }
    }

    // --- helpers ---

    private static java.util.List<BlockVector3> corners(BlockVector3 s) {
        java.util.List<BlockVector3> out = new java.util.ArrayList<>();
        for (int x : new int[]{0, s.x() - 1})
            for (int y : new int[]{0, s.y() - 1})
                for (int z : new int[]{0, s.z() - 1})
                    out.add(BlockVector3.at(x, y, z));
        return out;
    }

    private static java.util.List<BlockVector3> allCells(BlockVector3 s) {
        java.util.List<BlockVector3> out = new java.util.ArrayList<>();
        for (int x = 0; x < s.x(); x++)
            for (int y = 0; y < s.y(); y++)
                for (int z = 0; z < s.z(); z++)
                    out.add(BlockVector3.at(x, y, z));
        return out;
    }
}
