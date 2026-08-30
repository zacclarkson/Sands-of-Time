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
 * Covers {@link DungeonGenerator#selectTimerBaseRelativeLocation}, which turns the per-segment
 * {@code TIMER} markers into the one blueprint-relative point the visual sand column stands on.
 *
 * <p>This is the only source of the column's location: when it comes back null the team plays with
 * no sand column at all, rather than getting one at the lobby spawn.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorTimerTest {

    /** Builds a bare template of the given type, with an optional timer offset. */
    private static Segment template(String name, SegmentType type, BlockVector3 timerOffset) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), timerOffset, List.of(), List.of(), List.of());
    }

    /** Places a template at a relative origin, the way the generator does during a blueprint run. */
    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    @Test
    void returnsNullWhenNoSegmentDefinesATimer() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, null), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, null), 16, 0, 0));

        assertNull(DungeonGenerator.selectTimerBaseRelativeLocation(segments));
    }

    @Test
    void addsTheOffsetToTheSegmentOrigin() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, BlockVector3.at(21, 1, 18)), 16, 64, 32));

        Vector timerBase = DungeonGenerator.selectTimerBaseRelativeLocation(segments);

        assertNotNull(timerBase);
        assertEquals(new Vector(37, 65, 50), timerBase);
    }

    @Test
    void prefersTheHubMarkerEvenWhenAnotherSegmentComesFirst() {
        List<PlacedSegment> segments = List.of(
                placed(template("corridor", SegmentType.CORRIDOR, BlockVector3.at(1, 0, 1)), 16, 0, 0),
                placed(template("hub", SegmentType.HUB, BlockVector3.at(5, 0, 5)), 0, 0, 0));

        Vector timerBase = DungeonGenerator.selectTimerBaseRelativeLocation(segments);

        assertEquals(new Vector(5, 0, 5), timerBase, "a HUB marker outranks one on any other segment");
    }

    @Test
    void keepsTheFirstMarkerWhenTwoHubsWouldQualify() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub_a", SegmentType.HUB, BlockVector3.at(1, 0, 1)), 0, 0, 0),
                placed(template("hub_b", SegmentType.HUB, BlockVector3.at(9, 0, 9)), 0, 0, 0));

        assertEquals(new Vector(1, 0, 1), DungeonGenerator.selectTimerBaseRelativeLocation(segments));
    }
}
