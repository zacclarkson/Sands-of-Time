package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.segment.Segment;
import com.clarkson.sot.dungeon.segment.SegmentType;

import com.sk89q.worldedit.math.BlockVector3;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Round-trips a segment template through {@link StructureMetadataSaver} and {@link StructureLoader}
 * to check that the safe-exit offset survives being written to and read back from JSON. Only a
 * mocked {@link Plugin} (logger + data folder) is needed; no server is involved.
 */
class SegmentSafeExitMetadataTest {

    @TempDir
    File dataFolder;

    private StructureMetadataSaver saver;
    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SegmentSafeExitMetadataTest"));
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        saver = new StructureMetadataSaver(plugin);
        loader = new StructureLoader(plugin);
    }

    /** Builds a minimal HUB template carrying only the given safe-exit offset. */
    private Segment hubTemplate(String name, @Nullable BlockVector3 safeExitOffset) {
        return new Segment(
                name, SegmentType.HUB, name + ".schem", BlockVector3.at(16, 8, 16),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                0, null, null, null, null,
                null, new ArrayList<>(), null,
                new ArrayList<>(), new ArrayList<>(),
                safeExitOffset);
    }

    private Segment loadOnly(String name) {
        List<Segment> loaded = loader.loadSegmentTemplates(dataFolder);
        assertEquals(1, loaded.size(), "expected exactly one template in " + dataFolder);
        assertEquals(name, loaded.get(0).getName());
        return loaded.get(0);
    }

    @Test
    void safeExitOffsetSurvivesSaveAndLoad() {
        assertTrue(saver.saveMetadata(hubTemplate("hub_with_exit", BlockVector3.at(4, 1, 9))));

        Segment reloaded = loadOnly("hub_with_exit");
        assertEquals(BlockVector3.at(4, 1, 9), reloaded.getSafeExitOffset());
    }

    @Test
    void templateWithoutSafeExitLoadsWithNullOffset() {
        assertTrue(saver.saveMetadata(hubTemplate("hub_no_exit", null)));

        Segment reloaded = loadOnly("hub_no_exit");
        assertNull(reloaded.getSafeExitOffset());
    }
}
