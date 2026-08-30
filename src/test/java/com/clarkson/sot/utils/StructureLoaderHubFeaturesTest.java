package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.timer.VisualTimerLayout;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the hub-feature fields added for the builder markers: the coin bank, death cages, sand-timer
 * deposit points, the visual timer column's base, and the 2D safe-exit portal bound.
 * {@link StructureLoader} only uses the plugin for logging, so this runs without a server. The second
 * case is the back-compatibility guarantee: every template written before these keys existed must
 * still load with the new fields empty/null.
 */
class StructureLoaderHubFeaturesTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderHubFeaturesTest"));
        loader = new StructureLoader(plugin);
    }

    private void writeTemplate(String fileName, String extraJson) throws Exception {
        String json = """
                {
                  "name": "test_hub",
                  "type": "HUB",
                  "schematicFileName": "test_hub.schem",
                  "size": {"x": 16, "y": 8, "z": 16},
                  "entryPoints": [],
                  "sandSpawnLocations": [],
                  "itemSpawnLocations": [],
                  "coinSpawnLocations": [],
                  "totalCoins": 0,
                  "gates": [],
                  "sandSacrificeLocations": [],
                  "mobSpawnerLocations": []%s
                }
                """.formatted(extraJson);
        Files.writeString(new File(dataDir.toFile(), fileName).toPath(), json, StandardCharsets.UTF_8);
    }

    @Test
    void readsBankCagesTimersAndSafeExitBound() throws Exception {
        writeTemplate("hub.json", """
                ,
                  "bankLocationOffset": {"x": 8, "y": 1, "z": 8},
                  "deathCageLocations": [{"x": 3, "y": 1, "z": 3}, {"x": 3, "y": 1, "z": 12}],
                  "sandTimerLocations": [{"x": 5, "y": 1, "z": 5}],
                  "timerLocationOffset": {"x": 6, "y": 1, "z": 6},
                  "safeExitBound": {"min": {"x": 1, "y": 1, "z": 0}, "max": {"x": 3, "y": 4, "z": 0}}""");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the template should load");
        Segment seg = segments.get(0);

        assertEquals(BlockVector3.at(8, 1, 8), seg.getBankOffset());
        assertEquals(List.of(BlockVector3.at(3, 1, 3), BlockVector3.at(3, 1, 12)), seg.getDeathCageOffsets());
        assertEquals(List.of(BlockVector3.at(5, 1, 5)), seg.getSandTimerOffsets());
        assertEquals(BlockVector3.at(6, 1, 6), seg.getTimerOffset());

        SegmentBound bound = seg.getSafeExitBound();
        assertNotNull(bound, "safe exit bound should load");
        assertEquals(BlockVector3.at(1, 1, 0), bound.getMin());
        assertEquals(BlockVector3.at(3, 4, 0), bound.getMax());
    }

    @Test
    void loadsTemplatesWrittenBeforeTheHubMarkersExisted() throws Exception {
        writeTemplate("hub.json", "");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "a template with no hub-feature keys must still load");
        Segment seg = segments.get(0);
        assertNull(seg.getBankOffset());
        assertNull(seg.getSafeExitBound());
        assertTrue(seg.getDeathCageOffsets().isEmpty());
        assertTrue(seg.getSandTimerOffsets().isEmpty());
        assertNull(seg.getTimerOffset());
    }

    /**
     * The hub shipped in the jar must define a TIMER marker. Without one the generator has nowhere to
     * put the draining sand column, and a fresh server plays a whole round with no visible timer — the
     * regression that used to leave a pillar of sand standing at the lobby spawn instead.
     */
    @Test
    void theBundledHubDefinesATimerMarker() throws Exception {
        // Read the shipped template off the classpath, the same place installBundledSegments() gets it.
        try (InputStream bundled = getClass().getClassLoader().getResourceAsStream("bundled_segments/hub.json")) {
            assertNotNull(bundled, "bundled_segments/hub.json should be on the classpath");
            Files.copy(bundled, new File(dataDir.toFile(), "hub.json").toPath());
        }

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the bundled hub should load");
        assertNotNull(segments.get(0).getTimerOffset(),
                "the bundled hub must carry a TIMER marker so the sand column stands in the hub");
    }

    /**
     * The hub must declare itself tall enough to contain the sand column it anchors.
     *
     * <p>{@code GameManager.startGame} puts the column at relative Y {@code timerOffset.y + 1} up to
     * {@code timerOffset.y + COLUMN_HEIGHT_BLOCKS}, but the blueprint's bounds come from the declared
     * {@code size} ({@code DungeonGenerator.calculateRelativeMaxBounds}), and those bounds are what
     * {@code DungeonManager.cleanupInstance()} air-fills between rounds. Under-declare the height and
     * the top of every team's column falls outside the wipe, left standing for the next round — which
     * cannot clear it either, because the paste uses {@code ignoreAirBlocks}.
     *
     * <p>The bundled hub's {@code size.y} is deliberately larger than {@code hub.schem}'s own height
     * for this reason; the extra layers are air and cost nothing to paste. It is easy to lose: a
     * re-save in game rewrites {@code size} from the WorldEdit selection
     * ({@code SaveSegmentCommand}), so selecting the schematic's height would silently reopen the
     * gap. Hence this test rather than a comment.
     *
     * <p>The bound is derived from the constants, so raising {@code DEFAULT_MAX_TIMER_SECONDS} fails
     * here too instead of quietly outgrowing the hub.
     */
    @Test
    void theBundledHubDeclaresEnoughHeightToContainItsTimerColumn() throws Exception {
        try (InputStream bundled = getClass().getClassLoader().getResourceAsStream("bundled_segments/hub.json")) {
            assertNotNull(bundled, "bundled_segments/hub.json should be on the classpath");
            Files.copy(bundled, new File(dataDir.toFile(), "hub.json").toPath());
        }

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());
        assertEquals(1, segments.size(), "the bundled hub should load");
        Segment hub = segments.get(0);

        BlockVector3 timerOffset = hub.getTimerOffset();
        assertNotNull(timerOffset, "the bundled hub must carry a TIMER marker");

        int topColumnCell = timerOffset.y() + VisualTimerLayout.COLUMN_HEIGHT_BLOCKS;
        assertTrue(hub.getSize().y() > topColumnCell,
                "the bundled hub declares size.y=" + hub.getSize().y() + ", but its sand column reaches"
                        + " relative Y " + topColumnCell + " (TIMER marker at y=" + timerOffset.y()
                        + " plus " + VisualTimerLayout.COLUMN_HEIGHT_BLOCKS + " blocks). Cleanup"
                        + " air-fills only the declared bounds, so the top of the column would be left"
                        + " standing between rounds. Re-save the hub from a WorldEdit selection at"
                        + " least " + (topColumnCell + 1) + " blocks tall.");
    }
}
