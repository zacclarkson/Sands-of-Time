package com.clarkson.sot.dungeon;

import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.main.SoT;
import com.clarkson.sot.utils.ItemManager;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the one seam between the two managers that own a vault: {@link VaultManager} owns the marker
 * click end to end (bug #65), and {@link DoorManager} owns the wall behind it.
 *
 * <p>There is no second keyhole and no second key -- opening the vault is what opens its door -- so
 * this hand-off is the <em>only</em> thing that ever opens a vault door.
 */
class VaultManagerVaultDoorHookTest {

    private ServerMock server;
    private World world;
    private SoT plugin;
    private DoorManager doorManager;
    private VaultManager vaultManager;
    private FloorItemManager floorItemManager;
    private Player player;

    private final UUID teamId = UUID.randomUUID();
    private Location blueMarker;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("vault-hook-world");

        plugin = mock(SoT.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("VaultManagerVaultDoorHookTest"));
        // Key items are tagged through a real plugin's NamespacedKey, so ItemManager needs one.
        Plugin keyPlugin = MockBukkit.createMockPlugin();
        ItemManager.initializeKeys(keyPlugin);

        player = server.addPlayer();
        blueMarker = new Location(world, 5, 64, 5);
        blueMarker.getBlock().setType(Material.BLUE_CONCRETE);

        doorManager = mock(DoorManager.class);
        TeamManager teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);
        when(teamManager.getTeamMemberUUIDs(teamId)).thenReturn(Set.of(player.getUniqueId()));

        Map<VaultColor, Location> markers = new EnumMap<>(VaultColor.class);
        markers.put(VaultColor.BLUE, blueMarker);
        Dungeon dungeon = mock(Dungeon.class);
        when(dungeon.getTeamId()).thenReturn(teamId);
        when(dungeon.getVaultMarkerLocations()).thenReturn(markers);
        DungeonManager dungeonManager = mock(DungeonManager.class);
        when(dungeonManager.getDungeonData()).thenReturn(dungeon);

        GameManager gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getDoorManager()).thenReturn(doorManager);
        when(gameManager.getTeamDungeonManager(teamId)).thenReturn(dungeonManager);
        floorItemManager = mock(FloorItemManager.class);
        when(gameManager.getFloorItemManager()).thenReturn(floorItemManager);

        vaultManager = new VaultManager(plugin, gameManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void rightClickMarker(ItemStack inHand) {
        player.getInventory().setItemInMainHand(inHand);
        Block block = blueMarker.getBlock();
        vaultManager.onPlayerInteract(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, inHand, block, BlockFace.NORTH));
    }

    @Test
    void openingAVaultSinksItsDoorAndTheMarkerWithIt() {
        rightClickMarker(ItemManager.createVaultKey(VaultColor.BLUE));

        // The marker goes down with the wall: leaving it hanging where the wall used to be reads as
        // a bug, and the reward is whatever the segment puts behind the wall.
        verify(doorManager).openVaultDoors(teamId, VaultColor.BLUE, blueMarker.getBlock().getLocation());
    }

    @Test
    void openingAVaultSpawnsNoCoinsAtTheMarker() {
        rightClickMarker(ItemManager.createVaultKey(VaultColor.BLUE));

        // Rewards used to be scattered around the marker -- in front of the wall the vault was meant
        // to be sealing. They now sit behind the door and are revealed when it drops.
        verify(floorItemManager, never()).spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
    }

    @Test
    void aKeyOfTheWrongColourOpensNoDoor() {
        rightClickMarker(ItemManager.createVaultKey(VaultColor.RED));

        verify(doorManager, never()).openVaultDoors(any(), any(), any());
    }

    @Test
    void anEmptyHandOpensNoDoor() {
        rightClickMarker(null);

        verify(doorManager, never()).openVaultDoors(any(), any(), any());
    }

    @Test
    void aSecondClickOnAnAlreadyOpenVaultDoesNotReopenTheDoor() {
        rightClickMarker(ItemManager.createVaultKey(VaultColor.BLUE));
        rightClickMarker(ItemManager.createVaultKey(VaultColor.BLUE));

        verify(doorManager, times(1)).openVaultDoors(eq(teamId), eq(VaultColor.BLUE), any());
    }
}
