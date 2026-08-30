package com.clarkson.sot.entities;

import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises a {@link SegmentDoor} against a live MockBukkit scheduler: built closed, then opened
 * layer by layer until the passage is clear.
 *
 * <p>The build step is what was missing in game — {@code DoorManager} registered door objects but
 * never wrote their blocks, so nothing blocked the passage and the lock location was air.
 */
class SegmentDoorAnimationTest {

    private static final Material DOOR_MATERIAL = Material.DARK_OAK_PLANKS;
    private static final Material KEYHOLE_MATERIAL = Material.OXIDIZED_CUT_COPPER;
    /** Door.animationTickDelay defaults to 3, and the opening is 4 layers tall. */
    private static final long TICKS_TO_FULLY_OPEN = 3L * 6L;

    private ServerMock server;
    private Plugin plugin;
    private World world;
    private SegmentDoor door;
    private Location marker;
    private Location lock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("door-animation-world");
        ItemManager.initializeKeys(plugin);

        // A 3-wide x 4-tall opening across the X axis, marker at its bottom centre. Door only
        // touches blocks in loaded chunks, so make sure the ones it spans are loaded.
        world.loadChunk(0, 0);
        marker = new Location(world, 8, 64, 8);
        lock = marker.clone().add(0, 1, 0);
        Area bounds = new Area(marker.clone().add(-1, 0, 0), marker.clone().add(1, 3, 0));
        door = new SegmentDoor(plugin, UUID.randomUUID(), bounds, lock, DOOR_MATERIAL, KEYHOLE_MATERIAL);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block blockAt(int dx, int dy) {
        return world.getBlockAt(marker.getBlockX() + dx, marker.getBlockY() + dy, marker.getBlockZ());
    }

    @Test
    void buildClosedFillsTheOpeningAndStampsTheKeyhole() {
        door.buildClosed();

        assertFalse(door.isOpen(), "A freshly built door is closed");
        assertEquals(KEYHOLE_MATERIAL, lock.getBlock().getType(), "The lock block is the keyhole");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                if (dx == 0 && dy == 1) continue; // the keyhole, asserted above
                assertEquals(DOOR_MATERIAL, blockAt(dx, dy).getType(),
                        "Block at offset (" + dx + ", " + dy + ") should be door material");
            }
        }
    }

    @Test
    void openingClearsTheWholePassage() {
        door.buildClosed();
        PlayerMock player = server.addPlayer();

        assertTrue(door.open(player), "A closed door with no animation running should open");
        server.getScheduler().performTicks(TICKS_TO_FULLY_OPEN);

        assertTrue(door.isOpen(), "The door reports itself open once the animation finishes");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                assertEquals(Material.AIR, blockAt(dx, dy).getType(),
                        "Block at offset (" + dx + ", " + dy + ") should be cleared, keyhole included");
            }
        }
    }

    @Test
    void theDoorClearsFromTheTopDown() {
        door.buildClosed();
        PlayerMock player = server.addPlayer();

        door.open(player);
        server.getScheduler().performTicks(3L); // one layer

        assertEquals(Material.AIR, blockAt(0, 3).getType(), "The top layer goes first, so the door sinks");
        assertNotEquals(Material.AIR, blockAt(0, 0).getType(), "The bottom layer is still standing");
    }

    @Test
    void onlyARustyKeyOpensASegmentDoor() {
        assertTrue(door.isCorrectKey(ItemManager.createRustyKey()));
        assertFalse(door.isCorrectKey(null));
        assertFalse(door.isCorrectKey(new org.bukkit.inventory.ItemStack(Material.TRIPWIRE_HOOK)));
    }
}
