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
 * Covers loading the per-chest sand price of a gate sacrifice ({@code sandSacrificeCosts}).
 *
 * <p>The list is index-aligned with {@code sandSacrificeLocations} and every template saved before it
 * existed carries none, so the shape that matters is the degraded one: missing, short, malformed or
 * out of range must all load with the default price rather than fail the template.
 */
class StructureLoaderSacrificeCostTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderSacrificeCostTest"));
        loader = new StructureLoader(plugin);
    }

    /** Two sacrifice markers plus whatever {@code extraJson} adds (leading comma included). */
    private void writeTemplate(String extraJson) throws Exception {
        String json = """
                {
                  "name": "paywall",
                  "type": "SMALL_ROOM",
                  "schematicFileName": "paywall.schem",
                  "size": {"x": 16, "y": 8, "z": 16},
                  "entryPoints": [],
                  "sandSpawnLocations": [],
                  "itemSpawnLocations": [],
                  "coinSpawnLocations": [],
                  "totalCoins": 0,
                  "gates": [{"min": {"x": 2, "y": 1, "z": 5}, "max": {"x": 4, "y": 4, "z": 5}}],
                  "sandSacrificeLocations": [{"x": 3, "y": 0, "z": 3}, {"x": 6, "y": 0, "z": 3}],
                  "mobSpawnerLocations": []%s
                }
                """.formatted(extraJson);
        Files.writeString(new File(dataDir.toFile(), "paywall.json").toPath(), json, StandardCharsets.UTF_8);
    }

    private Segment load() {
        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());
        assertEquals(1, segments.size(), "the template loads");
        return segments.get(0);
    }

    @Test
    void readsOneCostPerSacrificeMarker() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [2, 5]");

        Segment segment = load();

        assertEquals(List.of(2, 5), segment.getSandSacrificeCosts());
        assertEquals(2, segment.getSandSacrificeCost(0));
        assertEquals(5, segment.getSandSacrificeCost(1));
        assertEquals(List.of(BlockVector3.at(3, 0, 3), BlockVector3.at(6, 0, 3)), segment.getSandSacrificeLocations());
    }

    @Test
    void aTemplateSavedBeforePricesExistedCostsTheDefaultPerChest() throws Exception {
        writeTemplate("");

        Segment segment = load();

        assertEquals(List.of(Segment.DEFAULT_SACRIFICE_COST, Segment.DEFAULT_SACRIFICE_COST),
                segment.getSandSacrificeCosts(), "one default price per marker, never an empty list");
    }

    @Test
    void aShortCostListIsPaddedWithTheDefault() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [4]");

        assertEquals(List.of(4, Segment.DEFAULT_SACRIFICE_COST), load().getSandSacrificeCosts());
    }

    @Test
    void aLongCostListIsTruncatedToTheMarkers() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [1, 2, 3, 4]");

        assertEquals(List.of(1, 2), load().getSandSacrificeCosts());
    }

    @Test
    void outOfRangeCostsAreClamped() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [0, 99]");

        assertEquals(List.of(1, Segment.MAX_SACRIFICE_COST), load().getSandSacrificeCosts());
    }

    @Test
    void aNonNumericCostFallsBackToTheDefaultWithoutFailingTheTemplate() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [\"three\", 2]");

        assertEquals(List.of(Segment.DEFAULT_SACRIFICE_COST, 2), load().getSandSacrificeCosts());
    }

    @Test
    void anOutOfRangeIndexReadsAsTheDefault() throws Exception {
        writeTemplate(", \"sandSacrificeCosts\": [2, 5]");

        assertEquals(Segment.DEFAULT_SACRIFICE_COST, load().getSandSacrificeCost(7));
        assertEquals(Segment.DEFAULT_SACRIFICE_COST, load().getSandSacrificeCost(-1));
    }

    @Test
    void aStaleSandTradeLocationsKeyIsIgnored() throws Exception {
        // The direct-coin trade chest was removed; a template saved while it existed still loads and
        // the key is simply never read.
        writeTemplate(", \"sandTradeLocations\": [{\"x\": 9, \"y\": 0, \"z\": 9}]");

        Segment segment = load();

        assertEquals(2, segment.getSandSacrificeLocations().size(), "the trade cell does not leak into anything");
    }
}
