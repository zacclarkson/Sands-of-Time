package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;
import com.clarkson.sot.entities.Area;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The blueprint's {@link Area} must cover the ROTATED footprint of every segment it contains.
 *
 * <p>{@code calculateRelativeMaxBounds} used to read the unrotated {@code getSize()} of the template,
 * so a non-square segment placed at 90° or 270° had its X/Z maximum computed from the wrong axis and
 * the bounds under-covered it. Those bounds are exactly the region
 * {@code DungeonManager.cleanupInstance()} air-fills between rounds, so the overhang was left standing
 * and the next round's paste — which uses {@code ignoreAirBlocks} — could not clear it either.
 *
 * <p>Blueprint-stage only: no schematics, no world, no server.
 */
class DungeonGeneratorRotatedBoundsTest {

    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorRotatedBoundsTest"));
        generator = new DungeonGenerator(plugin);
        generator.setSeed(20260830L);
    }

    private static RelativeEntryPoint ep(int x, int y, int z, Direction dir) {
        return new RelativeEntryPoint(BlockVector3.at(x, y, z), dir);
    }

    /**
     * A deliberately non-square room: 5 wide, 9 deep. Its only entry faces NORTH, so a WEST-facing
     * hub can only attach it by rotating it — which swaps that 5x9 footprint to 9x5.
     */
    private static Segment room(String name, SegmentType type, List<RelativeEntryPoint> entries,
                                VaultColor vault, VaultColor key) {
        BlockVector3 vaultOffset = (vault != null) ? BlockVector3.at(2, 1, 2) : null;
        BlockVector3 keyOffset = (key != null) ? BlockVector3.at(2, 1, 2) : null;
        return new Segment(
                name, type, name + ".schem", BlockVector3.at(5, 5, 9),
                entries, List.of(), List.of(), List.of(),
                0, vault, key, vaultOffset, keyOffset,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of());
    }

    /**
     * Hub whose exits face only EAST, so every NORTH-entry room has to be rotated a quarter turn to
     * attach — and the layout then grows along +X, which is the axis a quarter turn moves the
     * footprint's depth onto. That is what makes the blueprint's X maximum come from a rotated
     * non-square segment rather than from the (never rotated) hub.
     */
    private static Segment hubEastOnly(int exits) {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(4, 1, 2 + i * 12, Direction.EAST));
        }
        int sizeZ = 2 + (exits - 1) * 12 + 3;
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(5, 5, sizeZ),
                entries, List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of());
    }

    private List<Segment> nonSquareSet() {
        List<Segment> set = new ArrayList<>();
        set.add(hubEastOnly(8));
        set.add(room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 8, Direction.SOUTH)), null, null));
        for (VaultColor color : List.of(VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(room("vault_" + color, SegmentType.VAULT,
                    List.of(ep(2, 1, 0, Direction.NORTH)), color, null));
        }
        for (VaultColor color : List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(room("key_" + color, SegmentType.END,
                    List.of(ep(2, 1, 0, Direction.NORTH)), null, color));
        }
        return set;
    }

    @Test
    void blueprintBoundsCoverTheRotatedFootprintOfEverySegment() {
        generator.setAvailableSegmentsForTest(nonSquareSet());

        DungeonBlueprint bp = generator.generateDungeonLayout();
        assertNotNull(bp, "generation should finish via rotation");

        // Guard against a vacuous pass: the layout must actually contain a quarter-turned non-square
        // segment, which is the only case the unrotated size got wrong.
        assertTrue(bp.getRelativeSegments().stream().anyMatch(s ->
                        s.getRotationSteps() % 2 == 1
                                && s.getSegmentTemplate().getSize().x() != s.getSegmentTemplate().getSize().z()),
                "the layout should contain a non-square segment rotated 90 or 270 degrees");

        Location boundsMax = bp.getRelativeBounds().getMaxPoint();
        for (PlacedSegment segment : bp.getRelativeSegments()) {
            Location origin = segment.getWorldOrigin();
            BlockVector3 size = segment.getRotatedSize();
            String where = segment.getName() + " (rotation " + segment.getRotationSteps()
                    + ", footprint " + size + ") at " + origin.toVector();

            assertTrue(origin.getX() + size.x() - 1 <= boundsMax.getX(),
                    "blueprint bounds must cover the X extent of " + where + "; bounds max " + boundsMax.toVector());
            assertTrue(origin.getY() + size.y() - 1 <= boundsMax.getY(),
                    "blueprint bounds must cover the Y extent of " + where + "; bounds max " + boundsMax.toVector());
            assertTrue(origin.getZ() + size.z() - 1 <= boundsMax.getZ(),
                    "blueprint bounds must cover the Z extent of " + where + "; bounds max " + boundsMax.toVector());
        }
    }
}
