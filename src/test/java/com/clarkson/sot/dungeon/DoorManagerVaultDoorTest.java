package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the vault door wall: built closed in its vault's colour, and sunk into the floor when that
 * vault is opened.
 *
 * <p>A vault door has no keyhole and no key of its own -- only one key of each colour exists per
 * dungeon and {@code VaultManager} consumes it at the vault marker, so a second keyhole here could
 * never be opened. That is why {@code openVaultDoors} is the entry point rather than a click handler,
 * and why {@code getDoorAt} must never resolve one.
 */
class DoorManagerVaultDoorTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private DoorManager doorManager;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("vault-door-world");
        teamId = UUID.randomUUID();
        plugin = MockBukkit.createMockPlugin();

        GameManager gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        doorManager = new DoorManager(plugin, gameManager);
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

    private Area wallAt(int x, int y, int z) {
        return new Area(at(x, y, z), at(x + 2, y + 3, z));
    }

    private void assertFilledWith(Area bounds, Material expected) {
        Location min = bounds.getMinPoint();
        Location max = bounds.getMaxPoint();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++)
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++)
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++)
                    assertEquals(expected, world.getBlockAt(x, y, z).getType(),
                            "block at " + x + "," + y + "," + z);
    }

    @Test
    void aVaultDoorIsBuiltClosedInItsVaultsColour() {
        Area wall = wallAt(5, 64, 5);

        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.GREEN, wall, "vaultroom")));

        assertFilledWith(wall, Material.LIME_CONCRETE);
    }

    @Test
    void openingTheVaultSinksItsDoor() {
        Area wall = wallAt(5, 64, 5);

        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.BLUE, wall, "vaultroom")));

        assertEquals(1, doorManager.openVaultDoors(teamId, VaultColor.BLUE), "one door opened");
        server.getScheduler().performTicks(18L); // 4 layers at the default 3-tick delay, plus slack

        assertFilledWith(wall, Material.AIR);
    }

    @Test
    void openingOneColourLeavesTheOthersStanding() {
        Area blueWall = wallAt(5, 64, 5);
        Area redWall = wallAt(45, 64, 45);

        doorManager.initializeGatesForInstance(teamId, List.of(), List.of(
                new VaultDoorPlacement(VaultColor.BLUE, blueWall, "blueroom"),
                new VaultDoorPlacement(VaultColor.RED, redWall, "redroom")));

        doorManager.openVaultDoors(teamId, VaultColor.BLUE);
        server.getScheduler().performTicks(18L);

        assertFilledWith(blueWall, Material.AIR);
        assertFilledWith(redWall, Material.RED_CONCRETE);
    }

    @Test
    void openingAColourWithNoDoorIsANoOp() {
        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.BLUE, wallAt(5, 64, 5), "blueroom")));

        assertEquals(0, doorManager.openVaultDoors(teamId, VaultColor.GOLD),
                "not every dungeon authors a door for every colour");
        assertEquals(0, doorManager.openVaultDoors(UUID.randomUUID(), VaultColor.BLUE),
                "and another team's instance is not this one's");
    }

    @Test
    void openingTheSameVaultTwiceOpensItsDoorOnlyOnce() {
        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.BLUE, wallAt(5, 64, 5), "blueroom")));

        assertEquals(1, doorManager.openVaultDoors(teamId, VaultColor.BLUE));
        server.getScheduler().performTicks(18L);
        assertEquals(0, doorManager.openVaultDoors(teamId, VaultColor.BLUE),
                "an already-open vault door reports nothing left to open");
    }

    @Test
    void aVaultDoorIsNotAKeyDoor() {
        Area wall = wallAt(5, 64, 5);

        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.BLUE, wall, "blueroom")));

        // getDoorAt feeds the rusty-key branch of onPlayerInteract. VaultManager owns every vault
        // interaction; a vault door resolving here would make one click emit two managers' messages.
        assertNull(doorManager.getDoorAt(teamId, wall.getMinPoint()), "the wall is not a keyed door");
    }

    @Test
    void clearTeamStateDropsTheVaultDoors() {
        doorManager.initializeGatesForInstance(teamId, List.of(),
                List.of(new VaultDoorPlacement(VaultColor.BLUE, wallAt(5, 64, 5), "blueroom")));

        doorManager.clearTeamState(teamId);

        assertEquals(0, doorManager.openVaultDoors(teamId, VaultColor.BLUE),
                "a torn-down team's vault doors no longer resolve");
    }
}
