package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.entities.Door;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.ItemManager;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins that segment doors actually exist in the world.
 *
 * <p>{@link DoorManager} used to register {@code SegmentDoor} objects without writing a single
 * block. Segment templates carve their doorways as open 3x4 holes, so the "door" was invisible
 * air the player walked straight through, and its lock location was air too — which never fires
 * {@code RIGHT_CLICK_BLOCK}, so the rusty key had nothing to be used on.
 *
 * <p>Also pins that only real connections become doors: entry points the generator attached no
 * neighbour to are sealed as plain wall instead of dressed as a door onto empty space.
 */
class DoorManagerSegmentDoorTest {

    /** Matches DoorManager's constants; a change to either should be a deliberate one. */
    private static final Material DOOR_MATERIAL = Material.DARK_OAK_PLANKS;
    private static final Material KEYHOLE_MATERIAL = Material.OXIDIZED_CUT_COPPER;

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private GameManager gameManager;
    private DoorManager doorManager;
    private PlayerMock player;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("segment-door-world");
        teamId = UUID.randomUUID();

        // A real MockBukkit plugin, not a Mockito mock: JavaPlugin.getName() is final, so a mock
        // hands NamespacedKey a null namespace, and the door animation needs a live scheduler.
        plugin = MockBukkit.createMockPlugin();
        ItemManager.initializeKeys(plugin);

        player = server.addPlayer();
        TeamManager teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);
        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);

        doorManager = new DoorManager(plugin, gameManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * A location in this world, with its chunk (and its neighbours across a chunk border) loaded:
     * Door only touches blocks in loaded chunks.
     */
    private Location at(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.loadChunk((x + dx) >> 4, (z + dz) >> 4);
            }
        }
        return new Location(world, x, y, z);
    }

    private Dungeon dungeon(List<EntryPoint> doorways, List<EntryPoint> unusedOpenings) {
        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getDoorways()).thenReturn(doorways);
        when(dungeon.getUnusedOpenings()).thenReturn(unusedOpenings);
        return dungeon;
    }

    private PlayerInteractEvent rightClick(Block block, ItemStack inHand) {
        player.getInventory().setItemInMainHand(inHand);
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, inHand, block, BlockFace.NORTH);
        doorManager.onPlayerInteract(event);
        return event;
    }

    /**
     * The 3x4 opening around a NORTH-facing marker at (x, y, z): three blocks wide along X
     * (the axis across a north/south passage) and four tall from the marker upwards.
     */
    private void assertOpeningIs(Location marker, Material expected) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                Block block = world.getBlockAt(marker.getBlockX() + dx, marker.getBlockY() + dy, marker.getBlockZ());
                assertEquals(expected, block.getType(),
                        "Block at offset (" + dx + ", " + dy + ", 0) from the marker");
            }
        }
    }

    @Test
    void aDoorwayIsBuiltAsASolidWallWithAKeyhole() {
        Location marker = at(5, 64, 5);

        doorManager.initializeDoorsForInstance(
                dungeon(List.of(new EntryPoint(marker, Direction.NORTH)), List.of()));

        Location lock = marker.clone().add(0, 1, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                Block block = world.getBlockAt(marker.getBlockX() + dx, marker.getBlockY() + dy, marker.getBlockZ());
                boolean isLock = block.getX() == lock.getBlockX()
                        && block.getY() == lock.getBlockY()
                        && block.getZ() == lock.getBlockZ();
                Material expected = isLock ? KEYHOLE_MATERIAL : DOOR_MATERIAL;
                assertEquals(expected, block.getType(),
                        "Block at offset (" + dx + ", " + dy + ", 0) from the marker");
            }
        }
        assertNotNull(doorManager.getDoorAt(teamId, lock), "The keyhole block should be a registered door");
    }

    @Test
    void anUnusedOpeningIsSealedWithNoDoorToOpen() {
        Location marker = at(20, 64, 20);

        doorManager.initializeDoorsForInstance(
                dungeon(List.of(), List.of(new EntryPoint(marker, Direction.NORTH))));

        assertOpeningIs(marker, DOOR_MATERIAL);
        for (int dy = 0; dy < 4; dy++) {
            assertNull(doorManager.getDoorAt(teamId, marker.clone().add(0, dy, 0)),
                    "A sealed opening is plain wall, not a door that costs a rusty key");
        }
    }

    @Test
    void aDoorwayIsBuiltAcrossZForAnEastFacingOpening() {
        // EAST/WEST passages run along X, so their doorway spans Z instead.
        Location marker = at(40, 64, 40);

        doorManager.initializeDoorsForInstance(
                dungeon(List.of(new EntryPoint(marker, Direction.EAST)), List.of()));

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 0; dy < 4; dy++) {
                Block block = world.getBlockAt(marker.getBlockX(), marker.getBlockY() + dy, marker.getBlockZ() + dz);
                assertNotEquals(Material.AIR, block.getType(),
                        "Block at offset (0, " + dy + ", " + dz + ") from the marker should be part of the door");
            }
        }
    }

    @Test
    void clickingTheKeyholeWithoutAKeyLeavesTheDoorStanding() {
        Location marker = at(5, 64, 5);
        doorManager.initializeDoorsForInstance(
                dungeon(List.of(new EntryPoint(marker, Direction.NORTH)), List.of()));
        Location lock = marker.clone().add(0, 1, 0);

        PlayerInteractEvent event = rightClick(lock.getBlock(), null);

        assertTrue(event.isCancelled(), "the keyhole click is ours to handle, not vanilla's");
        assertFalse(doorManager.getDoorAt(teamId, lock).isOpen(), "no key, no entry");
        assertEquals(KEYHOLE_MATERIAL, lock.getBlock().getType(), "the keyhole is still there");
    }

    @Test
    void aRustyKeyIsConsumedAndOpensTheDoor() {
        Location marker = at(5, 64, 5);
        doorManager.initializeDoorsForInstance(
                dungeon(List.of(new EntryPoint(marker, Direction.NORTH)), List.of()));
        Location lock = marker.clone().add(0, 1, 0);

        rightClick(lock.getBlock(), ItemManager.createRustyKey());
        server.getScheduler().performTicks(18L); // 4 layers at the default 3-tick delay, plus slack

        assertTrue(doorManager.getDoorAt(teamId, lock).isOpen(), "the door should be open");
        assertFalse(ItemManager.isRustyKey(player.getInventory().getItemInMainHand()),
                "the key is consumed on use");
        assertOpeningIs(marker, Material.AIR);
    }

    @Test
    void aVaultKeyDoesNotOpenASegmentDoor() {
        Location marker = at(5, 64, 5);
        doorManager.initializeDoorsForInstance(
                dungeon(List.of(new EntryPoint(marker, Direction.NORTH)), List.of()));
        Location lock = marker.clone().add(0, 1, 0);

        rightClick(lock.getBlock(), ItemManager.createVaultKey(VaultColor.BLUE));

        assertFalse(doorManager.getDoorAt(teamId, lock).isOpen(), "only rusty keys open segment doors");
        assertTrue(ItemManager.isVaultKey(player.getInventory().getItemInMainHand()),
                "a key that does not fit is not eaten");
    }

    @Test
    void everyDoorwayGetsItsOwnDoor() {
        List<EntryPoint> doorways = List.of(
                new EntryPoint(at(0, 64, 0), Direction.NORTH),
                new EntryPoint(at(0, 64, 30), Direction.SOUTH),
                new EntryPoint(at(30, 64, 0), Direction.EAST));

        doorManager.initializeDoorsForInstance(dungeon(doorways, List.of()));

        for (EntryPoint doorway : doorways) {
            Door door = doorManager.getDoorAt(teamId, doorway.getLocation().clone().add(0, 1, 0));
            assertNotNull(door, "Every connection should get a door, missing one at " + doorway.getLocation());
            assertFalse(door.isOpen(), "A freshly built door starts closed");
        }
    }
}
