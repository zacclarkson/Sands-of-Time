package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end (blueprint-stage) coverage of {@link DungeonGenerator#generateDungeonLayout()}: with a
 * connectable segment set, generation must finish and place all four vaults + the RED/GREEN/GOLD keys.
 * The blueprint stage is pure logic (no schematics, no world), so this runs without a server.
 *
 * <p>The synthetic set tiles cleanly: the hub has several straight SOUTH exits in disjoint x-columns,
 * each leading a chain of NORTH/SOUTH corridors that can terminate in any NORTH-entry vault/key room —
 * so branches never collide and every vault/key is reachable by depth.
 */
class DungeonGeneratorGenerationTest {

    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorGenerationTest"));
        generator = new DungeonGenerator(plugin);
        generator.setRandomSeedForTest(20260829L);
    }

    // --- Segment builders (5x5x5 rooms; entries centred on their face, matching the real convention) ---

    private static RelativeEntryPoint ep(int x, int y, int z, Direction dir) {
        return new RelativeEntryPoint(BlockVector3.at(x, y, z), dir);
    }

    /** A generic 5x5x5 room with the given entries, and optional vault/key metadata. */
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
                null, List.of(), null, List.of(), null, List.of());
    }

    /** Hub with `exits` straight SOUTH exits (z=4) spaced 6 apart in x, so branches stay in disjoint columns. */
    private static Segment hub(int exits) {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(2 + i * 6, 1, 4, Direction.SOUTH));
        }
        int sizeX = 2 + (exits - 1) * 6 + 3; // cover the furthest exit
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(sizeX, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of());
    }

    /** Hub whose exits face only WEST (x=0), spaced 6 apart in z. Connecting to NORTH-entry rooms is
     *  only possible if the generator rotates them, so this exercises rotation specifically. */
    private static Segment hubWestOnly(int exits) {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(0, 1, 2 + i * 6, Direction.WEST));
        }
        int sizeZ = 2 + (exits - 1) * 6 + 3;
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(5, 5, sizeZ),
                entries, List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of());
    }

    private static Segment corridorNS() {
        return room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)), null, null);
    }

    private static Segment vault(VaultColor color) {
        return room("vault_" + color, SegmentType.VAULT,
                List.of(ep(2, 1, 0, Direction.NORTH)), color, null);
    }

    private static Segment key(VaultColor color) {
        return room("key_" + color, SegmentType.END,
                List.of(ep(2, 1, 0, Direction.NORTH)), null, color);
    }

    private List<Segment> fullSet() {
        List<Segment> set = new ArrayList<>();
        set.add(hub(8));
        set.add(corridorNS());
        set.add(vault(VaultColor.BLUE));
        set.add(vault(VaultColor.RED));
        set.add(vault(VaultColor.GREEN));
        set.add(vault(VaultColor.GOLD));
        set.add(key(VaultColor.RED));
        set.add(key(VaultColor.GREEN));
        set.add(key(VaultColor.GOLD));
        return set;
    }

    @Test
    void generatesACompleteLayoutWithAllVaultsAndKeys() {
        generator.setAvailableSegmentsForTest(fullSet());

        DungeonBlueprint bp = generator.generateDungeonLayout();

        assertNotNull(bp, "generation should finish with a valid layout");
        assertTrue(bp.getVaultMarkerRelativeLocations().keySet()
                        .containsAll(List.of(VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)),
                "all four vaults placed, got: " + bp.getVaultMarkerRelativeLocations().keySet());
        assertTrue(bp.getKeySpawnRelativeLocations().keySet()
                        .containsAll(List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)),
                "RED/GREEN/GOLD keys placed, got: " + bp.getKeySpawnRelativeLocations().keySet());
    }

    @Test
    void generatesViaRotationWhenTheHubFacesOnlyOneWay() {
        // All rooms are defined with NORTH entries; the hub only offers WEST exits. This can only
        // connect if the generator rotates segments to present the needed direction.
        List<Segment> set = fullSet();
        set.removeIf(s -> s.getType() == SegmentType.HUB);
        set.add(0, hubWestOnly(8));
        generator.setAvailableSegmentsForTest(set);

        DungeonBlueprint bp = generator.generateDungeonLayout();

        assertNotNull(bp, "generation should finish via rotation");
        assertTrue(bp.getVaultMarkerRelativeLocations().keySet()
                        .containsAll(List.of(VaultColor.BLUE, VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)),
                "all four vaults placed via rotation, got: " + bp.getVaultMarkerRelativeLocations().keySet());
        assertTrue(bp.getKeySpawnRelativeLocations().keySet()
                        .containsAll(List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)),
                "keys placed via rotation, got: " + bp.getKeySpawnRelativeLocations().keySet());
    }

    @Test
    void failsWhenAVaultCannotBePlaced() {
        List<Segment> set = fullSet();
        set.removeIf(s -> s.getContainedVault() == VaultColor.GOLD); // no gold vault anywhere
        generator.setAvailableSegmentsForTest(set);

        assertNull(generator.generateDungeonLayout(),
                "generation must fail validation when a required vault is missing");
    }
}
