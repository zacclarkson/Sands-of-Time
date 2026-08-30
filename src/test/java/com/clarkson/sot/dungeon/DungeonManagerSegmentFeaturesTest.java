package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.dungeon.segment.SegmentGeometry;
import com.clarkson.sot.dungeon.segment.SegmentRotation;
import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.entities.Area;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers {@link DungeonManager#resolveGateGroups} and {@link DungeonManager#resolveVaultDoors}, which
 * turn a placed segment's builder-authored GATE/LEVER/VAULT_DOOR markers into absolute geometry.
 *
 * <p>The pairing is what matters: a lever opens the gates of <em>its own</em> segment, so a bug that
 * flattened gates across the dungeon would hand one team's lever another room's wall.
 *
 * <p>Only a mocked World is needed -- these are pure coordinate maths and never touch a block.
 */
class DungeonManagerSegmentFeaturesTest {

    private static final Logger LOG = Logger.getLogger("DungeonManagerSegmentFeaturesTest");
    private static final BlockVector3 SIZE = BlockVector3.at(16, 8, 12); // non-cube, so X<->Z swaps show

    private final World world = mock(World.class);

    /** A bare template carrying only the gate/lever/vault-door markers under test. */
    private static Segment template(String name, List<SegmentBound> gates, BlockVector3 leverOffset,
                                    SegmentBound vaultDoorBound, VaultColor containedVault,
                                    BlockVector3 vaultOffset) {
        return new Segment(
                name, SegmentType.SMALL_ROOM, name + ".schem", SIZE,
                List.of(), List.of(), List.of(), List.of(),
                0, containedVault, null, vaultOffset, null,
                vaultDoorBound, gates, leverOffset,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), List.of());
    }

    private PlacedSegment placed(Segment segment, int x, int y, int z) {
        return placed(segment, x, y, z, 0);
    }

    private PlacedSegment placed(Segment segment, int x, int y, int z, int rotationSteps) {
        return new PlacedSegment(segment, new Location(world, x, y, z), 0, rotationSteps);
    }

    private static SegmentBound bound(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new SegmentBound(BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    private static void assertAreaSpans(Area area, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        assertEquals(minX, area.getMinPoint().getBlockX(), "min x");
        assertEquals(minY, area.getMinPoint().getBlockY(), "min y");
        assertEquals(minZ, area.getMinPoint().getBlockZ(), "min z");
        assertEquals(maxX, area.getMaxPoint().getBlockX(), "max x");
        assertEquals(maxY, area.getMaxPoint().getBlockY(), "max y");
        assertEquals(maxZ, area.getMaxPoint().getBlockZ(), "max z");
    }

    // --- gates ---

    @Test
    void addsEachGateAndItsLeverToTheSegmentOrigin() {
        Segment segment = template("gateroom",
                List.of(bound(2, 0, 5, 4, 3, 5)), BlockVector3.at(1, 1, 5), null, null, null);

        List<GateGroup> groups = DungeonManager.resolveGateGroups(
                List.of(placed(segment, 100, 64, 200, 0)), LOG);

        assertEquals(1, groups.size());
        GateGroup group = groups.get(0);
        assertEquals(101, group.getLeverLocation().getBlockX(), "lever x");
        assertEquals(65, group.getLeverLocation().getBlockY(), "lever y");
        assertEquals(205, group.getLeverLocation().getBlockZ(), "lever z");
        assertEquals(1, group.getGateBounds().size());
        assertAreaSpans(group.getGateBounds().get(0), 102, 64, 205, 104, 67, 205);
    }

    @Test
    void pairsEachSegmentsGatesWithItsOwnLever() {
        Segment first = template("first",
                List.of(bound(2, 0, 5, 4, 3, 5)), BlockVector3.at(1, 1, 5), null, null, null);
        Segment second = template("second",
                List.of(bound(6, 0, 7, 8, 3, 7)), BlockVector3.at(5, 1, 7), null, null, null);

        List<GateGroup> groups = DungeonManager.resolveGateGroups(
                List.of(placed(first, 0, 64, 0), placed(second, 100, 64, 0)), LOG);

        assertEquals(2, groups.size());
        GateGroup firstGroup = groups.stream().filter(g -> g.getSegmentName().equals("first")).findFirst().orElseThrow();
        GateGroup secondGroup = groups.stream().filter(g -> g.getSegmentName().equals("second")).findFirst().orElseThrow();

        // Each lever's gate must sit inside its own segment's footprint, never the other's.
        assertEquals(0, firstGroup.getLeverLocation().getBlockX() / 100, "first lever stays in the first segment");
        assertEquals(1, secondGroup.getLeverLocation().getBlockX() / 100, "second lever stays in the second segment");
        assertAreaSpans(firstGroup.getGateBounds().get(0), 2, 64, 5, 4, 67, 5);
        assertAreaSpans(secondGroup.getGateBounds().get(0), 106, 64, 7, 108, 67, 7);
    }

    @Test
    void opensGatesWithNoLeverRatherThanSealingThem() {
        // SaveSegmentCommand refuses this, but a hand-edited JSON can still carry it. A gate nothing
        // can raise would lock the loot away for the round, so the gate is simply not built.
        Segment segment = template("broken", List.of(bound(2, 0, 5, 4, 3, 5)), null, null, null, null);

        assertTrue(DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).isEmpty(),
                "gates with no lever must be left open, not built shut");
    }

    @Test
    void ignoresALeverWithNoGates() {
        Segment segment = template("lonely", List.of(), BlockVector3.at(1, 1, 5), null, null, null);

        assertTrue(DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).isEmpty(),
                "a lever with nothing to open is not a gate group");
    }

    @Test
    void appliesSegmentRotationToGatesAndLevers() {
        SegmentBound gate = bound(2, 0, 5, 4, 3, 5);
        BlockVector3 lever = BlockVector3.at(1, 1, 5);
        Segment segment = template("rotated", List.of(gate), lever, null, null, null);
        // rotationSteps are 90-degree steps, not degrees: 1 == a quarter turn.
        PlacedSegment rotated = placed(segment, 100, 64, 200, 1);

        List<GateGroup> groups = DungeonManager.resolveGateGroups(List.of(rotated), LOG);
        GateGroup group = groups.get(0);

        BlockVector3 expectedLever = rotated.getRotatedOffset(lever);
        assertEquals(100 + expectedLever.x(), group.getLeverLocation().getBlockX(), "rotated lever x");
        assertEquals(200 + expectedLever.z(), group.getLeverLocation().getBlockZ(), "rotated lever z");
        assertNotEquals(101, group.getLeverLocation().getBlockX(),
                "the unrotated offset would make this assertion vacuous");

        SegmentBound expectedGate = SegmentRotation.rotateBound(gate, 1, SIZE);
        assertAreaSpans(group.getGateBounds().get(0),
                100 + expectedGate.getMin().x(), 64 + expectedGate.getMin().y(), 200 + expectedGate.getMin().z(),
                100 + expectedGate.getMax().x(), 64 + expectedGate.getMax().y(), 200 + expectedGate.getMax().z());
    }

    /** A rotated gate must still come out with min <= max on every axis, or Door builds no blocks. */
    @Test
    void rotatedGateAreaKeepsItsMinimumCornerMinimal() {
        SegmentBound gate = bound(2, 0, 5, 4, 3, 5);
        Segment segment = template("rotated", List.of(gate), BlockVector3.at(1, 1, 5), null, null, null);

        for (int steps = 0; steps < 4; steps++) {
            Area area = SegmentGeometry.toAbsoluteArea(placed(segment, 100, 64, 200, steps), gate);
            assertTrue(area.getMinPoint().getBlockX() <= area.getMaxPoint().getBlockX(), "x at step " + steps);
            assertTrue(area.getMinPoint().getBlockZ() <= area.getMaxPoint().getBlockZ(), "z at step " + steps);
        }
    }

    // --- vault doors ---

    @Test
    void resolvesVaultDoorColourFromTheVaultTheSegmentContains() {
        Segment segment = template("vaultroom", List.of(), null,
                bound(3, 0, 8, 5, 3, 8), VaultColor.GREEN, BlockVector3.at(7, 1, 4));

        List<VaultDoorPlacement> doors = DungeonManager.resolveVaultDoors(
                List.of(placed(segment, 100, 64, 200, 0)), LOG);

        assertEquals(1, doors.size());
        assertEquals(VaultColor.GREEN, doors.get(0).getColor());
        assertAreaSpans(doors.get(0).getBounds(), 103, 64, 208, 105, 67, 208);
    }

    @Test
    void skipsAVaultDoorWithNoColour() {
        // /sotmode VAULT_DOOR with no colour argument stores none, leaving a wall nothing can open.
        Segment segment = template("colourless", List.of(), null, bound(3, 0, 8, 5, 3, 8), null, null);

        assertTrue(DungeonManager.resolveVaultDoors(List.of(placed(segment, 0, 64, 0)), LOG).isEmpty(),
                "a colourless vault door has nothing that could ever open it");
    }

    @Test
    void appliesSegmentRotationToVaultDoors() {
        SegmentBound door = bound(3, 0, 8, 5, 3, 8);
        Segment segment = template("vaultroom", List.of(), null, door, VaultColor.RED, BlockVector3.at(7, 1, 4));
        PlacedSegment rotated = placed(segment, 100, 64, 200, 3);

        Area bounds = DungeonManager.resolveVaultDoors(List.of(rotated), LOG).get(0).getBounds();

        SegmentBound expected = SegmentRotation.rotateBound(door, 3, SIZE);
        assertAreaSpans(bounds,
                100 + expected.getMin().x(), 64 + expected.getMin().y(), 200 + expected.getMin().z(),
                100 + expected.getMax().x(), 64 + expected.getMax().y(), 200 + expected.getMax().z());
        // Compare against the unrotated placement rather than a hardcoded coordinate: at three steps
        // this bound's min X happens to land back on its unrotated value, so an X-only guard passes
        // for free.
        Area unrotated = SegmentGeometry.toAbsoluteArea(placed(segment, 100, 64, 200, 0), door);
        assertNotEquals(unrotated.getMinPoint().getBlockZ(), bounds.getMinPoint().getBlockZ(),
                "a rotated segment must move its vault door with it");
    }

    @Test
    void ignoresSegmentsWithNoGateOrVaultDoorMarkers() {
        Segment plain = template("plain", List.of(), null, null, null, null);
        List<PlacedSegment> segments = List.of(placed(plain, 0, 64, 0));

        assertTrue(DungeonManager.resolveGateGroups(segments, LOG).isEmpty());
        assertTrue(DungeonManager.resolveVaultDoors(segments, LOG).isEmpty());
    }
}
