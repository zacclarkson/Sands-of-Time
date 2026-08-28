package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;

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
 * Covers the {@code safeExitOffset} half of the segment template JSON schema.
 *
 * <p>{@link StructureLoader} only uses the plugin for logging, so this runs without a server. The
 * write side has no matching test: {@code StructureSaver.serializeSegmentTemplate} is private and
 * its only caller also writes the schematic through WorldEdit.
 *
 * <p>The second case is the backward-compatibility guarantee. Every segment template saved before
 * the SAFE_EXIT marker existed lacks the key, and those files must still load.
 */
class StructureLoaderSafeExitTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderSafeExitTest"));
        loader = new StructureLoader(plugin);
    }

    /** Writes a minimal but valid segment template; name, schematicFileName and size are required. */
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
    void readsSafeExitOffsetFromJson() throws Exception {
        writeTemplate("hub.json", ",\n  \"safeExitOffset\": {\"x\": 4, \"y\": 1, \"z\": 7}");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the template should load");
        assertEquals(BlockVector3.at(4, 1, 7), segments.get(0).getSafeExitOffset());
    }

    @Test
    void loadsTemplatesWrittenBeforeTheSafeExitMarkerExisted() throws Exception {
        writeTemplate("hub.json", "");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "a template with no safeExitOffset key must still load");
        assertNull(segments.get(0).getSafeExitOffset());
    }

    @Test
    void ignoresAMalformedSafeExitOffsetWithoutDroppingTheTemplate() throws Exception {
        writeTemplate("hub.json", ",\n  \"safeExitOffset\": {\"x\": 4, \"y\": 1}");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "an incomplete offset must not discard the whole template");
        assertNull(segments.get(0).getSafeExitOffset());
    }
}
