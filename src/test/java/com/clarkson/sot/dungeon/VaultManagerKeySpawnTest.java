package com.clarkson.sot.dungeon;

import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.SoT;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins how {@link VaultManager} spawns vault keys.
 *
 * <p>Keys used to be dropped with {@code World.dropItemNaturally}. That gave them the drop's random
 * velocity (so a key slid off the block the builder marked), let any team walk over them, and let
 * them despawn after the vanilla five minutes — often before the round even started, since dungeons
 * are generated at {@code /sot setup} but only go live at {@code /sot start}. They are now spawned
 * through {@link FloorItemManager} as tracked {@link com.clarkson.sot.entities.Key} floor items, so
 * they share the ItemDisplay visual, the team-scoped proximity pickup, and the instance cleanup that
 * coins and floor loot already use.
 */
class VaultManagerKeySpawnTest {

    private ServerMock server;
    private World world;
    private SoT plugin;
    private GameManager gameManager;
    private FloorItemManager floorItemManager;
    private VaultManager vaultManager;

    private final UUID teamId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("vault-key-test-world");

        plugin = mock(SoT.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("VaultManagerKeySpawnTest"));

        floorItemManager = mock(FloorItemManager.class);
        gameManager = mock(GameManager.class);
        when(gameManager.getFloorItemManager()).thenReturn(floorItemManager);

        vaultManager = new VaultManager(plugin, gameManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A dungeon with a key spawn for every colour and no vault markers (block placement is not under test). */
    private Dungeon dungeonWithKeysForEveryColour() {
        Map<VaultColor, Location> keySpawns = new EnumMap<>(VaultColor.class);
        int x = 0;
        for (VaultColor color : VaultColor.values()) {
            keySpawns.put(color, new Location(world, x += 10, 64, 10));
        }

        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getInstanceId()).thenReturn(instanceId);
        when(dungeon.getVaultMarkerLocations()).thenReturn(Map.of());
        when(dungeon.getKeySpawnLocations()).thenReturn(keySpawns);
        return dungeon;
    }

    @Test
    void everyKeySpawnBecomesATrackedFloorItem() {
        Dungeon dungeon = dungeonWithKeysForEveryColour();
        Map<VaultColor, Location> expected = dungeon.getKeySpawnLocations();

        vaultManager.initializeForInstance(dungeon);
        server.getScheduler().performTicks(1L); // in case the spawn was deferred to the main thread

        for (VaultColor color : VaultColor.values()) {
            verify(floorItemManager).spawnKey(eq(expected.get(color)), eq(color), eq(teamId), eq(instanceId), anyInt());
        }
        verifyNoMoreInteractions(floorItemManager);
    }

    /**
     * The regression itself. A dropped Item is unscoped, drifts, and despawns; nothing in the key
     * path may create one.
     */
    @Test
    void noDroppedItemEntityIsCreated() {
        vaultManager.initializeForInstance(dungeonWithKeysForEveryColour());
        server.getScheduler().performTicks(1L);

        assertTrue(world.getEntitiesByClass(Item.class).isEmpty(),
                "keys must be ItemDisplay floor items, not dropped Item entities");
    }

    @Test
    void keySpawnsAreScopedToTheOwningTeamAndInstance() {
        vaultManager.initializeForInstance(dungeonWithKeysForEveryColour());
        server.getScheduler().performTicks(1L);

        verify(floorItemManager, times(VaultColor.values().length))
                .spawnKey(any(Location.class), any(VaultColor.class), eq(teamId), eq(instanceId), anyInt());
    }

    @Test
    void aKeySpawnInAnUnloadedWorldIsSkippedRatherThanThrowing() {
        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getInstanceId()).thenReturn(instanceId);
        when(dungeon.getVaultMarkerLocations()).thenReturn(Map.of());
        Map<VaultColor, Location> keySpawns = new EnumMap<>(VaultColor.class);
        keySpawns.put(VaultColor.RED, new Location(null, 0, 64, 0));
        when(dungeon.getKeySpawnLocations()).thenReturn(keySpawns);

        assertDoesNotThrow(() -> vaultManager.initializeForInstance(dungeon));
        verify(floorItemManager, never()).spawnKey(any(), any(), any(), any(), anyInt());
    }
}
