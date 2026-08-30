package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.entities.Door;
import com.clarkson.sot.entities.SegmentDoor;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.SoT;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins which manager owns a vault marker block.
 *
 * <p>{@link DoorManager} used to register a {@code VaultDoor} at every vault marker location —
 * the same block {@link VaultManager} already handles — so one right-click ran both handlers and
 * the player got "This key doesn't fit this vault!" followed by "This door is locked. You need
 * the correct key.". VaultManager owns vault markers; DoorManager owns segment doors only.
 */
class DoorManagerVaultOwnershipTest {

    private ServerMock server;
    private World world;
    private SoT plugin;
    private GameManager gameManager;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("door-test-world");
        teamId = UUID.randomUUID();

        plugin = mock(SoT.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoorManagerVaultOwnershipTest"));

        gameManager = mock(GameManager.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        // Door only touches blocks in loaded chunks, and initializing a doorway builds its blocks.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.loadChunk((x + dx) >> 4, (z + dz) >> 4);
            }
        }
        return new Location(world, x, y, z);
    }

    /** A dungeon with the given vault markers and doorways, and nothing else. */
    private Dungeon dungeon(Map<VaultColor, Location> markers, List<EntryPoint> doorways) {
        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getVaultMarkerLocations()).thenReturn(markers);
        when(dungeon.getDoorways()).thenReturn(doorways);
        when(dungeon.getUnusedOpenings()).thenReturn(List.of());
        return dungeon;
    }

    @Test
    void vaultMarkerBlocksAreNotRegisteredAsDoors() {
        Map<VaultColor, Location> markers = new EnumMap<>(VaultColor.class);
        markers.put(VaultColor.BLUE, at(10, 64, 10));
        markers.put(VaultColor.RED, at(20, 64, 20));
        markers.put(VaultColor.GREEN, at(30, 64, 30));
        markers.put(VaultColor.GOLD, at(40, 64, 40));

        DoorManager doorManager = new DoorManager(plugin, gameManager);
        doorManager.initializeDoorsForInstance(dungeon(markers, List.of()));

        for (Map.Entry<VaultColor, Location> entry : markers.entrySet()) {
            assertNull(doorManager.getDoorAt(teamId, entry.getValue()),
                    "No door should be registered at the " + entry.getKey() + " vault marker — VaultManager owns it");
        }
    }

    /** The door map is still populated for real segment connections, so the test above isn't vacuous. */
    @Test
    void segmentConnectionsStillGetADoor() {
        Location entryPointLoc = at(5, 64, 5);

        DoorManager doorManager = new DoorManager(plugin, gameManager);
        doorManager.initializeDoorsForInstance(
                dungeon(Map.of(), List.of(new EntryPoint(entryPointLoc, Direction.NORTH))));

        // The lock sits one block above the entry point marker (eye level).
        Door door = doorManager.getDoorAt(teamId, entryPointLoc.clone().add(0, 1, 0));
        assertNotNull(door, "A segment connection should still produce a door");
        assertInstanceOf(SegmentDoor.class, door, "Segment connections produce SegmentDoors");
    }
}
