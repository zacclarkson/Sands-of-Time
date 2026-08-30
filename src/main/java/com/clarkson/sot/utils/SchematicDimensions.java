package com.clarkson.sot.utils;

import com.sk89q.worldedit.math.BlockVector3;

import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinRootEntry;
import org.enginehub.linbus.tree.LinShortTag;
import org.enginehub.linbus.tree.LinTagType;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Reads the block dimensions out of a WorldEdit {@code .schem} file without pasting it.
 *
 * <p>This exists so a template's declared {@code size} can be checked against the geometry it actually
 * ships at load time. Nothing else in the plugin cross-checks the two: {@link StructureLoader} never
 * opens a schematic, and the paste does not compare the clipboard to the template either, so a template
 * whose declared bounds are too small fails silently — the bounds are what
 * {@code DungeonManager.cleanupInstance()} air-fills between rounds, so the part of the build outside
 * them is simply left standing, and the next round cannot clear it (the paste uses
 * {@code ignoreAirBlocks}).
 *
 * <p>The header is read straight from the NBT rather than through {@code ClipboardFormats}, because a
 * clipboard read needs a live WorldEdit platform for the block registry, and this runs at plugin load.
 * Sponge schematic v3 nests the header under a {@code Schematic} compound; v1/v2 keep it at the root,
 * and both layouts are accepted.
 */
public final class SchematicDimensions {

    private SchematicDimensions() {}

    /**
     * @return the schematic's {@code (Width, Height, Length)} as {@code (x, y, z)}, or null when the
     *         file carries no readable dimension header.
     * @throws IOException if the file cannot be read or is not gzipped NBT.
     */
    @Nullable
    public static BlockVector3 read(File schematicFile) throws IOException {
        try (InputStream file = new BufferedInputStream(new FileInputStream(schematicFile));
             DataInputStream nbt = new DataInputStream(new BufferedInputStream(new GZIPInputStream(file)))) {
            return dimensionsOf(LinBinaryIO.readUsing(nbt, LinRootEntry::readFrom).value());
        }
    }

    /** Pulls the dimension header out of an already-parsed schematic root tag. */
    @Nullable
    static BlockVector3 dimensionsOf(LinCompoundTag root) {
        LinCompoundTag nested = root.findTag("Schematic", LinTagType.compoundTag()); // Sponge v3
        LinCompoundTag header = nested != null ? nested : root;                      // Sponge v1/v2
        LinShortTag width = header.findTag("Width", LinTagType.shortTag());
        LinShortTag height = header.findTag("Height", LinTagType.shortTag());
        LinShortTag length = header.findTag("Length", LinTagType.shortTag());
        if (width == null || height == null || length == null) {
            return null;
        }
        return BlockVector3.at(width.valueAsShort(), height.valueAsShort(), length.valueAsShort());
    }

    /**
     * Whether a template's declared {@code size} is big enough to contain {@code schematic}.
     *
     * <p>Deliberately not an equality check: declaring more than the schematic occupies is legitimate
     * and load-bearing. The bundled hub declares {@code size.y = 17} against a 15-block-tall schematic
     * precisely so the two air layers above it are inside the region cleanup clears — that is where the
     * visual sand timer column stands. Declaring <i>less</i> is the defect: those blocks fall outside
     * the region and survive teardown.
     */
    public static boolean covers(BlockVector3 declared, BlockVector3 schematic) {
        return declared.x() >= schematic.x()
                && declared.y() >= schematic.y()
                && declared.z() >= schematic.z();
    }
}
