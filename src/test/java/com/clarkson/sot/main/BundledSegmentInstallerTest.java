package com.clarkson.sot.main;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link BundledSegmentInstaller}: what it installs, what it keeps, and — the point of the class —
 * what it says about a keep. The two properties that matter are that an existing template is never
 * overwritten just because the jar's copy differs, and that a template is never left half from the jar
 * and half from disk, since nothing downstream cross-checks a template's .json against its .schem.
 */
class BundledSegmentInstallerTest {

    private static final byte[] BUNDLED_JSON = "{\"name\":\"hub\",\"size\":{\"y\":17}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BUNDLED_SCHEM = new byte[] {1, 2, 3, 4, 5};

    @TempDir
    Path dataFolder;

    private final Map<String, byte[]> jarResources = new HashMap<>();
    private final List<LogRecord> logged = new ArrayList<>();
    private Logger logger;

    private File json() { return new File(dataFolder.toFile(), "hub.json"); }
    private File schem() { return new File(dataFolder.toFile(), "schematics/hub.schem"); }

    @BeforeEach
    void setUp() {
        jarResources.put("bundled_segments/manifest.txt", "# a comment\n\nhub\n".getBytes(StandardCharsets.UTF_8));
        jarResources.put("bundled_segments/hub.json", BUNDLED_JSON);
        jarResources.put("bundled_segments/schematics/hub.schem", BUNDLED_SCHEM);

        logger = Logger.getLogger("BundledSegmentInstallerTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    private void install() {
        new BundledSegmentInstaller(this::openResource, dataFolder.toFile(), logger).install();
    }

    private InputStream openResource(String path) {
        byte[] bytes = jarResources.get(path);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    private void writeLocal(File target, String contents) throws Exception {
        target.getParentFile().mkdirs();
        Files.writeString(target.toPath(), contents, StandardCharsets.UTF_8);
    }

    /** All log output for one run, so assertions can look for the operator-facing wording. */
    private String log() {
        StringBuilder sb = new StringBuilder();
        for (LogRecord record : logged) sb.append(record.getLevel()).append(' ').append(record.getMessage()).append('\n');
        return sb.toString();
    }

    private boolean loggedAtLeast(Level level) {
        return logged.stream().anyMatch(r -> r.getLevel().intValue() >= level.intValue());
    }

    @Test
    void installsBothHalvesOnAFreshServer() throws Exception {
        install();

        assertArrayEquals(BUNDLED_JSON, Files.readAllBytes(json().toPath()), "the .json should be installed");
        assertArrayEquals(BUNDLED_SCHEM, Files.readAllBytes(schem().toPath()), "the .schem should be installed");
        assertTrue(log().contains("Installed bundled segment 'hub'"), "the install should be logged: " + log());
    }

    @Test
    void keepsAnIdenticalTemplateWithoutRaisingItsVoice() throws Exception {
        install();
        logged.clear();

        install(); // second start-up, nothing has changed

        assertArrayEquals(BUNDLED_JSON, Files.readAllBytes(json().toPath()));
        assertFalse(loggedAtLeast(Level.INFO),
                "an unchanged bundled segment is routine and should not reach the console: " + log());
        assertTrue(log().contains("matches the copy in this jar"), "the skip should still be traceable: " + log());
    }

    @Test
    void keepsAnEditedTemplateButSaysTheBundledOneIsNotInUse() throws Exception {
        writeLocal(json(), "{\"name\":\"hub\",\"size\":{\"y\":15}}"); // e.g. the pre-fix hub still on disk
        writeLocal(schem(), "locally edited geometry");

        install();

        assertEquals("{\"name\":\"hub\",\"size\":{\"y\":15}}", Files.readString(json().toPath()),
                "an existing template must never be clobbered -- it may carry in-game edits");
        assertEquals("locally edited geometry", Files.readString(schem().toPath()));

        String log = log();
        assertTrue(log.contains("Keeping the existing 'hub' segment"), log);
        assertTrue(log.contains("NOT in use"), "the operator needs to know the jar's copy is being ignored: " + log);
        assertTrue(log.contains(json().getPath()) && log.contains(schem().getPath()),
                "the remedy has to name both files, since deleting one is what half-installs a template: " + log);
    }

    @Test
    void namesOnlyTheHalfThatDiffers() throws Exception {
        install();
        logged.clear();
        writeLocal(json(), "{\"name\":\"hub\",\"edited\":true}");

        install();

        assertTrue(log().contains("hub.json differs"), log());
        assertFalse(log().contains("both hub.json"), "the schematic is untouched, so it should not be named: " + log());
    }

    @Test
    void completesAHalfInstalledTemplateFromTheJarAndKeepsTheSurvivor() throws Exception {
        // The operator's only way to pick up a corrected bundled template today: delete one half.
        writeLocal(json(), "{\"name\":\"hub\",\"size\":{\"y\":15}}");

        install();

        assertArrayEquals(BUNDLED_JSON, Files.readAllBytes(json().toPath()),
                "a lone half must be completed from the jar, not left beside a stale local one");
        assertArrayEquals(BUNDLED_SCHEM, Files.readAllBytes(schem().toPath()));
        assertEquals("{\"name\":\"hub\",\"size\":{\"y\":15}}",
                Files.readString(new File(dataFolder.toFile(), "hub.json.bak").toPath()),
                "the file that was there should be recoverable");
        assertTrue(log().contains("half-installed"), log());
        assertTrue(loggedAtLeast(Level.WARNING), "replacing a file on disk deserves a warning: " + log());
    }

    @Test
    void completesAHalfInstalledTemplateWhenTheSchematicIsTheSurvivor() throws Exception {
        writeLocal(schem(), "orphaned geometry");

        install();

        assertArrayEquals(BUNDLED_JSON, Files.readAllBytes(json().toPath()));
        assertArrayEquals(BUNDLED_SCHEM, Files.readAllBytes(schem().toPath()));
        assertEquals("orphaned geometry",
                Files.readString(new File(dataFolder.toFile(), "schematics/hub.schem.bak").toPath()));
    }

    @Test
    void installsNeitherHalfWhenTheJarShipsOnlyOne() {
        jarResources.remove("bundled_segments/schematics/hub.schem");

        install();

        assertFalse(json().exists(), "installing the .json alone would leave a template with no geometry");
        assertFalse(schem().exists());
        assertTrue(log().contains("missing from the plugin jar"), log());
    }

    @Test
    void doesNothingWhenTheBuildBundlesNoSegments() {
        jarResources.clear();

        install();

        assertFalse(json().exists());
        assertTrue(logged.isEmpty(), "a build with no bundled segments has nothing to say: " + log());
    }
}
