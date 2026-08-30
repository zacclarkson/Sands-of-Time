package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the MOB_SPAWNER half of {@code DungeonGenerator.consolidateFeatureLocations} (#46).
 *
 * <p>The markers were saved into every segment template and read back into {@link Segment}, but the
 * generator dropped them on the floor: {@link DungeonBlueprint} had no field for them, so no mob
 * could ever spawn. These tests pin the consolidation, including the rotation the DFS applies —
 * a spawner in a segment the generator turned must land where the room actually is.
 *
 * <p>The blueprint stage is pure logic (no schematics, no world), so this runs without a server.
 * The segment set mirrors {@code DungeonGeneratorGenerationTest}'s: a wide hub whose straight SOUTH
 * exits sit in disjoint x-columns, feeding NORTH-entry corridors and vault/key rooms.
 */
class DungeonGeneratorMobSpawnerTest {

    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorMobSpawnerTest"));
        generator = new DungeonGenerator(plugin);
        generator.setSeed(20260830L);
    }

    // --- Segment builders (5x5x5 rooms, entries centred on their face) ---

    private static RelativeEntryPoint ep(int x, int y, int z, Direction dir) {
        return new RelativeEntryPoint(BlockVector3.at(x, y, z), dir);
    }

    private static Segment room(String name, SegmentType type, List<RelativeEntryPoint> entries,
                                VaultColor vault, VaultColor key, List<BlockVector3> mobSpawners) {
        BlockVector3 vaultOffset = (vault != null) ? BlockVector3.at(2, 1, 2) : null;
        BlockVector3 keyOffset = (key != null) ? BlockVector3.at(2, 1, 2) : null;
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(5, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, vault, key, vaultOffset, keyOffset,
                null, List.of(), null,
                List.of(), mobSpawners,
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of());
    }

    /** Hub with {@code exits} straight SOUTH exits spaced 6 apart in x, plus optional mob spawners. */
    private static Segment hub(int exits, List<BlockVector3> mobSpawners) {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(2 + i * 6, 1, 4, Direction.SOUTH));
        }
        int sizeX = 2 + (exits - 1) * 6 + 3;
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(sizeX, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), mobSpawners,
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of());
    }

    private static Segment corridorNS(List<BlockVector3> mobSpawners) {
        return room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)),
                null, null, mobSpawners);
    }

    /** The full connectable set, with the hub and corridors optionally carrying spawner markers. */
    private List<Segment> fullSet(List<BlockVector3> hubSpawners, List<BlockVector3> corridorSpawners) {
        List<Segment> set = new ArrayList<>();
        set.add(hub(8, hubSpawners));
        set.add(corridorNS(corridorSpawners));
        for (VaultColor color : List.of(VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(room("vault_" + color, SegmentType.VAULT,
                    List.of(ep(2, 1, 0, Direction.NORTH)), color, null, List.of()));
        }
        for (VaultColor color : List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(room("key_" + color, SegmentType.END,
                    List.of(ep(2, 1, 0, Direction.NORTH)), null, color, List.of()));
        }
        return set;
    }

    private DungeonBlueprint generate(List<Segment> set) {
        generator.setAvailableSegmentsForTest(set);
        DungeonBlueprint bp = generator.generateDungeonLayout();
        assertNotNull(bp, "generation should finish with a valid layout");
        return bp;
    }

    @Test
    void consolidatesHubSpawnersAtTheirAbsoluteOffsets() {
        // Only the hub carries markers, and the hub is always placed at the blueprint origin with no
        // rotation, so the expected vectors are exactly its offsets.
        DungeonBlueprint bp = generate(fullSet(
                List.of(BlockVector3.at(1, 1, 2), BlockVector3.at(3, 1, 2)), List.of()));

        assertEquals(Set.of(new Vector(1, 1, 2), new Vector(3, 1, 2)),
                new HashSet<>(bp.getMobSpawnerRelativeLocations()));
    }

    @Test
    void returnsEmptyWhenNoSegmentDefinesSpawners() {
        // The bundled hub declares "mobSpawnerLocations": [] today, so this is the stock case: the
        // dungeon still generates, it just has no designed encounters.
        DungeonBlueprint bp = generate(fullSet(List.of(), List.of()));

        assertTrue(bp.getMobSpawnerRelativeLocations().isEmpty(),
                "no template defines a spawner, so none should be consolidated");
    }

    @Test
    void collectsSpawnersFromEveryPlacedSegment() {
        DungeonBlueprint bp = generate(fullSet(
                List.of(BlockVector3.at(1, 1, 2)), List.of(BlockVector3.at(2, 1, 2))));

        long corridors = bp.getRelativeSegments().stream()
                .filter(ps -> ps.getSegmentTemplate().getType() == SegmentType.CORRIDOR)
                .count();
        assertTrue(corridors > 0, "the layout should contain corridors for this to prove anything");

        // One from the hub, one from each placed corridor.
        assertEquals(1 + corridors, bp.getMobSpawnerRelativeLocations().size(),
                "every placed segment carrying a marker should contribute one spawner");
    }

    @Test
    void appliesTheSegmentRotationToEachOffset() {
        // Recomputing the expectation from the layout's own placements pins the consolidation to
        // addRotated(): adding raw template offsets would put a spawner outside a rotated room.
        DungeonBlueprint bp = generate(fullSet(
                List.of(BlockVector3.at(1, 1, 2)), List.of(BlockVector3.at(0, 1, 3))));

        Set<Vector> expected = new HashSet<>();
        for (PlacedSegment placed : bp.getRelativeSegments()) {
            Vector origin = placed.getWorldOrigin().toVector();
            for (BlockVector3 offset : placed.getSegmentTemplate().getMobSpawnerLocations()) {
                BlockVector3 rotated = placed.getRotatedOffset(offset);
                expected.add(origin.clone().add(new Vector(rotated.x(), rotated.y(), rotated.z())));
            }
        }

        assertFalse(expected.isEmpty(), "the layout should place segments carrying markers");
        assertEquals(expected, new HashSet<>(bp.getMobSpawnerRelativeLocations()));
    }

    @Test
    void everySpawnerLandsInsideTheBlueprintBounds() {
        DungeonBlueprint bp = generate(fullSet(
                List.of(BlockVector3.at(1, 1, 2)), List.of(BlockVector3.at(0, 1, 3))));

        var min = bp.getRelativeBounds().getMinPoint();
        var max = bp.getRelativeBounds().getMaxPoint();
        for (Vector spawner : bp.getMobSpawnerRelativeLocations()) {
            assertTrue(spawner.getX() >= min.getX() && spawner.getX() <= max.getX()
                            && spawner.getY() >= min.getY() && spawner.getY() <= max.getY()
                            && spawner.getZ() >= min.getZ() && spawner.getZ() <= max.getZ(),
                    spawner + " fell outside the blueprint bounds " + min.toVector() + ".." + max.toVector());
        }
    }
}
