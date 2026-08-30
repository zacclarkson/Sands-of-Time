package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the doorways {@link DungeonGenerator} records for the layout it builds.
 *
 * <p>Doors used to be derived at paste time by walking every entry point of every placed segment,
 * which produced a locked door at openings the generator never attached a neighbour to — the hub
 * template alone declares nine. The generator now reports the connections it actually made, and
 * the leftovers separately so they can be sealed as plain wall.
 *
 * <p>Blueprint-stage only: no schematics, no world, no server.
 */
class DungeonGeneratorDoorwayTest {

    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorDoorwayTest"));
        generator = new DungeonGenerator(plugin);
        generator.setSeed(20260829L);
    }

    // --- Same synthetic 5x5x5 segment set as DungeonGeneratorGenerationTest ---

    private static RelativeEntryPoint ep(int x, int y, int z, Direction dir) {
        return new RelativeEntryPoint(BlockVector3.at(x, y, z), dir);
    }

    private static Segment room(String name, SegmentType type, List<RelativeEntryPoint> entries,
                                VaultColor vault, VaultColor key) {
        BlockVector3 vaultOffset = (vault != null) ? BlockVector3.at(2, 1, 2) : null;
        BlockVector3 keyOffset = (key != null) ? BlockVector3.at(2, 1, 2) : null;
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(5, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, vault, key, vaultOffset, keyOffset,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), List.of());
    }

    private static Segment hub(int exits) {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(2 + i * 6, 1, 4, Direction.SOUTH));
        }
        int sizeX = 2 + (exits - 1) * 6 + 3;
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(sizeX, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, null, VaultColor.BLUE, null, BlockVector3.at(1, 1, 1),
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), List.of());
    }

    private List<Segment> fullSet() {
        List<Segment> set = new ArrayList<>();
        set.add(hub(8));
        set.add(room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)), null, null));
        for (VaultColor color : VaultColor.values()) {
            set.add(room("vault_" + color, SegmentType.VAULT,
                    List.of(ep(2, 1, 0, Direction.NORTH)), color, null));
        }
        for (VaultColor color : List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(room("key_" + color, SegmentType.END,
                    List.of(ep(2, 1, 0, Direction.NORTH)), null, color));
        }
        return set;
    }

    private DungeonBlueprint generate() {
        generator.setAvailableSegmentsForTest(fullSet());
        DungeonBlueprint bp = generator.generateDungeonLayout();
        assertNotNull(bp, "generation should finish with a valid layout");
        return bp;
    }

    /** Every rotated entry point of every placed segment, counted by the whole-block cell it occupies. */
    private static Map<BlockVector3, Integer> entryPointCounts(List<PlacedSegment> segments) {
        Map<BlockVector3, Integer> counts = new HashMap<>();
        for (PlacedSegment segment : segments) {
            Vector origin = segment.getWorldOrigin().toVector();
            for (RelativeEntryPoint entry : segment.getRotatedEntryPoints()) {
                BlockVector3 pos = entry.getRelativePosition();
                Vector cell = origin.clone().add(new Vector(pos.x(), pos.y(), pos.z()));
                counts.merge(cell(cell), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static BlockVector3 cell(Vector v) {
        return BlockVector3.at(v.getBlockX(), v.getBlockY(), v.getBlockZ());
    }

    @Test
    void oneDoorwayPerConnection() {
        DungeonBlueprint bp = generate();

        assertFalse(bp.getDoorways().isEmpty(), "a multi-segment layout has connections");
        // The DFS is a tree: every segment but the hub is reached through exactly one connection.
        assertEquals(bp.getRelativeSegments().size() - 1, bp.getDoorways().size(),
                "each placed segment beyond the hub is attached by exactly one doorway");
    }

    @Test
    void everyDoorwayIsSharedByTwoSegments() {
        DungeonBlueprint bp = generate();
        Map<BlockVector3, Integer> counts = entryPointCounts(bp.getRelativeSegments());

        for (Doorway doorway : bp.getDoorways()) {
            assertEquals(2, counts.getOrDefault(cell(doorway.getRelativePosition()), 0),
                    "a doorway is the shared cell of two connected segments: " + doorway);
        }
    }

    @Test
    void unusedOpeningsAreTheEntryPointsNoDoorwayUses() {
        DungeonBlueprint bp = generate();

        List<BlockVector3> doorwayCells = bp.getDoorways().stream()
                .map(d -> cell(d.getRelativePosition())).toList();
        for (Doorway opening : bp.getUnusedOpenings()) {
            assertFalse(doorwayCells.contains(cell(opening.getRelativePosition())),
                    "an opening that carries a door is not also sealed: " + opening);
        }

        // Doorways are counted twice (once per side), unused openings once, and together they
        // account for every entry point in the layout.
        int totalEntryPoints = entryPointCounts(bp.getRelativeSegments()).values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(totalEntryPoints, bp.getDoorways().size() * 2 + bp.getUnusedOpenings().size(),
                "every entry point either carries a door or gets sealed");
    }

    /**
     * A hand-built two-room layout, so the seal/door split is checked against a known answer rather
     * than whatever the DFS happened to produce.
     */
    @Test
    void findUnusedOpeningsKeepsOnlyTheEntriesWithNoConnection() {
        // Two 5x5x5 rooms meeting on the plane z=4: the left room's SOUTH exit at (2,1,4) is the
        // right room's NORTH entry, so that one cell is connected and the other two are not.
        Segment twoWay = room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)), null, null);
        PlacedSegment first = new PlacedSegment(twoWay, new Location(null, 0, 0, 0), 0);
        PlacedSegment second = new PlacedSegment(twoWay, new Location(null, 0, 0, 4), 1);
        List<PlacedSegment> layout = List.of(first, second);

        Doorway shared = new Doorway(new Vector(2, 1, 4), Direction.SOUTH);
        List<Doorway> unused = DungeonGenerator.findUnusedOpenings(layout, List.of(shared));

        List<BlockVector3> cells = unused.stream().map(d -> cell(d.getRelativePosition())).toList();
        assertEquals(2, unused.size(), "the two outward-facing ends are unused: " + unused);
        assertTrue(cells.contains(BlockVector3.at(2, 1, 0)), "the first room's NORTH end is unused");
        assertTrue(cells.contains(BlockVector3.at(2, 1, 8)), "the second room's SOUTH end is unused");
        assertFalse(cells.contains(BlockVector3.at(2, 1, 4)), "the shared cell carries a door, not a seal");

        assertTrue(DungeonGenerator.findUnusedOpenings(List.of(), List.of()).isEmpty(),
                "an empty layout has nothing to seal");
    }

    @Test
    void withNoConnectionsRecordedEveryEntryPointIsUnused() {
        DungeonBlueprint bp = generate();
        List<Doorway> allEntries = DungeonGenerator.findUnusedOpenings(bp.getRelativeSegments(), List.of());

        int totalEntryPoints = entryPointCounts(bp.getRelativeSegments()).values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(totalEntryPoints, allEntries.size());
    }

    @Test
    void doorwaysAreRelativeToTheBlueprintOrigin() {
        DungeonBlueprint bp = generate();
        Map<Vector, PlacedSegment> originsByCell = new HashMap<>();
        for (PlacedSegment segment : bp.getRelativeSegments()) {
            originsByCell.put(segment.getWorldOrigin().toVector(), segment);
        }

        for (Doorway doorway : bp.getDoorways()) {
            Location location = new Location(null, doorway.getRelativePosition().getX(),
                    doorway.getRelativePosition().getY(), doorway.getRelativePosition().getZ());
            assertTrue(bp.getRelativeSegments().stream().anyMatch(s -> s.getWorldBounds().contains(location)),
                    "a doorway sits inside the segments it connects: " + doorway);
        }
    }
}
