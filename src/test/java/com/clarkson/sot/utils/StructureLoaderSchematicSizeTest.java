package com.clarkson.sot.utils;

import org.bukkit.plugin.Plugin;
import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinRootEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins the load-time size cross-check. Nothing else compares a template's declared {@code size} to its
 * schematic, and the declared size is what the dungeon's cleanup region is built from, so an
 * under-declared template leaves blocks standing after a round with nothing pointing at the cause.
 * Over-declaring is legitimate — the bundled hub does it — and must stay quiet.
 */
class StructureLoaderSchematicSizeTest {

    @TempDir
    Path dataDir;

    private final List<LogRecord> logged = new ArrayList<>();
    private StructureLoader loader;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("StructureLoaderSchematicSizeTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(logger);
        loader = new StructureLoader(plugin);
    }

    private void writeTemplate(int sizeX, int sizeY, int sizeZ) throws Exception {
        String json = """
                {
                  "name": "test_hub",
                  "type": "HUB",
                  "schematicFileName": "test_hub.schem",
                  "size": {"x": %d, "y": %d, "z": %d},
                  "entryPoints": []
                }
                """.formatted(sizeX, sizeY, sizeZ);
        Files.writeString(new File(dataDir.toFile(), "hub.json").toPath(), json, StandardCharsets.UTF_8);
    }

    private void writeSchematic(short width, short height, short length) throws Exception {
        File schematics = new File(dataDir.toFile(), "schematics");
        schematics.mkdirs();
        LinCompoundTag header = LinCompoundTag.builder()
                .putInt("Version", 3)
                .putShort("Width", width)
                .putShort("Height", height)
                .putShort("Length", length)
                .build();
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new FileOutputStream(new File(schematics, "test_hub.schem"))))) {
            LinBinaryIO.write(out, new LinRootEntry("", LinCompoundTag.builder().put("Schematic", header).build()));
        }
    }

    private List<String> warnings() {
        return logged.stream()
                .filter(r -> r.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    @Test
    void warnsWhenTheDeclaredSizeIsSmallerThanTheSchematic() throws Exception {
        writeTemplate(42, 12, 37);
        writeSchematic((short) 42, (short) 15, (short) 37);

        assertEquals(1, loader.loadSegmentTemplates(dataDir.toFile()).size(), "the template should still load");

        List<String> warnings = warnings();
        assertEquals(1, warnings.size(), "expected exactly one size warning, got " + warnings);
        assertTrue(warnings.get(0).contains("42x12x37") && warnings.get(0).contains("42x15x37"),
                "the warning should name both sizes: " + warnings.get(0));
    }

    @Test
    void staysQuietWhenTheDeclaredSizeReservesExtraRoom() throws Exception {
        writeTemplate(42, 17, 37); // the bundled hub's arrangement: two air layers for the timer column
        writeSchematic((short) 42, (short) 15, (short) 37);

        assertEquals(1, loader.loadSegmentTemplates(dataDir.toFile()).size());
        assertEquals(List.of(), warnings(), "over-declaring is deliberate and must not be flagged");
    }

    @Test
    void staysQuietWhenThereIsNoSchematicToCheckAgainst() throws Exception {
        writeTemplate(42, 12, 37);

        assertEquals(1, loader.loadSegmentTemplates(dataDir.toFile()).size());
        assertEquals(List.of(), warnings(),
                "a missing schematic already fails loudly at paste time; the loader should not duplicate it");
    }
}
