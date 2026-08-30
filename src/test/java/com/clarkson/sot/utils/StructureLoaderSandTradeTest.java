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
 * Covers the {@code sandTradeLocations} key of the segment template JSON schema.
 *
 * <p>The backward-compatibility case is the one that matters. <em>Every</em> template on disk
 * predates the SAND_TRADE marker — the bundled hub included — so a missing key has to mean "no trade
 * points here", not a malformed template. Get that wrong and a server that has not re-saved its
 * segments loses every one of them.
 *
 * <p>{@link StructureLoader} only uses the plugin for logging, so this runs without a server.
 */
class StructureLoaderSandTradeTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderSandTradeTest"));
        loader = new StructureLoader(plugin);
    }

    /** Writes a minimal but valid segment template; name, schematicFileName and size are required. */
    private void writeTemplate(String fileName, String extraJson) throws Exception {
        String json = """
                {
                  "name": "test_room",
                  "type": "SMALL_ROOM",
                  "schematicFileName": "test_room.schem",
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
    void readsSandTradeLocationsFromJson() throws Exception {
        writeTemplate("room.json", """
                ,
                  "sandTradeLocations": [{"x": 4, "y": 1, "z": 7}, {"x": 9, "y": 1, "z": 2}]""");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the template should load");
        assertEquals(List.of(BlockVector3.at(4, 1, 7), BlockVector3.at(9, 1, 2)),
                segments.get(0).getSandTradeLocations());
    }

    @Test
    void loadsTemplatesWrittenBeforeTheSandTradeMarkerExisted() throws Exception {
        writeTemplate("room.json", "");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "a template with no sandTradeLocations key must still load");
        assertTrue(segments.get(0).getSandTradeLocations().isEmpty());
    }

    @Test
    void anEmptyArrayLoadsAsNoTradePoints() throws Exception {
        writeTemplate("room.json", ",\n  \"sandTradeLocations\": []");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size());
        assertTrue(segments.get(0).getSandTradeLocations().isEmpty());
    }
}
