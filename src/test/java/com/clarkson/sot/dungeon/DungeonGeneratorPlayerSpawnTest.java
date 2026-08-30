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
 * Covers {@link DungeonGenerator#selectPlayerSpawnRelativeLocations}, which rolls the per-segment
 * PLAYER_SPAWN markers up into the blueprint-relative spawn points players are spread across.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorPlayerSpawnTest {

    /** Builds a bare template of the given type with the given player-spawn offsets. */
    private static Segment template(String name, SegmentType type, List<BlockVector3> playerSpawns) {
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, playerSpawns, List.of());
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    @Test
    void returnsEmptyWhenNoSegmentDefinesSpawns() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of()), 0, 0, 0),
                placed(template("corridor", SegmentType.CORRIDOR, List.of()), 16, 0, 0));

        assertTrue(DungeonGenerator.selectPlayerSpawnRelativeLocations(segments).isEmpty());
    }

    @Test
    void addsEachOffsetToItsSegmentOrigin() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB,
                        List.of(BlockVector3.at(1, 1, 1), BlockVector3.at(2, 1, 3))), 16, 64, 32));

        List<Vector> spawns = DungeonGenerator.selectPlayerSpawnRelativeLocations(segments);

        assertEquals(List.of(new Vector(17, 65, 33), new Vector(18, 65, 35)), spawns);
    }

    @Test
    void prefersHubSpawnsExclusivelyWhenAHubDefinesThem() {
        List<PlacedSegment> segments = List.of(
                placed(template("corridor", SegmentType.CORRIDOR, List.of(BlockVector3.at(0, 0, 0))), 16, 0, 0),
                placed(template("hub", SegmentType.HUB, List.of(BlockVector3.at(5, 0, 5))), 0, 0, 0));

        List<Vector> spawns = DungeonGenerator.selectPlayerSpawnRelativeLocations(segments);

        assertEquals(List.of(new Vector(5, 0, 5)), spawns,
                "when the HUB defines spawns, non-hub spawns are ignored");
    }

    @Test
    void fallsBackToNonHubSpawnsWhenNoHubDefinesAny() {
        List<PlacedSegment> segments = List.of(
                placed(template("hub", SegmentType.HUB, List.of()), 0, 0, 0),
                placed(template("room", SegmentType.LARGE_ROOM, List.of(BlockVector3.at(1, 0, 2))), 16, 0, 0));

        List<Vector> spawns = DungeonGenerator.selectPlayerSpawnRelativeLocations(segments);

        assertEquals(List.of(new Vector(17, 0, 2)), spawns);
    }
}
