package com.clarkson.sot.main;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs the segment templates bundled in the plugin jar (under {@code bundled_segments/}) into the
 * plugin data folder, so a fresh server ships with a working HUB and {@code /sot start} works without a
 * hand-built segment. The names come from {@code bundled_segments/manifest.txt}; for each name the
 * {@code <name>.json} goes to the data folder root and {@code schematics/<name>.schem} to the schematics
 * sub-dir — the layout {@link com.clarkson.sot.utils.StructureLoader} reads.
 *
 * <p>Two rules govern what happens when a template is already on disk.
 *
 * <p><b>Skip-if-present, but never silently.</b> An existing template is kept, because it may carry
 * in-game edits that a plugin update must not clobber. That means a corrected bundled template does
 * <i>not</i> reach a server that has run the plugin before, so the skip is reported: the bytes are
 * compared against the bundled copy and, when they differ, the operator is told which halves differ and
 * what to delete to take the bundled version. A skip of an identical copy is only worth a {@code FINE}.
 *
 * <p><b>The {@code .json} and the {@code .schem} are one unit.</b> They come from a single
 * {@code /sotsavesegment}, and nothing downstream cross-checks them — {@code StructureLoader} never
 * opens a schematic — so a template whose declared {@code size} and actual geometry disagree fails
 * quietly, at cleanup time rather than at load. Installing one bundled half next to one stale local
 * half is exactly how that is produced, and deleting a single half (the only way to pick up an updated
 * bundled template) is how an operator lands there. So a half-present pair is installed as a unit: the
 * surviving half is moved aside to {@code <name>.<ext>.bak} and both bundled files are written, with a
 * warning naming the backup.
 */
public class BundledSegmentInstaller {

    /** Manifest listing the bundled template names, one per line; {@code #} starts a comment. */
    static final String MANIFEST_RESOURCE = "bundled_segments/manifest.txt";
    /** Suffix given to a surviving half that is moved aside when its pair is completed from the jar. */
    static final String BACKUP_SUFFIX = ".bak";

    private final Function<String, InputStream> resources;
    private final File dataFolder;
    private final Logger logger;

    /**
     * @param resources opens a jar resource by path, returning null when the jar has no such entry
     *                  (i.e. {@link org.bukkit.plugin.java.JavaPlugin#getResource}).
     * @param dataFolder the plugin data folder; {@code schematics/} beneath it holds the schematics.
     */
    public BundledSegmentInstaller(Function<String, InputStream> resources, File dataFolder, Logger logger) {
        this.resources = resources;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /** Installs every template named in the manifest. Does nothing when this build bundles none. */
    public void install() {
        byte[] manifest = readResource(MANIFEST_RESOURCE);
        if (manifest == null) {
            return; // No segments bundled in this build.
        }
        File schematicsDir = new File(dataFolder, "schematics");
        for (String name : parseManifest(manifest)) {
            try {
                installTemplate(name, schematicsDir);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to install bundled segment '" + name + "'", e);
            }
        }
    }

    /** Names listed in the manifest, in file order, ignoring blank and {@code #} comment lines. */
    private static List<String> parseManifest(byte[] manifest) {
        List<String> names = new ArrayList<>();
        for (String line : new String(manifest, StandardCharsets.UTF_8).split("\\R")) {
            String name = line.trim();
            if (!name.isEmpty() && !name.startsWith("#")) {
                names.add(name);
            }
        }
        return names;
    }

    private void installTemplate(String name, File schematicsDir) throws IOException {
        File jsonTarget = new File(dataFolder, name + ".json");
        File schemTarget = new File(schematicsDir, name + ".schem");

        byte[] bundledJson = readResource("bundled_segments/" + name + ".json");
        byte[] bundledSchem = readResource("bundled_segments/schematics/" + name + ".schem");
        if (bundledJson == null || bundledSchem == null) {
            // Installing the half that does exist would produce the very mismatch this class avoids.
            logger.warning("Bundled segment '" + name + "' is listed in " + MANIFEST_RESOURCE
                    + " but its " + (bundledJson == null ? name + ".json" : "schematics/" + name + ".schem")
                    + " is missing from the plugin jar; installing neither half, since a template needs both.");
            return;
        }

        boolean jsonPresent = jsonTarget.isFile();
        boolean schemPresent = schemTarget.isFile();

        if (!jsonPresent && !schemPresent) {
            writePair(jsonTarget, bundledJson, schemTarget, bundledSchem);
            logger.info("Installed bundled segment '" + name + "' (" + jsonTarget.getName()
                    + " + schematics/" + schemTarget.getName() + ").");
            return;
        }

        if (jsonPresent && schemPresent) {
            reportKeptTemplate(name, jsonTarget, bundledJson, schemTarget, bundledSchem);
            return;
        }

        // Exactly one half is on disk. Complete the pair from the jar rather than leaving a template
        // that either has no geometry or no metadata, and keep the survivor next to it for recovery.
        File survivor = jsonPresent ? jsonTarget : schemTarget;
        File missing = jsonPresent ? schemTarget : jsonTarget;
        File backup = new File(survivor.getParentFile(), survivor.getName() + BACKUP_SUFFIX);
        Files.move(survivor.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        writePair(jsonTarget, bundledJson, schemTarget, bundledSchem);
        logger.warning("Segment '" + name + "' was half-installed: " + survivor.getName()
                + " was on disk but " + missing.getName() + " was missing. A template's .json and .schem"
                + " must come from the same save, so the bundled pair has been installed as a unit;"
                + " the file that was there is kept as " + backup.getName() + ".");
    }

    /**
     * Logs what a skip actually costs. Identical copies are routine and log at {@code FINE}; a template
     * that differs from the jar's is the useful signal — it means a corrected bundled template is on the
     * server but not in use, which is otherwise invisible.
     */
    private void reportKeptTemplate(String name, File jsonTarget, byte[] bundledJson,
                                    File schemTarget, byte[] bundledSchem) throws IOException {
        boolean jsonDiffers = !Arrays.equals(bundledJson, Files.readAllBytes(jsonTarget.toPath()));
        boolean schemDiffers = !Arrays.equals(bundledSchem, Files.readAllBytes(schemTarget.toPath()));

        if (!jsonDiffers && !schemDiffers) {
            logger.fine("Bundled segment '" + name + "' is already installed and matches the copy in this jar.");
            return;
        }

        String differing;
        if (jsonDiffers && schemDiffers) {
            differing = "both " + jsonTarget.getName() + " and schematics/" + schemTarget.getName();
        } else if (jsonDiffers) {
            differing = jsonTarget.getName();
        } else {
            differing = "schematics/" + schemTarget.getName();
        }
        logger.info("Keeping the existing '" + name + "' segment: " + differing + " differs from the copy"
                + " bundled in this plugin jar, so any change shipped with a plugin update is NOT in use."
                + " Delete both " + jsonTarget.getPath() + " and " + schemTarget.getPath()
                + " to install the bundled version (in-game edits to that segment would be lost).");
    }

    /** Writes both halves of a template, creating the schematics directory if it is missing. */
    private static void writePair(File jsonTarget, byte[] json, File schemTarget, byte[] schem) throws IOException {
        File parent = jsonTarget.getParentFile();
        if (parent != null) parent.mkdirs();
        File schemParent = schemTarget.getParentFile();
        if (schemParent != null) schemParent.mkdirs();
        Files.copy(new ByteArrayInputStream(json), jsonTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(new ByteArrayInputStream(schem), schemTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Reads a jar resource fully, or returns null when the jar has no such entry. */
    private byte[] readResource(String resourcePath) {
        try (InputStream in = resources.apply(resourcePath)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read bundled resource " + resourcePath, e);
            return null;
        }
    }
}
