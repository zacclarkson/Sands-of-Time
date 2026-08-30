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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link DungeonGenerator#generateDungeonLayout()} retries {@code attemptGeneration} up to 20 times,
 * and every attempt re-runs the same checks. Conditions that are a property of the segment templates
 * on disk rather than of an individual attempt must therefore be logged <b>once per call</b>, not
 * once per retry — otherwise a single {@code /sot setup} floods the console with 20 identical lines
 * (issue #84; CLAUDE.md has always claimed the SAFE_EXIT case "logs a warning once").
 *
 * <p>Genuinely per-attempt validation failures go the other way: off the console at {@code fine}
 * level, with one {@code severe} summary if every attempt fails.
 */
class DungeonGeneratorWarningLoggingTest {

    private DungeonGenerator generator;
    private RecordingHandler handler;

    /** Captures everything the generator logs, at every level. */
    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("DungeonGeneratorWarningLoggingTest-" + System.nanoTime());
        logger.setUseParentHandlers(false); // keep the surefire output clean
        logger.setLevel(Level.ALL);
        handler = new RecordingHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(logger);
        generator = new DungeonGenerator(plugin);
        generator.setSeed(20260830L);
    }

    private long countAtLeast(Level level, String substring) {
        return handler.records.stream()
                .filter(r -> r.getLevel().intValue() >= level.intValue())
                .filter(r -> r.getMessage() != null && r.getMessage().contains(substring))
                .count();
    }

    // --- Segment builders (mirrors DungeonGeneratorGenerationTest's synthetic, cleanly tiling set) ---

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

    /** Hub with 8 straight SOUTH exits in disjoint x-columns, optionally carrying its own vault marker. */
    private static Segment hub(VaultColor vault) {
        int exits = 8;
        List<RelativeEntryPoint> entries = new ArrayList<>();
        for (int i = 0; i < exits; i++) {
            entries.add(ep(2 + i * 6, 1, 4, Direction.SOUTH));
        }
        int sizeX = 2 + (exits - 1) * 6 + 3;
        BlockVector3 vaultOffset = (vault != null) ? BlockVector3.at(1, 1, 1) : null;
        return new Segment(
                "hub", SegmentType.HUB, "hub.schem", BlockVector3.at(sizeX, 5, 5),
                entries, List.of(), List.of(), List.of(),
                0, vault, null, vaultOffset, null,
                null, List.of(), null,
                List.of(), List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of());
    }

    private static Segment corridorNS() {
        return room("cor_ns", SegmentType.CORRIDOR,
                List.of(ep(2, 1, 0, Direction.NORTH), ep(2, 1, 4, Direction.SOUTH)), null, null);
    }

    private static Segment vault(VaultColor color) {
        return room("vault_" + color, SegmentType.VAULT, List.of(ep(2, 1, 0, Direction.NORTH)), color, null);
    }

    private static Segment key(VaultColor color) {
        return room("key_" + color, SegmentType.END, List.of(ep(2, 1, 0, Direction.NORTH)), null, color);
    }

    /** A set that can never validate: no GOLD vault exists, so all 20 attempts fail. */
    private List<Segment> unsatisfiableSet() {
        List<Segment> set = new ArrayList<>();
        set.add(hub(null));
        set.add(corridorNS());
        set.add(vault(VaultColor.BLUE));
        set.add(vault(VaultColor.RED));
        set.add(vault(VaultColor.GREEN));
        set.add(key(VaultColor.RED));
        set.add(key(VaultColor.GREEN));
        set.add(key(VaultColor.GOLD));
        return set;
    }

    @Test
    void logsTemplateLevelWarningsOncePerCallDespiteEveryRetry() {
        generator.setAvailableSegmentsForTest(unsatisfiableSet());

        assertNull(generator.generateDungeonLayout(), "no GOLD vault template, so generation must fail");
        assertEquals(1, countAtLeast(Level.SEVERE, "after 20 attempts"),
                "the run really did exhaust all 20 retries");

        assertEquals(1, countAtLeast(Level.WARNING, "No SAFE_EXIT marker in any segment template"),
                "the missing-SAFE_EXIT warning describes the templates, not the attempt");
        assertEquals(1, countAtLeast(Level.WARNING, "No BLUE key spawn in any segment template"),
                "the missing-blue-key warning describes the templates, not the attempt");
    }

    @Test
    void keepsPerAttemptValidationFailuresOffTheConsoleAndSummarisesThemOnce() {
        generator.setAvailableSegmentsForTest(unsatisfiableSet());

        assertNull(generator.generateDungeonLayout());

        assertEquals(0, countAtLeast(Level.WARNING, "Missing vault marker"),
                "a per-attempt validation failure must not warn 20 times");
        assertEquals(1, countAtLeast(Level.SEVERE, "Unmet layout requirements"),
                "one summary instead");
        assertEquals(1, countAtLeast(Level.SEVERE, "vault marker for GOLD (missing on 20/20 attempts)"),
                "the summary names what was missing and how often");
    }

    /**
     * A duplicate marker is a property of the loaded templates too: here the hub carries a GOLD vault
     * marker and a GOLD vault room also exists, so consolidation reports a duplicate on every attempt.
     */
    @Test
    void logsADuplicateMarkerWarningOncePerCall() {
        List<Segment> set = unsatisfiableSet();
        set.set(0, hub(VaultColor.GOLD)); // hub marker wins; the DFS-placed room is the duplicate
        set.add(vault(VaultColor.GOLD));
        set.removeIf(s -> s.getContainedVaultKey() == VaultColor.GOLD); // still unsatisfiable -> 20 attempts
        generator.setAvailableSegmentsForTest(set);

        assertNull(generator.generateDungeonLayout());
        assertEquals(1, countAtLeast(Level.SEVERE, "after 20 attempts"), "all 20 retries ran");
        // Precondition: the DFS has to actually place the GOLD room for the duplicate to arise, and
        // on more than one attempt for "once per call" to differ from "once per attempt".
        assertTrue(countAtLeast(Level.INFO, "Placed GOLD vault segment") > 1,
                "the GOLD vault room should be placed on most attempts, making the duplicate recur");
        assertEquals(1, countAtLeast(Level.WARNING, "Duplicate vault marker found for color GOLD"),
                "one line per duplicate, not one per retry");
    }

    /** The suppression is per call, not per generator: a second /sot setup must warn again. */
    @Test
    void warnsAgainOnTheNextGenerationCall() {
        generator.setAvailableSegmentsForTest(unsatisfiableSet());

        generator.generateDungeonLayout();
        generator.generateDungeonLayout();

        assertEquals(2, countAtLeast(Level.WARNING, "No SAFE_EXIT marker in any segment template"),
                "each generateDungeonLayout call gets its own warning");
        assertEquals(2, countAtLeast(Level.SEVERE, "vault marker for GOLD (missing on 20/20 attempts)"),
                "and its own failure summary, not a running total");
    }
}
