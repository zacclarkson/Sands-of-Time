package com.clarkson.sot.main;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SoTConfig}, the config.yml location reader/writer.
 *
 * <p>The world lookup is injected, so these only need a {@link YamlConfiguration} and a stubbed
 * {@link World}. MockBukkit is started anyway because Bukkit's configuration classes reach for the
 * server logger on some error paths.
 */
class SoTConfigTest {

    private static final String WORLD_NAME = "sot_world";
    private static final Logger LOG = Logger.getLogger(SoTConfigTest.class.getName());

    private World world;
    private Function<String, World> worlds;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD_NAME);
        worlds = name -> WORLD_NAME.equals(name) ? world : null;
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
    void parsesFullSection() {
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: sot_world
                    x: 12.5
                    y: 64.0
                    z: -8.25
                    yaw: 90.0
                    pitch: -12.0
                """);

        Location location = SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG);

        assertNotNull(location);
        assertSame(world, location.getWorld());
        assertEquals(12.5, location.getX(), 1e-6);
        assertEquals(64.0, location.getY(), 1e-6);
        assertEquals(-8.25, location.getZ(), 1e-6);
        assertEquals(90.0f, location.getYaw(), 1e-4);
        assertEquals(-12.0f, location.getPitch(), 1e-4);
    }

    @Test
    void yawAndPitchDefaultToZero() {
        YamlConfiguration config = config("""
                locations:
                  trapped:
                    world: sot_world
                    x: 1.0
                    y: 2.0
                    z: 3.0
                """);

        Location location = SoTConfig.readLocation(config, SoTConfig.TRAPPED_PATH, worlds, LOG);

        assertNotNull(location);
        assertEquals(0.0f, location.getYaw(), 1e-4);
        assertEquals(0.0f, location.getPitch(), 1e-4);
    }

    @Test
    void acceptsIntegerCoordinates() {
        // YAML parses `y: 100` as an Integer, so the reader must not insist on Double.
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: sot_world
                    x: 0
                    y: 100
                    z: -20
                """);

        Location location = SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG);

        assertNotNull(location);
        assertEquals(100.0, location.getY(), 1e-6);
        assertEquals(-20.0, location.getZ(), 1e-6);
    }

    @Test
    void missingSectionIsUnset() {
        assertNull(SoTConfig.readLocation(config("other: 1\n"), SoTConfig.LOBBY_PATH, worlds, LOG));
    }

    @Test
    void blankWorldIsUnset() {
        // This is the shipped default: the section exists to document the schema, world is blank.
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: ''
                    x: 0.0
                    y: 0.0
                    z: 0.0
                """);

        assertNull(SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG));
    }

    @Test
    void unknownWorldIsRejected() {
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: deleted_world
                    x: 1.0
                    y: 2.0
                    z: 3.0
                """);

        assertNull(SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG));
    }

    @Test
    void missingCoordinateIsRejectedRatherThanDefaultedToZero() {
        // getDouble() would silently return 0.0 here and drop players at y=0.
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: sot_world
                    x: 1.0
                    z: 3.0
                """);

        assertNull(SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG));
    }

    @Test
    void nonNumericCoordinateIsRejected() {
        YamlConfiguration config = config("""
                locations:
                  lobby:
                    world: sot_world
                    x: over there
                    y: 2.0
                    z: 3.0
                """);

        assertNull(SoTConfig.readLocation(config, SoTConfig.LOBBY_PATH, worlds, LOG));
    }

    @Test
    void writeThenReadRoundTrips() {
        YamlConfiguration config = new YamlConfiguration();
        Location original = new Location(world, 12.5, 64.0, -8.25, 90.0f, -12.0f);

        SoTConfig.writeLocation(config, SoTConfig.TRAPPED_PATH, original);
        Location read = SoTConfig.readLocation(config, SoTConfig.TRAPPED_PATH, worlds, LOG);

        assertNotNull(read);
        assertEquals(original.getX(), read.getX(), 1e-6);
        assertEquals(original.getY(), read.getY(), 1e-6);
        assertEquals(original.getZ(), read.getZ(), 1e-6);
        assertEquals(original.getYaw(), read.getYaw(), 1e-4);
        assertEquals(original.getPitch(), read.getPitch(), 1e-4);
        assertSame(world, read.getWorld());
    }

    @Test
    void writeProducesHandEditableScalars() {
        // Regression guard: if someone switches to config.set(path, location), the YAML grows an
        // opaque `==: org.bukkit.Location` marker and stops being hand-editable.
        YamlConfiguration config = new YamlConfiguration();
        SoTConfig.writeLocation(config, SoTConfig.LOBBY_PATH, new Location(world, 1.0, 2.0, 3.0));

        String yaml = config.saveToString();
        assertFalse(yaml.contains("=="), "Expected plain scalars, got a serialized Location:\n" + yaml);
        assertTrue(yaml.contains("world: " + WORLD_NAME));
    }

    @Test
    void writeRejectsLocationWithoutWorld() {
        YamlConfiguration config = new YamlConfiguration();
        assertThrows(NullPointerException.class,
                () -> SoTConfig.writeLocation(config, SoTConfig.LOBBY_PATH, new Location(null, 1, 2, 3)));
    }
}
