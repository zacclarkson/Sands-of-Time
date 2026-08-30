package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the DEATH_CAGE / SAND_SACRIFICE half of the blueprint: the two marker selectors, and
 * {@link DungeonGenerator#reconcileSacrificePoints} which pairs them and derives a point for any
 * cage the templates left unpaired.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorSacrificeTest {

    /** Bare template carrying only the cage and sacrifice markers this suite cares about. */
    private static Segment template(String name, SegmentType type,
                                    List<BlockVector3> cages, List<BlockVector3> sacrifices) {
        return template(name, type, cages, sacrifices, BlockVector3.at(16, 8, 16));
    }

    private static Segment template(String name, SegmentType type, List<BlockVector3> cages,
                                    List<BlockVector3> sacrifices, BlockVector3 size) {
        return new Segment(
                name, type, name + ".schem", size,
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                sacrifices, List.of(),
                null,
                null, cages, null, List.of(), null, List.of(), List.of(), List.of());
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z, int rotationSteps) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0, rotationSteps);
    }

    // --- cage selector ---

    @Test
    void returnsEmptyWhenNoSegmentDefinesCages() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of(), List.of()), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, List.of(), List.of()), 16, 0, 0));

        assertTrue(DungeonGenerator.selectDeathCageRelativeLocations(segments).isEmpty());
    }

    @Test
    void cagesAreOffsetFromTheirSegmentOrigin() {
        Segment hub = template("hub", SegmentType.HUB, List.of(BlockVector3.at(3, 1, 4)), List.of());

        List<Vector> cages = DungeonGenerator.selectDeathCageRelativeLocations(List.of(placed(hub, 32, 64, 16)));

        assertEquals(List.of(new Vector(35, 65, 20)), cages);
    }

    @Test
    void hubCagesWinOverOtherSegments() {
        Segment hub = template("hub", SegmentType.HUB, List.of(BlockVector3.at(1, 1, 1)), List.of());
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(BlockVector3.at(2, 2, 2)), List.of());

        List<Vector> cages = DungeonGenerator.selectDeathCageRelativeLocations(
                List.of(placed(hub, 0, 0, 0), placed(room, 16, 0, 0)));

        assertEquals(List.of(new Vector(1, 1, 1)), cages, "a HUB's cages are used exclusively");
    }

    @Test
    void fallsBackToOtherSegmentsWhenTheHubDefinesNoCages() {
        Segment hub = template("hub", SegmentType.HUB, List.of(), List.of());
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(BlockVector3.at(2, 2, 2)), List.of());

        List<Vector> cages = DungeonGenerator.selectDeathCageRelativeLocations(
                List.of(placed(hub, 0, 0, 0), placed(room, 16, 0, 0)));

        assertEquals(List.of(new Vector(18, 2, 2)), cages);
    }

    @Test
    void capsCagesAtFourEvenWhenTheTemplateDefinesMore() {
        Segment hub = template("hub", SegmentType.HUB, List.of(
                BlockVector3.at(1, 1, 1), BlockVector3.at(2, 1, 1), BlockVector3.at(3, 1, 1),
                BlockVector3.at(4, 1, 1), BlockVector3.at(5, 1, 1), BlockVector3.at(6, 1, 1)), List.of());

        List<Vector> cages = DungeonGenerator.selectDeathCageRelativeLocations(List.of(placed(hub, 0, 0, 0)));

        assertEquals(4, cages.size(), "a team is at most four players");
        assertEquals(new Vector(1, 1, 1), cages.get(0), "the first four in placement order");
        assertEquals(new Vector(4, 1, 1), cages.get(3));
    }

    @Test
    void appliesSegmentRotationToCages() {
        Segment hub = template("hub", SegmentType.HUB, List.of(BlockVector3.at(1, 0, 0)), List.of(),
                BlockVector3.at(16, 8, 16));

        List<Vector> unrotated = DungeonGenerator.selectDeathCageRelativeLocations(List.of(placed(hub, 0, 0, 0, 0)));
        List<Vector> rotated = DungeonGenerator.selectDeathCageRelativeLocations(List.of(placed(hub, 0, 0, 0, 1)));

        assertNotEquals(unrotated, rotated, "a rotated placement must move its cage markers");
    }

    // --- sacrifice selector ---

    @Test
    void returnsEmptyWhenNoSegmentDefinesSacrificePoints() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of(), List.of()), 0, 0, 0));

        assertTrue(DungeonGenerator.selectSandSacrificeRelativeLocations(segments).isEmpty());
    }

    @Test
    void sacrificePointsAreOffsetFromTheirSegmentOrigin() {
        Segment hub = template("hub", SegmentType.HUB, List.of(), List.of(BlockVector3.at(3, 1, 6)));

        List<Vector> points = DungeonGenerator.selectSandSacrificeRelativeLocations(List.of(placed(hub, 32, 64, 16)));

        assertEquals(List.of(new Vector(35, 65, 22)), points);
    }

    @Test
    void hubSacrificePointsWinOverOtherSegments() {
        Segment hub = template("hub", SegmentType.HUB, List.of(), List.of(BlockVector3.at(1, 1, 1)));
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(), List.of(BlockVector3.at(2, 2, 2)));

        List<Vector> points = DungeonGenerator.selectSandSacrificeRelativeLocations(
                List.of(placed(hub, 0, 0, 0), placed(room, 16, 0, 0)));

        assertEquals(List.of(new Vector(1, 1, 1)), points);
    }

    // --- pairing and derivation ---

    private static final Vector HUB_CENTRE = new Vector(21, 7, 18);

    @Test
    void markersPairWithCagesInPlacementOrder() {
        List<Vector> cages = List.of(new Vector(30, 1, 4), new Vector(33, 1, 4));
        List<Vector> markers = List.of(new Vector(30, 1, 7), new Vector(33, 1, 7));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, markers, HUB_CENTRE);

        assertEquals(markers, paired, "the Nth marker frees the Nth cage");
    }

    @Test
    void everyCageGetsAPointEvenWithNoMarkersAtAll() {
        List<Vector> cages = List.of(new Vector(30, 1, 4), new Vector(33, 1, 4));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);

        assertEquals(cages.size(), paired.size(), "the lists must stay index-aligned");
    }

    @Test
    void unpairedCagesGetADerivedPointAndPairedOnesKeepTheirMarker() {
        List<Vector> cages = List.of(new Vector(30, 1, 4), new Vector(33, 1, 4), new Vector(36, 1, 4));
        List<Vector> markers = List.of(new Vector(30, 1, 9));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, markers, HUB_CENTRE);

        assertEquals(3, paired.size());
        assertEquals(new Vector(30, 1, 9), paired.get(0), "the one marker is honoured");
        assertEquals(new Vector(33, 1, 6), paired.get(1), "the rest are derived");
        assertEquals(new Vector(36, 1, 6), paired.get(2));
    }

    @Test
    void surplusMarkersAreDropped() {
        List<Vector> cages = List.of(new Vector(30, 1, 4));
        List<Vector> markers = List.of(new Vector(30, 1, 7), new Vector(33, 1, 7), new Vector(36, 1, 7));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, markers, HUB_CENTRE);

        assertEquals(List.of(new Vector(30, 1, 7)), paired, "one point per cage, no more");
    }

    @Test
    void theBundledHubsRowOfCagesDerivesInFrontOfItself() {
        // The bundled hub is 42x15x37 with its four cages in a row along X at z=4.
        List<Vector> cages = List.of(new Vector(30, 1, 4), new Vector(33, 1, 4),
                                     new Vector(36, 1, 4), new Vector(39, 1, 4));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);

        assertEquals(List.of(new Vector(30, 1, 6), new Vector(33, 1, 6),
                             new Vector(36, 1, 6), new Vector(39, 1, 6)), paired,
                "one shared +Z step, rather than some cages flipping onto the X axis");
    }

    @Test
    void derivedPointsNeverCollideWithEachOtherOrTheCages() {
        List<Vector> cages = List.of(new Vector(30, 1, 4), new Vector(33, 1, 4),
                                     new Vector(36, 1, 4), new Vector(39, 1, 4));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);

        Set<Vector> distinct = new HashSet<>(paired);
        assertEquals(paired.size(), distinct.size(), "two cages must never share a chest");
        List<Vector> overlap = new ArrayList<>(paired);
        overlap.retainAll(cages);
        assertTrue(overlap.isEmpty(), "a chest must not land inside a cage");
    }

    @Test
    void derivationPointsTowardTheHubCentreOnTheDominantAxis() {
        // Cages far to the +X side of the centre: the step should come back along -X, not Z.
        List<Vector> cages = List.of(new Vector(40, 1, 18));

        List<Vector> paired = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);

        assertEquals(List.of(new Vector(38, 1, 18)), paired);
    }

    @Test
    void aDegenerateCentroidStillDerivesDeterministically() {
        List<Vector> cages = List.of(HUB_CENTRE.clone());

        List<Vector> first = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);
        List<Vector> second = DungeonGenerator.reconcileSacrificePoints(cages, List.of(), HUB_CENTRE);

        assertEquals(first, second, "a cage sitting on the centre must not derive randomly");
        assertEquals(1, first.size());
        assertNotEquals(HUB_CENTRE, first.get(0), "and must still move off the cage");
    }

    @Test
    void noCagesMeansNoPoints() {
        assertTrue(DungeonGenerator.reconcileSacrificePoints(List.of(), List.of(new Vector(1, 1, 1)), HUB_CENTRE)
                .isEmpty());
    }

    // --- fallback cages ---

    @Test
    void fallbackCagesSurroundTheHubCentreRatherThanItsOrigin() {
        List<Vector> cages = DungeonGenerator.fallbackCageLocations(HUB_CENTRE, 0);

        assertEquals(4, cages.size());
        for (Vector cage : cages) {
            assertTrue(cage.getX() > 0 && cage.getZ() > 0,
                    "centred fallback cages stay inside the hub, unlike origin-anchored ones: " + cage);
        }
        assertEquals(4, new HashSet<>(cages).size(), "the four fallback cages are distinct");
    }

    @Test
    void fallbackCagesStandOnTheHubFloorNotAtItsMidHeight() {
        // Centring the cages horizontally is the point; taking the centre's *height* too would hang
        // them half the hub up, in mid-air.
        List<Vector> cages = DungeonGenerator.fallbackCageLocations(new Vector(21.0, 7.5, 18.5), 0);

        for (Vector cage : cages) {
            assertEquals(0.0, cage.getY(), "cages belong on the hub floor: " + cage);
        }
    }

    @Test
    void fallbackCagesSitOnWholeCells() {
        // A 42x15x37 hub centres on x.0/y.5/z.5; a cage on a block boundary would put its teleport
        // and its chest half a block out.
        List<Vector> cages = DungeonGenerator.fallbackCageLocations(new Vector(21.0, 7.5, 18.5), 64);

        for (Vector cage : cages) {
            assertEquals(Math.floor(cage.getX()), cage.getX(), "x should be whole: " + cage);
            assertEquals(Math.floor(cage.getY()), cage.getY(), "y should be whole: " + cage);
            assertEquals(Math.floor(cage.getZ()), cage.getZ(), "z should be whole: " + cage);
        }
    }

    @Test
    void theHubCentreComesFromTheHubsOwnSize() {
        Segment hub = template("hub", SegmentType.HUB, List.of(), List.of(), BlockVector3.at(42, 15, 37));

        Vector centre = DungeonGenerator.hubCentreOf(List.of(placed(hub, 0, 0, 0)), new Vector(0, 0, 0));

        assertEquals(21.0, centre.getX(), "the centre of the hub, not its origin corner");
        assertEquals(18.5, centre.getZ());
    }
}
