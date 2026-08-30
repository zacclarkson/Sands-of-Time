package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.VaultColor;
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
 * Covers loading the gate, lever and vault-door geometry the builder authors.
 *
 * <p>Until these were consumed at runtime the only fixture anywhere carried {@code "gates": []}, so a
 * populated array had never actually been read back. {@link StructureLoader} only uses the plugin for
 * logging, so this runs without a server.
 */
class StructureLoaderGateFeaturesTest {

    @TempDir
    Path dataDir;

    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StructureLoaderGateFeaturesTest"));
        loader = new StructureLoader(plugin);
    }

    private void writeTemplate(String gatesJson, String extraJson) throws Exception {
        String json = """
                {
                  "name": "gate_room",
                  "type": "SMALL_ROOM",
                  "schematicFileName": "gate_room.schem",
                  "size": {"x": 16, "y": 8, "z": 16},
                  "entryPoints": [],
                  "sandSpawnLocations": [],
                  "itemSpawnLocations": [],
                  "coinSpawnLocations": [],
                  "totalCoins": 0,
                  "gates": %s,
                  "sandSacrificeLocations": [],
                  "mobSpawnerLocations": []%s
                }
                """.formatted(gatesJson, extraJson);
        Files.writeString(new File(dataDir.toFile(), "gate_room.json").toPath(), json, StandardCharsets.UTF_8);
    }

    private static void assertBound(SegmentBound bound, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        assertEquals(BlockVector3.at(minX, minY, minZ), bound.getMin(), "bound min");
        assertEquals(BlockVector3.at(maxX, maxY, maxZ), bound.getMax(), "bound max");
    }

    @Test
    void readsEveryGateWithItsLever() throws Exception {
        writeTemplate("""
                [
                    {"min": {"x": 2, "y": 1, "z": 5}, "max": {"x": 4, "y": 4, "z": 5}},
                    {"min": {"x": 8, "y": 1, "z": 5}, "max": {"x": 10, "y": 4, "z": 5}}
                  ]""", """
                ,
                  "leverOffset": {"x": 1, "y": 2, "z": 5}""");

        Segment segment = loader.loadSegmentTemplates(dataDir.toFile()).get(0);

        assertEquals(2, segment.getGates().size(), "both gates survive the round trip");
        assertBound(segment.getGates().get(0), 2, 1, 5, 4, 4, 5);
        assertBound(segment.getGates().get(1), 8, 1, 5, 10, 4, 5);
        assertEquals(BlockVector3.at(1, 2, 5), segment.getLeverOffset(),
                "one lever opens every gate in the segment");
    }

    @Test
    void readsAVaultDoorAndTheColourThatOpensIt() throws Exception {
        writeTemplate("[]", """
                ,
                  "containedVault": "GREEN",
                  "vaultLocationOffset": {"x": 7, "y": 1, "z": 7},
                  "vaultDoorBound": {"min": {"x": 3, "y": 1, "z": 8}, "max": {"x": 5, "y": 4, "z": 8}}""");

        Segment segment = loader.loadSegmentTemplates(dataDir.toFile()).get(0);

        assertNotNull(segment.getVaultDoorBound(), "the vault door bound survives the round trip");
        assertBound(segment.getVaultDoorBound(), 3, 1, 8, 5, 4, 8);
        // A vault door has no key of its own, so the colour is the only thing that can open it.
        assertEquals(VaultColor.GREEN, segment.getContainedVault(), "the vault whose opening drops this wall");
    }

    @Test
    void aTemplateWithNoGateMarkersStillLoads() throws Exception {
        // Back-compatibility: every segment saved before these markers existed must keep working.
        writeTemplate("[]", "");

        Segment segment = loader.loadSegmentTemplates(dataDir.toFile()).get(0);

        assertTrue(segment.getGates().isEmpty(), "no gates");
        assertNull(segment.getLeverOffset(), "no lever");
        assertNull(segment.getVaultDoorBound(), "no vault door");
    }

    @Test
    void aMissingGatesKeyLoadsAsNoGates() throws Exception {
        String json = """
                {
                  "name": "gate_room",
                  "type": "SMALL_ROOM",
                  "schematicFileName": "gate_room.schem",
                  "size": {"x": 16, "y": 8, "z": 16},
                  "entryPoints": [],
                  "sandSpawnLocations": [],
                  "itemSpawnLocations": [],
                  "coinSpawnLocations": [],
                  "totalCoins": 0,
                  "sandSacrificeLocations": [],
                  "mobSpawnerLocations": []
                }
                """;
        Files.writeString(new File(dataDir.toFile(), "gate_room.json").toPath(), json, StandardCharsets.UTF_8);

        List<Segment> segments = loader.loadSegmentTemplates(dataDir.toFile());

        assertEquals(1, segments.size(), "a template with no gates key is still a valid template");
        assertTrue(segments.get(0).getGates().isEmpty());
    }
}
