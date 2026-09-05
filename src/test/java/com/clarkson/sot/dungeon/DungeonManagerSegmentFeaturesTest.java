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
        return template(name, SegmentType.SMALL_ROOM, gates, leverOffset, List.of(), List.of(),
                vaultDoorBound, containedVault, vaultOffset);
    }

    /** A template with gates and the sacrifice chests (with costs) that may open them. */
    private static Segment gated(String name, SegmentType type, List<SegmentBound> gates,
                                 BlockVector3 leverOffset, List<BlockVector3> sacrifices, List<Integer> costs) {
        return template(name, type, gates, leverOffset, sacrifices, costs, null, null, null);
    }

    private static Segment template(String name, SegmentType type, List<SegmentBound> gates,
                                    BlockVector3 leverOffset, List<BlockVector3> sacrifices,
                                    List<Integer> costs, SegmentBound vaultDoorBound,
                                    VaultColor containedVault, BlockVector3 vaultOffset) {
        return new Segment(
                name, type, name + ".schem", SIZE,
                List.of(), List.of(), List.of(), List.of(),
                0, containedVault, null, vaultOffset, null,
                vaultDoorBound, gates, leverOffset,
                sacrifices, List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), costs);
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
    void opensGatesWithNeitherLeverNorSacrificeChestRatherThanSealingThem() {
        // SaveSegmentCommand refuses this, but a hand-edited JSON can still carry it. A gate nothing
        // can raise would lock the loot away for the round, so the gate is simply not built.
        Segment segment = template("broken", List.of(bound(2, 0, 5, 4, 3, 5)), null, null, null, null);

        assertTrue(DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).isEmpty(),
                "gates with nothing to open them must be left open, not built shut");
    }

    // --- gate sacrifice chests ---

    @Test
    void aSacrificeChestStandsInForTheLever() {
        // The sand-for-money trade: no lever at all, the chest in front of the gate is what opens it.
        Segment segment = gated("paywall", SegmentType.SMALL_ROOM, List.of(bound(2, 0, 5, 4, 3, 5)),
                null, List.of(BlockVector3.at(3, 0, 3)), List.of(3));

        List<GateGroup> groups = DungeonManager.resolveGateGroups(List.of(placed(segment, 100, 64, 200)), LOG);

        assertEquals(1, groups.size(), "a sacrifice-only segment is still a gate group");
        GateGroup group = groups.get(0);
        assertFalse(group.hasLever());
        assertNull(group.getLeverLocation());
        assertEquals(1, group.getSacrificePlacements().size());
        GateGroup.SacrificePlacement chest = group.getSacrificePlacements().get(0);
        assertEquals(103, chest.location().getBlockX(), "chest x");
        assertEquals(64, chest.location().getBlockY(), "chest y");
        assertEquals(203, chest.location().getBlockZ(), "chest z");
        assertEquals(3, chest.cost(), "the builder's price rides with the marker");
        assertAreaSpans(group.getGateBounds().get(0), 102, 64, 205, 104, 67, 205);
    }

    @Test
    void aLeverAndASacrificeChestCoexistOnTheSameGates() {
        Segment segment = gated("either", SegmentType.SMALL_ROOM, List.of(bound(2, 0, 5, 4, 3, 5)),
                BlockVector3.at(1, 1, 5), List.of(BlockVector3.at(3, 0, 3)), List.of(2));

        GateGroup group = DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).get(0);

        assertTrue(group.hasLever());
        assertEquals(1, group.getSacrificePlacements().size());
        assertEquals(2, group.getSacrificePlacements().get(0).cost());
    }

    @Test
    void costsRideWithTheirMarkerIndex() {
        Segment segment = gated("two_chests", SegmentType.SMALL_ROOM, List.of(bound(2, 0, 5, 4, 3, 5)),
                null, List.of(BlockVector3.at(3, 0, 3), BlockVector3.at(6, 0, 3)), List.of(1, 5));

        GateGroup group = DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).get(0);

        assertEquals(2, group.getSacrificePlacements().size());
        assertEquals(3, group.getSacrificePlacements().get(0).location().getBlockX());
        assertEquals(1, group.getSacrificePlacements().get(0).cost());
        assertEquals(6, group.getSacrificePlacements().get(1).location().getBlockX());
        assertEquals(5, group.getSacrificePlacements().get(1).cost());
    }

    @Test
    void hubSacrificeMarkersAreNeverGateSacrifices() {
        // A HUB's SAND_SACRIFICE markers are death-cage points (blueprint data). A hub with gates and
        // no lever therefore has nothing that could open them, and its gates are left open.
        Segment hub = gated("hub", SegmentType.HUB, List.of(bound(2, 0, 5, 4, 3, 5)),
                null, List.of(BlockVector3.at(3, 0, 3)), List.of(1));

        assertTrue(DungeonManager.resolveGateGroups(List.of(placed(hub, 0, 64, 0)), LOG).isEmpty(),
                "a hub's sacrifice chests must never be registered as gate sacrifices");
    }

    @Test
    void aHubWithGatesAndALeverCarriesNoSacrificeChests() {
        Segment hub = gated("hub", SegmentType.HUB, List.of(bound(2, 0, 5, 4, 3, 5)),
                BlockVector3.at(1, 1, 5), List.of(BlockVector3.at(3, 0, 3)), List.of(1));

        GateGroup group = DungeonManager.resolveGateGroups(List.of(placed(hub, 0, 64, 0)), LOG).get(0);

        assertTrue(group.hasLever());
        assertTrue(group.getSacrificePlacements().isEmpty(), "the hub's chests stay cage points");
    }

    @Test
    void aSacrificeChestOnASegmentWithNoGatesIsIgnored() {
        Segment segment = gated("nothing_to_open", SegmentType.SMALL_ROOM, List.of(),
                null, List.of(BlockVector3.at(3, 0, 3)), List.of(1));

        assertTrue(DungeonManager.resolveGateGroups(List.of(placed(segment, 0, 64, 0)), LOG).isEmpty(),
                "a chest with no gates to open is not a gate group");
    }

    @Test
    void appliesSegmentRotationToSacrificeChests() {
        // Off the diagonal, so a quarter turn moves X: (x,z) -> (z, sizeX-1-x) gives (7, 12).
        BlockVector3 chest = BlockVector3.at(3, 0, 7);
        Segment segment = gated("rotated", SegmentType.SMALL_ROOM, List.of(bound(2, 0, 5, 4, 3, 5)),
                null, List.of(chest), List.of(1));
        PlacedSegment rotated = placed(segment, 100, 64, 200, 1);

        GateGroup group = DungeonManager.resolveGateGroups(List.of(rotated), LOG).get(0);

        BlockVector3 expected = rotated.getRotatedOffset(chest);
        Location actual = group.getSacrificePlacements().get(0).location();
        assertEquals(100 + expected.x(), actual.getBlockX(), "rotated chest x");
        assertEquals(200 + expected.z(), actual.getBlockZ(), "rotated chest z");
        assertNotEquals(103, actual.getBlockX(), "the unrotated offset would make this assertion vacuous");
    }

    @Test
    void aGateGroupNeedsSomethingToOpenIt() {
        assertThrows(IllegalArgumentException.class, () -> new GateGroup(null,
                List.of(new Area(new Location(world, 0, 0, 0), new Location(world, 1, 1, 1))),
                List.of(), "nothing"));
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
