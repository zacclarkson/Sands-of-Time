package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link DungeonGenerator#selectSandTimerRelativeLocations}, which rolls the per-segment
 * TIMER_DEPOSIT markers up into the blueprint-relative cells where carried sand is spent on the timer.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorSandTimerTest {

    /** Builds a bare template of the given type with the given sand deposit offsets. */
    private static Segment template(String name, SegmentType type, List<BlockVector3> sandTimers) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, sandTimers, null, List.of());
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    @Test
    void returnsEmptyWhenNoSegmentDefinesDeposits() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of()), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, List.of()), 16, 0, 0));

        assertTrue(DungeonGenerator.selectSandTimerRelativeLocations(segments).isEmpty(),
                "templates saved before the TIMER_DEPOSIT marker existed must still generate");
    }

    @Test
    void addsEachOffsetToItsSegmentOrigin() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB,
                        List.of(BlockVector3.at(1, 2, 1), BlockVector3.at(2, 2, 3))), 16, 64, 32));

        List<Vector> deposits = DungeonGenerator.selectSandTimerRelativeLocations(segments);

        assertEquals(List.of(new Vector(17, 66, 33), new Vector(18, 66, 35)), deposits);
    }

    @Test
    void prefersHubDepositsExclusivelyWhenAHubDefinesThem() {
        List<PlacedSegment> segments = List.of(
                placed(template("corridor", SegmentType.CORRIDOR, List.of(BlockVector3.at(0, 0, 0))), 16, 0, 0),
                placed(template("hub", SegmentType.HUB, List.of(BlockVector3.at(5, 2, 5))), 0, 0, 0));

        List<Vector> deposits = DungeonGenerator.selectSandTimerRelativeLocations(segments);

        assertEquals(List.of(new Vector(5, 2, 5)), deposits,
                "the deposit belongs beside the hub's timer column, so a stray marker in a random "
                        + "room must not arm a second deposit site");
    }

    @Test
    void fallsBackToNonHubDepositsWhenNoHubDefinesAny() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of()), 0, 0, 0),
                placed(template("room", SegmentType.LARGE_ROOM, List.of(BlockVector3.at(1, 0, 2))), 16, 0, 0));

        List<Vector> deposits = DungeonGenerator.selectSandTimerRelativeLocations(segments);

        assertEquals(List.of(new Vector(17, 0, 2)), deposits);
    }

    @Test
    void appliesSegmentRotation() {
        Segment hub = template("hub", SegmentType.HUB, List.of(BlockVector3.at(1, 2, 0)));
        // rotationSteps are 90-degree steps, not degrees: 1 == a quarter turn.
        PlacedSegment rotated = new PlacedSegment(hub, new Location(null, 100, 0, 100), 0, 1);

        List<Vector> deposits = DungeonGenerator.selectSandTimerRelativeLocations(List.of(rotated));

        BlockVector3 expected = rotated.getRotatedOffset(BlockVector3.at(1, 2, 0));
        assertEquals(List.of(new Vector(100 + expected.x(), 0 + expected.y(), 100 + expected.z())), deposits,
                "a rotated hub must move its deposit cells with it");
        assertNotEquals(List.of(new Vector(101, 2, 100)), deposits,
                "the unrotated offset would be a no-op assertion");
    }
}
