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
 * Covers {@link DungeonGenerator#selectBankRelativeLocation}, which turns the per-segment
 * {@code BANK} markers into the one blueprint-relative cell the coin bank is built in.
 *
 * <p>This is the only source of the bank's location. Coming back null is not a generation failure —
 * the round simply plays with no bank (and nothing to score), which is why the generator warns
 * instead of aborting.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorBankTest {

    /** Builds a bare template of the given type, with an optional bank offset. */
    private static Segment template(String name, SegmentType type, BlockVector3 bankOffset) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                bankOffset, List.of(), null, List.of(), null, List.of(), List.of());
    }

    /** Places a template at a relative origin, the way the generator does during a blueprint run. */
    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    @Test
    void returnsNullWhenNoSegmentDefinesABank() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, null), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, null), 16, 0, 0));

        assertNull(DungeonGenerator.selectBankRelativeLocation(segments),
                "a hub saved before the BANK marker existed leaves the round with no bank");
    }

    @Test
    void addsTheOffsetToTheSegmentOrigin() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, BlockVector3.at(21, 1, 4)), 16, 64, 32));

        Vector bank = DungeonGenerator.selectBankRelativeLocation(segments);

        assertNotNull(bank);
        assertEquals(new Vector(37, 65, 36), bank);
    }

    @Test
    void prefersTheHubMarkerEvenWhenAnotherSegmentComesFirst() {
        List<PlacedSegment> segments = List.of(
                placed(template("corridor", SegmentType.CORRIDOR, BlockVector3.at(1, 0, 1)), 16, 0, 0),
                placed(template("hub", SegmentType.HUB, BlockVector3.at(5, 0, 5)), 0, 0, 0));

        Vector bank = DungeonGenerator.selectBankRelativeLocation(segments);

        assertEquals(new Vector(5, 0, 5), bank, "a HUB marker outranks one on any other segment");
    }

    @Test
    void fallsBackToANonHubMarkerWhenTheHubHasNone() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, null), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, BlockVector3.at(2, 0, 3)), 16, 0, 0));

        assertEquals(new Vector(18, 0, 3), DungeonGenerator.selectBankRelativeLocation(segments));
    }

    @Test
    void keepsTheFirstMarkerWhenTwoHubsWouldQualify() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub_a", SegmentType.HUB, BlockVector3.at(1, 0, 1)), 0, 0, 0),
                placed(template("hub_b", SegmentType.HUB, BlockVector3.at(9, 0, 9)), 0, 0, 0));

        assertEquals(new Vector(1, 0, 1), DungeonGenerator.selectBankRelativeLocation(segments));
    }
}
