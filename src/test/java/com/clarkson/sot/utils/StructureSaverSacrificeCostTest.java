package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentBound;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * Round-trips a gate sacrifice price through the JSON half of {@link StructureSaver} and back through
 * {@link StructureLoader}. The schematic half needs a live WorldEdit and is covered in-game.
 */
class StructureSaverSacrificeCostTest {

    @TempDir
    Path dataDir;

    private StructureSaver saver;
    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureSaverSacrificeCostTest"));
        saver = new StructureSaver(plugin);
        loader = new StructureLoader(plugin);
    }

    private static Segment paywall(List<BlockVector3> sacrifices, List<Integer> costs) {
        return new Segment(
                "paywall", SegmentType.SMALL_ROOM, "paywall.schem", BlockVector3.at(16, 8, 16),
                List.of(), List.of(), List.of(), List.of(),
                0, null, null, null, null,
                null, List.of(new SegmentBound(BlockVector3.at(2, 1, 5), BlockVector3.at(4, 4, 5))), null,
                sacrifices, List.of(),
                null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), costs);
    }

    @Test
    void writesOneCostPerSacrificeMarker() {
        JsonElement json = saver.serializeSegmentTemplate(
                paywall(List.of(BlockVector3.at(3, 0, 3), BlockVector3.at(6, 0, 3)), List.of(2, 5)));

        JsonObject object = assertInstanceOf(JsonObject.class, json);
        assertEquals(2, object.getAsJsonArray("sandSacrificeCosts").size());
        assertEquals(2, object.getAsJsonArray("sandSacrificeCosts").get(0).getAsInt());
        assertEquals(5, object.getAsJsonArray("sandSacrificeCosts").get(1).getAsInt());
        assertFalse(object.has("sandTradeLocations"), "the removed trade chest is not written");
    }

    @Test
    void costsSurviveTheRoundTrip() throws Exception {
        JsonElement json = saver.serializeSegmentTemplate(
                paywall(List.of(BlockVector3.at(3, 0, 3), BlockVector3.at(6, 0, 3)), List.of(2, 5)));
        Files.writeString(new File(dataDir.toFile(), "paywall.json").toPath(), json.toString(), StandardCharsets.UTF_8);

        Segment reloaded = loader.loadSegmentTemplates(dataDir.toFile()).get(0);

        assertEquals(List.of(2, 5), reloaded.getSandSacrificeCosts());
        assertEquals(List.of(BlockVector3.at(3, 0, 3), BlockVector3.at(6, 0, 3)), reloaded.getSandSacrificeLocations());
    }
}
