package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the hub-feature fields added for the builder markers: the coin bank, death cages, sand-timer
 * deposit points, and the 2D safe-exit portal bound. {@link StructureLoader} only uses the plugin for
 * logging, so this runs without a server. The second case is the back-compatibility guarantee: every
 * template written before these keys existed must still load with the new fields empty/null.
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
                  "safeExitBound": {"min": {"x": 1, "y": 1, "z": 0}, "max": {"x": 3, "y": 4, "z": 0}}""");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the template should load");
        Segment seg = segments.get(0);

        assertEquals(BlockVector3.at(8, 1, 8), seg.getBankOffset());
        assertEquals(List.of(BlockVector3.at(3, 1, 3), BlockVector3.at(3, 1, 12)), seg.getDeathCageOffsets());
        assertEquals(List.of(BlockVector3.at(5, 1, 5)), seg.getSandTimerOffsets());

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
    }
}
