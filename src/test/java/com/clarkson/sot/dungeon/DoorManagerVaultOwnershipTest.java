package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;
import com.clarkson.sot.dungeon.segment.EntryPoint;
import com.clarkson.sot.dungeon.segment.PlacedSegment;
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
    private DungeonManager dungeonManager;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("door-test-world");
        teamId = UUID.randomUUID();

        plugin = mock(SoT.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DoorManagerVaultOwnershipTest"));

        dungeonManager = mock(DungeonManager.class);
        gameManager = mock(GameManager.class);
        when(gameManager.getTeamDungeonManager(teamId)).thenReturn(dungeonManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** A dungeon with one vault marker per colour and nothing else. */
    private Dungeon dungeonWithVaultMarkers(Map<VaultColor, Location> markers) {
        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getVaultMarkerLocations()).thenReturn(markers);
        return dungeon;
    }

    @Test
    void vaultMarkerBlocksAreNotRegisteredAsDoors() {
        Map<VaultColor, Location> markers = new EnumMap<>(VaultColor.class);
        markers.put(VaultColor.BLUE, at(10, 64, 10));
        markers.put(VaultColor.RED, at(20, 64, 20));
        markers.put(VaultColor.GREEN, at(30, 64, 30));
        markers.put(VaultColor.GOLD, at(40, 64, 40));
        when(dungeonManager.getPlacedSegmentsInWorld()).thenReturn(List.of());

        DoorManager doorManager = new DoorManager(plugin, gameManager);
        doorManager.initializeDoorsForInstance(dungeonWithVaultMarkers(markers));

        for (Map.Entry<VaultColor, Location> entry : markers.entrySet()) {
            assertNull(doorManager.getDoorAt(teamId, entry.getValue()),
                    "No door should be registered at the " + entry.getKey() + " vault marker — VaultManager owns it");
        }
    }

    /** The door map is still populated for real segment connections, so the test above isn't vacuous. */
    @Test
    void segmentConnectionsStillGetADoor() {
        Location entryPointLoc = at(5, 64, 5);
        PlacedSegment segment = mock(PlacedSegment.class);
        when(segment.getAbsoluteEntryPoints()).thenReturn(List.of(new EntryPoint(entryPointLoc, Direction.NORTH)));
        when(dungeonManager.getPlacedSegmentsInWorld()).thenReturn(List.of(segment));

        DoorManager doorManager = new DoorManager(plugin, gameManager);
        doorManager.initializeDoorsForInstance(dungeonWithVaultMarkers(Map.of()));

        // The lock sits one block above the entry point marker (eye level).
        Door door = doorManager.getDoorAt(teamId, entryPointLoc.clone().add(0, 1, 0));
        assertNotNull(door, "A segment connection should still produce a door");
        assertInstanceOf(SegmentDoor.class, door, "Segment connections produce SegmentDoors");
    }
}
