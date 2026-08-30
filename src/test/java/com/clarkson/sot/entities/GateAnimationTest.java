package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link Gate}'s own behaviour: built as a solid iron-bars wall, sinking downward when opened,
 * and refusing both keys and closing.
 *
 * <p>The sinking order matters -- clearing the top layer first is what makes the wall look like it
 * drops into the floor rather than vanishing.
 */
class GateAnimationTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("gate-animation-world");
        plugin = MockBukkit.createMockPlugin();
        ItemManager.initializeKeys(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                world.loadChunk((x + dx) >> 4, (z + dz) >> 4);
        return new Location(world, x, y, z);
    }

    private Gate gate() {
        return new Gate(plugin, teamId, new Area(at(5, 64, 5), at(7, 67, 5)));
    }

    @Test
    void buildClosedFillsTheOpeningWithIronBars() {
        gate().buildClosed();

        for (int x = 5; x <= 7; x++)
            for (int y = 64; y <= 67; y++)
                assertEquals(Material.IRON_BARS, world.getBlockAt(x, y, 5).getType(),
                        "block at " + x + "," + y);
    }

    @Test
    void openingClearsTheTopLayerFirstSoTheWallSinks() {
        Gate gate = gate();
        gate.buildClosed();

        assertTrue(gate.open(), "a closed gate opens");
        server.getScheduler().performTicks(1L); // one layer only

        assertEquals(Material.AIR, world.getBlockAt(6, 67, 5).getType(), "the top layer goes first");
        assertEquals(Material.IRON_BARS, world.getBlockAt(6, 64, 5).getType(),
                "the bottom layer is still standing one tick in");
    }

    @Test
    void openingRunsToCompletion() {
        Gate gate = gate();
        gate.buildClosed();
        gate.open();
        server.getScheduler().performTicks(18L); // 4 layers at the default 3-tick delay, plus slack

        for (int x = 5; x <= 7; x++)
            for (int y = 64; y <= 67; y++)
                assertEquals(Material.AIR, world.getBlockAt(x, y, 5).getType(), "block at " + x + "," + y);
        assertTrue(gate.isOpen(), "the gate reports itself open");
    }

    @Test
    void anAlreadyOpenGateDoesNotReopen() {
        Gate gate = gate();
        gate.buildClosed();
        gate.open();
        server.getScheduler().performTicks(18L);

        assertFalse(gate.open(), "a second lever pull has nothing left to open");
    }

    @Test
    void aGateTakesNoKeyAndCannotBeClosed() {
        Gate gate = gate();

        assertFalse(gate.isCorrectKey(null), "no key at all");
        assertFalse(gate.isCorrectKey(ItemManager.createRustyKey()), "not a rusty key");
        for (VaultColor color : VaultColor.values()) {
            assertFalse(gate.isCorrectKey(ItemManager.createVaultKey(color)), "not a " + color + " vault key");
        }
        assertFalse(gate.close(null), "gates are one-way: the choice to open is not reversible");
    }
}
