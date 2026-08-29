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
 * Covers {@link DungeonGenerator#selectSafeExitRelativeLocation}, which turns the per-segment
 * SAFE_EXIT markers into the one blueprint-relative point the dungeon escapes through.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorSafeExitTest {

    /** Builds a bare template of the given type, with an optional safe exit offset. */
    private static Segment template(String name, SegmentType type, BlockVector3 safeExitOffset) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                safeExitOffset,
                null, List.of(), null, List.of(), null, List.of());
    }

    /** Places a template at a relative origin, the way the generator does during a blueprint run. */
    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    @Test
    void returnsNullWhenNoSegmentDefinesASafeExit() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, null), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, null), 16, 0, 0));

        assertNull(DungeonGenerator.selectSafeExitRelativeLocation(segments));
    }

    @Test
    void addsTheOffsetToTheSegmentOrigin() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, null), 0, 0, 0),
                placed(template("exit_room", SegmentType.LARGE_ROOM, BlockVector3.at(2, 1, 3)), 16, 64, 32));

        Vector safeExit = DungeonGenerator.selectSafeExitRelativeLocation(segments);

        assertNotNull(safeExit);
        assertEquals(new Vector(18, 65, 35), safeExit);
    }

    @Test
    void prefersTheHubMarkerEvenWhenAnotherSegmentComesFirst() {
        List<PlacedSegment> segments = List.of(
                placed(template("corridor", SegmentType.CORRIDOR, BlockVector3.at(1, 0, 1)), 16, 0, 0),
                placed(template("hub", SegmentType.HUB, BlockVector3.at(5, 0, 5)), 0, 0, 0));

        Vector safeExit = DungeonGenerator.selectSafeExitRelativeLocation(segments);

        assertEquals(new Vector(5, 0, 5), safeExit, "a HUB marker outranks one on any other segment");
    }

    @Test
    void keepsTheFirstMarkerWhenTwoHubsWouldQualify() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub_a", SegmentType.HUB, BlockVector3.at(1, 0, 1)), 0, 0, 0),
                placed(template("hub_b", SegmentType.HUB, BlockVector3.at(9, 0, 9)), 0, 0, 0));

        assertEquals(new Vector(1, 0, 1), DungeonGenerator.selectSafeExitRelativeLocation(segments));
    }
}
