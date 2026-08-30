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
 * Covers {@link DungeonGenerator#selectSandTradeRelativeLocations}, the one marker selector here
 * that is deliberately <em>not</em> hub-wins.
 *
 * <p>That is the headline behaviour: a trade point pays more the deeper it sits, so they belong out
 * in the branches. Gathering only the HUB's markers whenever the hub happened to carry one — what
 * every other selector does — would throw away every trade point in the dungeon and leave only
 * depth-0 ones.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorSandTradeTest {

    /** Bare template carrying only the trade markers this suite cares about. */
    private static Segment template(String name, SegmentType type, List<BlockVector3> trades) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), trades);
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z, int rotationSteps) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0, rotationSteps);
    }

    @Test
    void returnsEmptyWhenNoSegmentDefinesTradePoints() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of()), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, List.of()), 16, 0, 0));

        assertTrue(DungeonGenerator.selectSandTradeRelativeLocations(segments).isEmpty());
    }

    @Test
    void tradePointsAreOffsetFromTheirSegmentOrigin() {
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(BlockVector3.at(3, 1, 6)));

        List<Vector> points = DungeonGenerator.selectSandTradeRelativeLocations(List.of(placed(room, 32, 64, 16)));

        assertEquals(List.of(new Vector(35, 65, 22)), points);
    }

    @Test
    void everySegmentsTradePointsAreGatheredRatherThanTheHubWinning() {
        // The whole point of the feature: a hub that happens to carry a trade marker must not
        // suppress the deep ones out in the branches, the way it does for cages and sacrifices.
        Segment hub = template("hub", SegmentType.HUB, List.of(BlockVector3.at(1, 1, 1)));
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(BlockVector3.at(2, 2, 2)));

        List<Vector> points = DungeonGenerator.selectSandTradeRelativeLocations(
                List.of(placed(hub, 0, 0, 0), placed(room, 16, 0, 0)));

        assertEquals(List.of(new Vector(1, 1, 1), new Vector(18, 2, 2)), points,
                "a hub marker must not shadow the branch markers");
    }

    @Test
    void collectsEveryMarkerOnASingleSegment() {
        Segment room = template("room", SegmentType.SMALL_ROOM,
                List.of(BlockVector3.at(1, 1, 1), BlockVector3.at(5, 1, 9)));

        List<Vector> points = DungeonGenerator.selectSandTradeRelativeLocations(List.of(placed(room, 0, 0, 0)));

        assertEquals(List.of(new Vector(1, 1, 1), new Vector(5, 1, 9)), points);
    }

    @Test
    void appliesSegmentRotationToTradePoints() {
        Segment room = template("room", SegmentType.SMALL_ROOM, List.of(BlockVector3.at(1, 0, 0)));

        List<Vector> unrotated = DungeonGenerator.selectSandTradeRelativeLocations(List.of(placed(room, 0, 0, 0, 0)));
        List<Vector> rotated = DungeonGenerator.selectSandTradeRelativeLocations(List.of(placed(room, 0, 0, 0, 1)));

        assertNotEquals(unrotated, rotated, "a rotated placement must move its trade markers");
    }
}
