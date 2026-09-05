package com.clarkson.sot.main;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ResourcePackSettings}, the {@code resource-pack} section of config.yml.
 *
 * <p>Pure configuration parsing plus the hashing helpers the listener relies on. MockBukkit is
 * started only because Bukkit's configuration classes reach for the server logger on some error
 * paths (same reason as {@link SoTConfigTest}).
 */
class ResourcePackSettingsTest {

    /** {@code sha1sum} of the ASCII bytes "abc" — a well-known test vector. */
    private static final String ABC_SHA1 = "a9993e364706816aba3e25717850c26c9cd0d89d";

    private Logger log;
    private StringBuilder warnings;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        warnings = new StringBuilder();
        log = Logger.getLogger(ResourcePackSettingsTest.class.getName() + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.append(record.getMessage()).append('\n');
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private YamlConfiguration config(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        assertDoesNotThrow(() -> configuration.loadFromString(yaml));
        return configuration;
    }

    @Test
    void absentSectionMeansDisabledWithoutComplaint() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("locations: {}"),
                ResourcePackSettings.PATH, log);

        assertFalse(settings.isEnabled());
        assertNull(settings.url());
        assertEquals("", warnings.toString(), "An unconfigured pack is normal, not a warning");
    }

    @Test
    void shippedBlankDefaultsMeanDisabled() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: ''
                  sha1: ''
                  required: false
                  prompt: ''
                """), ResourcePackSettings.PATH, log);

        assertSame(ResourcePackSettings.DISABLED, settings);
        assertEquals("", warnings.toString());
    }

    @Test
    void parsesFullSection() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: http://100.125.118.2:25701/sot.zip
                  sha1: A9993E364706816ABA3E25717850C26C9CD0D89D
                  required: true
                  prompt: Grab the textures!
                """), ResourcePackSettings.PATH, log);

        assertTrue(settings.isEnabled());
        assertEquals("http://100.125.118.2:25701/sot.zip", settings.url());
        assertEquals(ABC_SHA1, settings.sha1Hex(), "Hash is normalised to lower case");
        assertArrayEquals(ResourcePackSettings.parseSha1(ABC_SHA1), settings.sha1Bytes());
        assertTrue(settings.required());
        assertEquals("Grab the textures!", settings.prompt());
        assertEquals("", warnings.toString());
    }

    @Test
    void urlAloneHashesTheDownloadAndUsesDefaults() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: https://packs.example/sot.zip
                """), ResourcePackSettings.PATH, log);

        assertTrue(settings.isEnabled());
        assertNull(settings.sha1Hex(), "No sha1 means: hash the download at enable");
        assertNull(settings.sha1Bytes());
        assertFalse(settings.required(), "Packs are optional unless asked for");
        assertEquals(ResourcePackSettings.DEFAULT_PROMPT, settings.prompt());
    }

    @Test
    void nonHttpUrlDisablesWithAWarning() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: ftp://packs.example/sot.zip
                """), ResourcePackSettings.PATH, log);

        assertFalse(settings.isEnabled());
        assertTrue(warnings.toString().contains("resource-pack.url"), warnings.toString());
    }

    @Test
    void composeServiceNameIsNotAUrlEither() {
        // The classic mistake: pointing clients at the docker-compose service name.
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: pack:80/sot.zip
                """), ResourcePackSettings.PATH, log);

        assertFalse(settings.isEnabled());
        assertTrue(warnings.toString().contains("not an http(s) URL"), warnings.toString());
    }

    @Test
    void malformedSha1FallsBackToHashingWithAWarning() {
        ResourcePackSettings settings = ResourcePackSettings.read(config("""
                resource-pack:
                  url: http://packs.example/sot.zip
                  sha1: not-a-hash
                """), ResourcePackSettings.PATH, log);

        assertTrue(settings.isEnabled(), "A bad hash should not cost the operator the pack");
        assertNull(settings.sha1Hex());
        assertTrue(warnings.toString().contains("resource-pack.sha1"), warnings.toString());
    }

    @Test
    void sha1HelpersRoundTripAndMatchSha1sum() {
        byte[] digest = ResourcePackSettings.sha1Of("abc".getBytes(StandardCharsets.US_ASCII));

        assertEquals(20, digest.length);
        assertEquals(ABC_SHA1, ResourcePackSettings.toHex(digest));
        assertArrayEquals(digest, ResourcePackSettings.parseSha1(ABC_SHA1.toUpperCase()));
    }

    @Test
    void parseSha1RejectsWrongLengthAndNonHex() {
        assertThrows(IllegalArgumentException.class, () -> ResourcePackSettings.parseSha1("abc"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackSettings.parseSha1("zz993e364706816aba3e25717850c26c9cd0d89d"));
    }

    @Test
    void constructorRejectsGarbageHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePackSettings("http://x/y.zip", "nope", false, "p"));
    }
}
