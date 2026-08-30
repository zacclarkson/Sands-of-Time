package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.Segment.RelativeEntryPoint;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Seeded generation (issue #49): the same seed must always produce the same dungeon.
 *
 * <p>The blueprint stage is pure logic — no schematics, no world — so a whole layout can be
 * generated per test and compared structurally. The fingerprint below covers everything the rest of
 * the plugin reads off a blueprint: which template was placed where at what rotation, where the
 * vaults and keys ended up, and which connections became doorways.
 */
class DungeonGeneratorSeedTest {

    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        generator = newGenerator();
    }

    private static DungeonGenerator newGenerator() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DungeonGeneratorSeedTest"));
        DungeonGenerator generator = new DungeonGenerator(plugin);
        generator.setAvailableSegmentsForTest(fullSet());
        return generator;
    }

    /**
     * Everything a consumer of a blueprint can observe about its layout, flattened to a comparable
     * string. Two blueprints with equal fingerprints are the same dungeon.
     */
    private static String fingerprint(DungeonBlueprint bp) {
        String segments = bp.getRelativeSegments().stream()
                .map(DungeonGeneratorSeedTest::describe)
                .collect(Collectors.joining("\n"));
        String doorways = bp.getDoorways().stream().map(Object::toString).collect(Collectors.joining("\n"));
        String unused = bp.getUnusedOpenings().stream().map(Object::toString).collect(Collectors.joining("\n"));
        return String.join("\n== ",
                segments,
                "vaults " + new java.util.TreeMap<>(bp.getVaultMarkerRelativeLocations()),
                "keys " + new java.util.TreeMap<>(bp.getKeySpawnRelativeLocations()),
                "sand " + bp.getSandSpawnRelativeLocations(),
                "coins " + bp.getCoinSpawnRelativeLocations(),
                "items " + bp.getItemSpawnRelativeLocations(),
                "doorways " + doorways,
                "unused " + unused);
    }

    private static String describe(PlacedSegment placed) {
        return placed.getName() + "@" + placed.getWorldOrigin().toVector()
                + " rot" + placed.getRotationSteps() + " depth" + placed.getDepth();
    }

    // --- Tests ---

    @Test
    void theSameSeedGeneratesTheSameDungeonTwice() {
        generator.setSeed(4815162342L);
        DungeonBlueprint first = generator.generateDungeonLayout();

        DungeonGenerator other = newGenerator();
        other.setSeed(4815162342L);
        DungeonBlueprint second = other.generateDungeonLayout();

        assertNotNull(first, "generation should finish with a valid layout");
        assertNotNull(second, "generation should finish with a valid layout");
        assertEquals(fingerprint(first), fingerprint(second),
                "the same seed must lay out the same dungeon, down to rotations and doorways");
    }

    @Test
    void aSecondCallOnTheSameGeneratorAlsoReproducesTheLayout() {
        // Reseeding happens per generateDungeonLayout() call, so a generator that has already run
        // must not carry RNG state into the next round.
        generator.setSeed(99L);
        DungeonBlueprint first = generator.generateDungeonLayout();
        DungeonBlueprint second = generator.generateDungeonLayout();

        assertNotNull(first);
        assertEquals(fingerprint(first), fingerprint(second),
                "consecutive rounds on one generator must not drift with the RNG's leftover state");
    }

    @Test
    void differentSeedsGenerateDifferentDungeons() {
        generator.setSeed(1L);
        DungeonBlueprint first = generator.generateDungeonLayout();

        DungeonGenerator other = newGenerator();
        other.setSeed(2L);
        DungeonBlueprint second = other.generateDungeonLayout();

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(fingerprint(first), fingerprint(second),
                "a different seed should produce a different dungeon");
    }

    /**
     * The point of logging the seed: an operator who generated a good dungeon with no seed
     * configured can read the number back and replay that exact layout.
     */
    @Test
    void anUnseededLayoutCanBeReplayedFromTheSeedItReports() {
        assertNull(generator.getSeed(), "no seed is configured by default");
        assertNull(generator.getLastUsedSeed(), "nothing has been generated yet");

        DungeonBlueprint original = generator.generateDungeonLayout();
        Long reported = generator.getLastUsedSeed();

        assertNotNull(original);
        assertNotNull(reported, "an unseeded generation must still report the seed it rolled");

        DungeonGenerator replay = newGenerator();
        replay.setSeed(reported);
        DungeonBlueprint replayed = replay.generateDungeonLayout();

        assertNotNull(replayed);
        assertEquals(fingerprint(original), fingerprint(replayed),
                "feeding getLastUsedSeed() back to setSeed() must reproduce the layout exactly");
    }

    @Test
    void aConfiguredSeedIsReportedAsTheSeedUsed() {
        generator.setSeed(7L);
        generator.generateDungeonLayout();

        assertEquals(7L, generator.getSeed(), "the configured seed is readable back");
        assertEquals(7L, generator.getLastUsedSeed(), "a configured seed is the one actually used");
    }

    @Test
    void clearingTheSeedGoesBackToRollingOnePerRound() {
        generator.setSeed(7L);
        generator.generateDungeonLayout();

        generator.setSeed(null);
        generator.generateDungeonLayout();

        assertNull(generator.getSeed(), "the seed is cleared");
        assertNotEquals(Long.valueOf(7L), generator.getLastUsedSeed(),
                "a cleared seed must roll a fresh one rather than reusing the old value");
    }

    /**
     * Reseeding is per {@code generateDungeonLayout()} call, deliberately not per retry attempt: the
     * attempts share one RNG stream so that a layout failing validation can succeed on the next try.
     * Reseeding inside the loop would make all 20 attempts byte-identical and turn one validation
     * failure into twenty copies of itself.
     *
     * <p>Which seeds need a retry is not something to hand-pick, so this sweeps a range of them with
     * a hub narrow enough to make retries common, asserts every seed reproduces, and asserts that
     * the sweep actually covered a retry — otherwise a change that made retries impossible would
     * leave this test silently proving nothing.
     */
    @Test
    void layoutsThatNeedRetriesAreStillReproducible() {
        // A dead-end filler room is what makes success marginal: without one, every branch is a
        // corridor chain that is eventually forced to place an outstanding vault or key, so a hub
        // with enough exits always succeeds on the first attempt and never retries at all. With one
        // in the pool a branch can simply stop, leaving a feature unplaced and the attempt invalid.
        List<Segment> retryProne = fullSet();
        retryProne.removeIf(s -> s.getType() == SegmentType.HUB);
        retryProne.add(0, hub(14));
        retryProne.add(room("dead_end", SegmentType.END,
                List.of(ep(2, 1, 0, Direction.NORTH)), null, null));

        int reproduced = 0;
        int seedsNeedingARetry = 0;
        for (long seed = 1L; seed <= 25L; seed++) {
            AttemptLog log = new AttemptLog();
            DungeonBlueprint first = generateWith(retryProne, seed, log);
            DungeonBlueprint second = generateWith(retryProne, seed, null);

            if (first == null) {
                assertNull(second, "seed " + seed + ": a seed that fails validation must fail identically");
            } else {
                assertNotNull(second, "seed " + seed + ": generation should be reproducible");
                assertEquals(fingerprint(first), fingerprint(second),
                        "seed " + seed + ": the layout must be a function of the seed alone");
                reproduced++;
            }
            if (log.attemptsUsed > 1) {
                seedsNeedingARetry++;
            }
        }

        assertTrue(reproduced > 0, "the sweep should have produced at least one valid layout");
        assertTrue(seedsNeedingARetry > 0,
                "the sweep should have covered at least one layout that needed a retry, otherwise it "
                        + "does not exercise the shared-RNG-across-attempts behaviour at all");
    }

    /** Generates one layout on a fresh generator, optionally recording how many attempts it took. */
    @Nullable
    private static DungeonBlueprint generateWith(List<Segment> segments, long seed, @Nullable AttemptLog log) {
        Logger logger = Logger.getLogger("DungeonGeneratorSeedTest.attempts." + seed + "." + (log != null));
        logger.setUseParentHandlers(false);
        if (log != null) {
            logger.addHandler(log);
        }
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(logger);
        DungeonGenerator generator = new DungeonGenerator(plugin);
        generator.setAvailableSegmentsForTest(segments);
        generator.setSeed(seed);
        try {
            return generator.generateDungeonLayout();
        } finally {
            if (log != null) {
                logger.removeHandler(log);
            }
        }
    }

    /** Reads the attempt number out of the generator's own success line. */
    private static final class AttemptLog extends Handler {
        private static final Pattern SUCCESS =
                Pattern.compile("generated successfully on attempt (\\d+)/");
        private int attemptsUsed;

        @Override
        public void publish(LogRecord record) {
            Matcher matcher = SUCCESS.matcher(String.valueOf(record.getMessage()));
            if (matcher.find()) {
                attemptsUsed = Integer.parseInt(matcher.group(1));
            }
        }

        @Override public void flush() { }
        @Override public void close() { }
    }

    // --- Segment builders (mirrors DungeonGeneratorGenerationTest's tiling set) ---

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
                null, List.of(), null, List.of(), null, List.of(), List.of());
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
                0, null, null, null, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of());
    }

    private static List<Segment> fullSet() {
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
}
