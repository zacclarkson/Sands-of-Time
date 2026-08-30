package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;

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
 * Template load order must be stable (issue #49).
 *
 * <p>{@code File.listFiles()} has no defined order — it returns whatever the filesystem hands back,
 * which varies by filesystem, by machine and by the order files happened to be written. The dungeon
 * seed indexes into this list (the generator picks templates by random index and takes the first HUB
 * it finds), so an unsorted list would make the same seed produce a different dungeon on a different
 * server — silently, and exactly where it is hardest to notice. Sorting by filename is what makes a
 * shared seed mean the same dungeon everywhere.
 */
class StructureLoaderOrderingTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderOrderingTest"));
        loader = new StructureLoader(plugin);
    }

    private void writeTemplate(String fileName, String segmentName) throws Exception {
        String json = """
                {
                  "name": "%s",
                  "type": "CORRIDOR",
                  "schematicFileName": "%s.schem",
                  "size": {"x": 16, "y": 8, "z": 16},
                  "entryPoints": [],
                  "sandSpawnLocations": [],
                  "itemSpawnLocations": [],
                  "coinSpawnLocations": [],
                  "totalCoins": 0,
                  "gates": [],
                  "sandSacrificeLocations": [],
                  "mobSpawnerLocations": []
                }
                """.formatted(segmentName, segmentName);
        Files.writeString(new File(dataDir.toFile(), fileName).toPath(), json, StandardCharsets.UTF_8);
    }

    @Test
    void loadsTemplatesInFilenameOrderRegardlessOfWriteOrder() throws Exception {
        // Written back to front, so a loader that simply echoed the directory listing would have a
        // fair chance of returning them that way.
        writeTemplate("zulu.json", "zulu");
        writeTemplate("mike.json", "mike");
        writeTemplate("alpha.json", "alpha");
        writeTemplate("bravo.json", "bravo");

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(List.of("alpha", "bravo", "mike", "zulu"),
                segments.stream().map(Segment::getName).toList(),
                "templates must load in filename order so a seed means the same dungeon everywhere");
    }

    @Test
    void orderIsRepeatableAcrossLoads() {
        // The real risk is not one bad ordering but an ordering that drifts between runs.
        assertDoesNotThrow(() -> {
            writeTemplate("delta.json", "delta");
            writeTemplate("charlie.json", "charlie");
            writeTemplate("echo.json", "echo");
        });

        List<String> first = loader.loadSegmentTemplates(dataDir.toFile()).stream()
                .map(Segment::getName).toList();
        List<String> second = loader.loadSegmentTemplates(dataDir.toFile()).stream()
                .map(Segment::getName).toList();

        assertEquals(first, second, "repeated loads must agree");
        assertEquals(List.of("charlie", "delta", "echo"), first);
    }
}
