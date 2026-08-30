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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the branch-colour signifier: {@link DungeonGenerator#resolveBranchSignifiers} and the two
 * pure helpers it leans on. A template says only <em>where</em> a colour marking goes, so the whole
 * point of this code is choosing <em>which</em> colour — the exit the marking stands beside decides,
 * and a marking beside an exit with no vault behind it must produce nothing rather than a lie.
 *
 * <p>Blueprint-stage locations have a null world by design, so none of this needs a server.
 */
class DungeonGeneratorBranchSignifierTest {

    private static final BlockVector3 SIZE = BlockVector3.at(5, 5, 5);

    private static RelativeEntryPoint ep(int x, int y, int z, Direction dir) {
        return new RelativeEntryPoint(BlockVector3.at(x, y, z), dir);
    }

    /** Bare template carrying only the entry points and colour-marking placeholders. */
    private static Segment template(String name, SegmentType type,
                                    List<RelativeEntryPoint> entries,
                                    List<BlockVector3> signifiers) {
        return template(name, type, entries, signifiers, null);
    }

    private static Segment template(String name, SegmentType type,
                                    List<RelativeEntryPoint> entries,
                                    List<BlockVector3> signifiers,
                                    VaultColor vault) {
        BlockVector3 vaultOffset = (vault != null) ? BlockVector3.at(2, 1, 2) : null;
        return new Segment(
                name, type, name + ".schem", SIZE,
                entries, List.of(), List.of(), List.of(),
                0, vault, null, vaultOffset, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), signifiers);
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0);
    }

    private static PlacedSegment placed(Segment segment, int x, int y, int z, int rotationSteps) {
        return new PlacedSegment(segment, new Location(null, x, y, z), 0, rotationSteps);
    }

    private static BlockVector3 cell(int x, int y, int z) {
        return BlockVector3.at(x, y, z);
    }

    // --- nearestEntryIndex ---

    @Test
    void nearestEntryIndexPicksTheClosestEntryPoint() {
        List<RelativeEntryPoint> entries = List.of(
                ep(0, 1, 2, Direction.WEST),
                ep(4, 1, 2, Direction.EAST));

        assertEquals(0, DungeonGenerator.nearestEntryIndex(cell(1, 1, 2), entries));
        assertEquals(1, DungeonGenerator.nearestEntryIndex(cell(3, 1, 2), entries));
    }

    @Test
    void nearestEntryIndexBreaksTiesOnTheLowestIndex() {
        List<RelativeEntryPoint> entries = List.of(
                ep(0, 1, 2, Direction.WEST),
                ep(4, 1, 2, Direction.EAST));

        assertEquals(0, DungeonGenerator.nearestEntryIndex(cell(2, 1, 2), entries),
                "an equidistant placeholder must resolve deterministically");
    }

    // --- nearestVaultColour ---

    @Test
    void nearestVaultColourPrefersTheShallowestVault() {
        Set<VaultColor> colours = EnumSet.of(VaultColor.GOLD, VaultColor.GREEN);
        Map<VaultColor, Integer> depths = Map.of(VaultColor.GOLD, 9, VaultColor.GREEN, 3);

        assertEquals(VaultColor.GREEN, DungeonGenerator.nearestVaultColour(colours, depths),
                "the marking should name the vault a player meets first");
    }

    @Test
    void nearestVaultColourIsNullWhenTheBranchHoldsNoVault() {
        assertNull(DungeonGenerator.nearestVaultColour(null, Map.of()));
        assertNull(DungeonGenerator.nearestVaultColour(Set.of(), Map.of()));
    }

    // --- resolveBranchSignifiers ---

    @Test
    void eachPlaceholderTakesTheColourOfTheBranchBehindItsNearestExit() {
        // Hub with a WEST and an EAST exit, and a marking beside each.
        Segment hub = template("hub", SegmentType.HUB,
                List.of(ep(0, 1, 2, Direction.WEST), ep(4, 1, 2, Direction.EAST)),
                List.of(cell(1, 1, 2), cell(3, 1, 2)));

        Map<BlockVector3, Set<VaultColor>> branches = Map.of(
                cell(0, 1, 2), EnumSet.of(VaultColor.RED),
                cell(4, 1, 2), EnumSet.of(VaultColor.BLUE));

        List<BranchSignifier> signifiers = DungeonGenerator.resolveBranchSignifiers(
                List.of(placed(hub, 0, 0, 0)), branches, Map.of(VaultColor.RED, 5, VaultColor.BLUE, 4));

        assertEquals(List.of(
                        new BranchSignifier(new Vector(1, 1, 2), VaultColor.RED),
                        new BranchSignifier(new Vector(3, 1, 2), VaultColor.BLUE)),
                signifiers);
    }

    @Test
    void placeholdersAreOffsetFromTheirSegmentOrigin() {
        Segment room = template("room", SegmentType.SMALL_ROOM,
                List.of(ep(2, 1, 0, Direction.NORTH)), List.of(cell(2, 1, 1)));

        List<BranchSignifier> signifiers = DungeonGenerator.resolveBranchSignifiers(
                List.of(placed(room, 32, 64, 16)),
                Map.of(cell(34, 65, 16), EnumSet.of(VaultColor.GOLD)),
                Map.of(VaultColor.GOLD, 9));

        assertEquals(List.of(new BranchSignifier(new Vector(34, 65, 17), VaultColor.GOLD)), signifiers);
    }

    @Test
    void placeholdersAndTheirExitsFollowThePlacementRotation() {
        // One 90 deg step maps (x,z) -> (z, 4-x) on a 5x5x5 footprint: the entry at (2,1,4) lands on
        // (4,1,2) and the marking beside it at (1,1,4) lands on (4,1,3). Resolving against the
        // unrotated cells would look up a doorway that does not exist and drop the marking.
        Segment room = template("room", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 4, Direction.SOUTH)), List.of(cell(1, 1, 4)));

        List<BranchSignifier> signifiers = DungeonGenerator.resolveBranchSignifiers(
                List.of(placed(room, 10, 0, 20, 1)),
                Map.of(cell(14, 1, 22), EnumSet.of(VaultColor.GREEN)),
                Map.of(VaultColor.GREEN, 2));

        assertEquals(List.of(new BranchSignifier(new Vector(14, 1, 23), VaultColor.GREEN)), signifiers);
    }

    @Test
    void aPlaceholderBesideAnExitThatLeadsNowhereIsDropped() {
        // The hub's non-vault exits are the common case: the DFS attached no neighbour, so there is
        // no branch and nothing truthful to paint.
        Segment hub = template("hub", SegmentType.HUB,
                List.of(ep(0, 1, 2, Direction.WEST)), List.of(cell(1, 1, 2)));

        assertTrue(DungeonGenerator.resolveBranchSignifiers(
                List.of(placed(hub, 0, 0, 0)), Map.of(), Map.of()).isEmpty());
    }

    @Test
    void aPlaceholderWhoseBranchHoldsNoVaultIsDropped() {
        Segment hub = template("hub", SegmentType.HUB,
                List.of(ep(0, 1, 2, Direction.WEST)), List.of(cell(1, 1, 2)));

        assertTrue(DungeonGenerator.resolveBranchSignifiers(
                        List.of(placed(hub, 0, 0, 0)),
                        Map.of(cell(0, 1, 2), EnumSet.noneOf(VaultColor.class)),
                        Map.of()).isEmpty(),
                "a puzzle-room branch has no vault colour to advertise");
    }

    @Test
    void aSegmentWithNoEntryPointsProducesNothing() {
        Segment orphan = template("orphan", SegmentType.SMALL_ROOM, List.of(), List.of(cell(1, 1, 2)));

        assertTrue(DungeonGenerator.resolveBranchSignifiers(
                List.of(placed(orphan, 0, 0, 0)),
                Map.of(cell(1, 1, 2), EnumSet.of(VaultColor.RED)),
                Map.of(VaultColor.RED, 5)).isEmpty());
    }

    // --- End to end through the generator ---

    /**
     * The DFS is what knows which vault lies down which branch, so the wiring only works if the
     * colours it collects on the way back out reach {@code resolveBranchSignifiers}. The synthetic
     * set below gives the hub eight straight SOUTH exits in disjoint x-columns, each leading a chain
     * of corridors that terminates in a single-entry vault or key room — so every branch holds at
     * most one vault, and the four markings beside the four vault exits must name the four colours.
     */
    @Test
    void generationColoursTheHubMarkingsFromTheBranchesBehindThem() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorBranchSignifierTest"));
        DungeonGenerator generator = new DungeonGenerator(plugin);
        generator.setSeed(20260829L);
        generator.setAvailableSegmentsForTest(branchingSet());

        DungeonBlueprint bp = generator.generateDungeonLayout();

        assertNotNull(bp, "generation should finish with a valid layout");
        List<BranchSignifier> signifiers = bp.getBranchSignifiers();

        Set<VaultColor> colours = signifiers.stream()
                .map(BranchSignifier::getColor).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(VaultColor.class), colours,
                "each vault branch should be advertised exactly once, got: " + signifiers);

        Set<Vector> placeholders = new HashSet<>();
        for (int i = 0; i < HUB_EXITS; i++) placeholders.add(new Vector(1 + i * 6, 1, 4));
        for (BranchSignifier signifier : signifiers) {
            assertTrue(placeholders.contains(signifier.getRelativePosition()),
                    "markings are written at the placeholder cells, not invented: " + signifier);
        }
        assertEquals(colours.size(), signifiers.size(),
                "a branch without a vault should stay unmarked, got: " + signifiers);
    }

    private static final int HUB_EXITS = 8;

    /**
     * The hub from {@code DungeonGeneratorGenerationTest}, plus one colour-marking placeholder one
     * block beside each exit (five blocks from its nearest neighbour, so the pairing is unambiguous).
     */
    private static List<Segment> branchingSet() {
        List<RelativeEntryPoint> entries = new ArrayList<>();
        List<BlockVector3> signifiers = new ArrayList<>();
        for (int i = 0; i < HUB_EXITS; i++) {
            entries.add(ep(2 + i * 6, 1, 4, Direction.SOUTH));
            signifiers.add(cell(1 + i * 6, 1, 4));
        }
        Segment hub = new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(2 + (HUB_EXITS - 1) * 6 + 3, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), signifiers);

        List<Segment> set = new ArrayList<>();
        set.add(hub);
        set.add(template("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)), List.of()));
        for (VaultColor colour : VaultColor.values()) {
            set.add(template("vault_" + colour, SegmentType.VAULT,
                    List.of(ep(2, 1, 0, Direction.NORTH)), List.of(), colour));
        }
        for (VaultColor colour : List.of(VaultColor.RED, VaultColor.GREEN, VaultColor.GOLD)) {
            set.add(keyRoom(colour));
        }
        return set;
    }

    /** Single-entry dead end carrying a key spawn, so a branch that finds one still terminates. */
    private static Segment keyRoom(VaultColor colour) {
        return new Segment(
                "key_" + colour, SegmentType.END, "key_" + colour + ".schem", SIZE,
                List.of(ep(2, 1, 0, Direction.NORTH)), List.of(), List.of(), List.of(),
                0, null, colour, null, BlockVector3.at(2, 1, 2),
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of());
    }
}
