package com.clarkson.sot.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.sk89q.worldedit.math.BlockVector3;

import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinRootEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link SchematicDimensions}, the load-time cross-check between a template's declared {@code size}
 * and the geometry its schematic actually ships. The last case is the one that matters in practice: it
 * pins the bundled hub's declared size against the bundled hub's schematic, which is the exact mismatch
 * that used to be discovered a round later, as blocks left standing after cleanup.
 */
class SchematicDimensionsTest {

    @TempDir
    Path dir;

    /** Writes a gzipped NBT file shaped like a Sponge schematic of the given version. */
    private File writeSchematic(String fileName, int spongeVersion, short width, short height, short length)
            throws Exception {
        LinCompoundTag header = LinCompoundTag.builder()
                .putInt("Version", spongeVersion)
                .putShort("Width", width)
                .putShort("Height", height)
                .putShort("Length", length)
                .build();
        // v3 nests the header under "Schematic"; v1/v2 keep it at the root.
        LinCompoundTag root = spongeVersion >= 3
                ? LinCompoundTag.builder().put("Schematic", header).build()
                : header;

        File file = new File(dir.toFile(), fileName);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            LinBinaryIO.write(out, new LinRootEntry("", root));
        }
        return file;
    }

    @Test
    void readsASpongeV3Header() throws Exception {
        File schematic = writeSchematic("v3.schem", 3, (short) 42, (short) 15, (short) 37);

        assertEquals(BlockVector3.at(42, 15, 37), SchematicDimensions.read(schematic));
    }

    @Test
    void readsASpongeV2HeaderAtTheRoot() throws Exception {
        File schematic = writeSchematic("v2.schem", 2, (short) 8, (short) 4, (short) 9);

        assertEquals(BlockVector3.at(8, 4, 9), SchematicDimensions.read(schematic));
    }

    @Test
    void returnsNullWhenThereIsNoDimensionHeader() throws Exception {
        File file = new File(dir.toFile(), "headerless.schem");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            LinBinaryIO.write(out, new LinRootEntry("", LinCompoundTag.builder().putInt("Version", 3).build()));
        }

        assertNull(SchematicDimensions.read(file), "an unrecognised file should degrade, not throw");
    }

    @Test
    void aDeclaredSizeMayExceedTheSchematicButNeverFallShort() {
        BlockVector3 schematic = BlockVector3.at(42, 15, 37);

        assertTrue(SchematicDimensions.covers(schematic, schematic), "an exact match is fine");
        assertTrue(SchematicDimensions.covers(BlockVector3.at(42, 17, 37), schematic),
                "declaring extra height is how the hub reserves room for its sand timer column");
        assertFalse(SchematicDimensions.covers(BlockVector3.at(42, 14, 37), schematic),
                "a short declaration leaves the top layer outside the region cleanup clears");
        assertFalse(SchematicDimensions.covers(BlockVector3.at(41, 15, 37), schematic));
        assertFalse(SchematicDimensions.covers(BlockVector3.at(42, 15, 36), schematic));
    }

    @Test
    void bundledHubDeclaresASizeThatCoversItsOwnSchematic() throws Exception {
        File hubJson = new File("src/main/resources/bundled_segments/hub.json");
        File hubSchem = new File("src/main/resources/bundled_segments/schematics/hub.schem");
        assertTrue(hubJson.isFile() && hubSchem.isFile(), "the bundled hub should be in the source tree");

        BlockVector3 actual = SchematicDimensions.read(hubSchem);
        assertNotNull(actual, "the bundled hub schematic should carry a readable dimension header");

        JsonObject size;
        try (FileReader reader = new FileReader(hubJson)) {
            size = new Gson().fromJson(reader, JsonObject.class).getAsJsonObject("size");
        }
        BlockVector3 declared = BlockVector3.at(
                size.get("x").getAsInt(), size.get("y").getAsInt(), size.get("z").getAsInt());

        assertTrue(SchematicDimensions.covers(declared, actual),
                "hub.json declares " + declared + " but hub.schem is " + actual
                        + "; everything outside the declared size survives dungeon cleanup");
    }
}
