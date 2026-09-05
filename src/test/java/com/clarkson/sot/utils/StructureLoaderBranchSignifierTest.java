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
 * Covers the {@code branchSignifierLocations} key of the segment template JSON schema — the
 * placeholder cells the generator paints a branch's vault colour onto.
 *
 * <p>{@link StructureLoader} only uses the plugin for logging, so this runs without a server. The
 * second case is the backward-compatibility guarantee: every template on disk predates the
 * BRANCH_SIGNIFIER marker, and those files must still load (with no markings) rather than fail.
 */
class StructureLoaderBranchSignifierTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderBranchSignifierTest"));
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
    void readsBranchSignifierLocationsFromJson() throws Exception {
        writeTemplate("hub.json", """
                ,
                  "branchSignifierLocations": [
                    {"x": 3, "y": 2, "z": 0},
                    {"x": 9, "y": 2, "z": 0}
                  ]""");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "the template should load");
        assertEquals(List.of(BlockVector3.at(3, 2, 0), BlockVector3.at(9, 2, 0)),
                segments.get(0).getBranchSignifierOffsets());
    }

    @Test
    void loadsTemplatesWrittenBeforeTheBranchSignifierMarkerExisted() throws Exception {
        writeTemplate("hub.json", "");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "a template with no branchSignifierLocations must still load");
        assertTrue(segments.get(0).getBranchSignifierOffsets().isEmpty());
    }
}
