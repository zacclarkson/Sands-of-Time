package com.clarkson.sot.dungeon;

import com.clarkson.sot.events.FloorItemManager;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins destroying a mob spawner (#46, PR feedback).
 *
 * <p>Breaking the block with a pickaxe is the <em>only</em> way to end an encounter — the spawner
 * produces waves indefinitely while a player is in range — so this is the release valve for the
 * whole mechanic, and it pays out. The payout matches an ordinary coin stack at the same depth and
 * goes through {@link FloorItemManager} so it inherits the ItemDisplay visual, team-scoped proximity
 * pickup, the batched pickup notifier, and instance cleanup that every other coin already has.
 */
class MobSpawnerBreakTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private GameManager gameManager;
    private TeamManager teamManager;
    private FloorItemManager floorItemManager;
    private MobManager mobManager;
    private PlayerMock player;

    private final AtomicLong now = new AtomicLong(0);
    private final UUID teamId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("mob-break-world");
        plugin = MockBukkit.createMockPlugin();

        player = server.addPlayer();

        teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        floorItemManager = mock(FloorItemManager.class);

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getFloorItemManager()).thenReturn(floorItemManager);

        mobManager = new MobManager(plugin, gameManager, now::get);
        server.getPluginManager().registerEvents(mobManager, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private void armAt(Location location, int depth) {
        mobManager.armSpawner(location, teamId, instanceId, depth);
    }

    /** Fires a BlockBreakEvent for the block at the location, with the given item in hand. */
    private BlockBreakEvent breakWith(Location location, Material heldItem) {
        player.getInventory().setItemInMainHand(heldItem == null ? null : new ItemStack(heldItem));
        BlockBreakEvent event = new BlockBreakEvent(location.getBlock(), player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void breakingWithAPickaxeDropsCoins() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 4);

        BlockBreakEvent event = breakWith(spawner, Material.IRON_PICKAXE);

        assertFalse(event.isCancelled(), "a pickaxe break should be allowed through");
        // Depth 4 -> base 5 + 4/2 = 7, the same as an ordinary coin stack at that depth.
        verify(floorItemManager).spawnCoinStack(any(Location.class), eq(7), eq(teamId), eq(instanceId), eq(4));
    }

    @Test
    void thePayoutScalesWithDepthLikeAnyOtherCoin() {
        armAt(at(100, 64, 100), 0);
        armAt(at(200, 64, 200), 10);

        breakWith(at(100, 64, 100), Material.DIAMOND_PICKAXE);
        breakWith(at(200, 64, 200), Material.DIAMOND_PICKAXE);

        verify(floorItemManager).spawnCoinStack(any(Location.class), eq(5), eq(teamId), eq(instanceId), eq(0));
        verify(floorItemManager).spawnCoinStack(any(Location.class), eq(10), eq(teamId), eq(instanceId), eq(10));
    }

    @Test
    void aBrokenSpawnerStopsProducingMobs() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 0);
        player.teleport(at(100, 64, 96));
        now.addAndGet(1);
        mobManager.tick();
        int duringFight = world.getEntitiesByClass(org.bukkit.entity.Mob.class).size();
        assertEquals(1, duringFight, "precondition: the spawner was running");

        breakWith(spawner, Material.IRON_PICKAXE);
        now.addAndGet(MobManager.SPAWN_INTERVAL_TICKS * 3);
        mobManager.tick();

        assertEquals(duringFight, world.getEntitiesByClass(org.bukkit.entity.Mob.class).size(),
                "breaking the block is what ends the encounter");
        assertEquals(0, mobManager.getActiveSpawnerCount(teamId));
    }

    @Test
    void mobsAlreadySpawnedSurviveTheBreak() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 0);
        player.teleport(at(100, 64, 96));
        now.addAndGet(1);
        mobManager.tick();

        breakWith(spawner, Material.IRON_PICKAXE);

        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Mob.class).size(),
                "destroying the spawner stops new waves; it does not win the fight for you");
    }

    @Test
    void breakingBareHandedIsRefused() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 4);

        BlockBreakEvent event = breakWith(spawner, null);

        assertTrue(event.isCancelled(), "a spawner should not come apart in your hands");
        verify(floorItemManager, never()).spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
        assertEquals(1, mobManager.getActiveSpawnerCount(teamId), "and it must keep running");
    }

    @Test
    void breakingWithTheWrongToolIsRefused() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 4);

        BlockBreakEvent event = breakWith(spawner, Material.IRON_SWORD);

        assertTrue(event.isCancelled());
        verify(floorItemManager, never()).spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
    }

    @Test
    void everyPickaxeTierWorks() {
        for (Material pickaxe : new Material[]{
                Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
                Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE}) {
            assertTrue(MobManager.isPickaxe(new ItemStack(pickaxe)), pickaxe.name());
        }
        assertFalse(MobManager.isPickaxe(new ItemStack(Material.IRON_SHOVEL)));
        assertFalse(MobManager.isPickaxe(null), "an empty hand is not a pickaxe");
    }

    @Test
    void anotherTeamCannotBreakTheSpawner() {
        Location spawner = at(100, 64, 100);
        mobManager.armSpawner(spawner, UUID.randomUUID(), instanceId, 4);

        BlockBreakEvent event = breakWith(spawner, Material.IRON_PICKAXE);

        assertTrue(event.isCancelled(), "one team must not dismantle another team's dungeon");
        verify(floorItemManager, never()).spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
    }

    @Test
    void breakingAnOrdinaryBlockIsUntouched() {
        armAt(at(100, 64, 100), 0);
        at(50, 64, 50).getBlock().setType(Material.STONE);

        BlockBreakEvent event = breakWith(at(50, 64, 50), Material.IRON_PICKAXE);

        assertFalse(event.isCancelled(), "the handler must only claim its own spawner blocks");
        verify(floorItemManager, never()).spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
    }

    @Test
    void aSpawnerCanOnlyBeCashedInOnce() {
        Location spawner = at(100, 64, 100);
        armAt(spawner, 4);

        breakWith(spawner, Material.IRON_PICKAXE);
        breakWith(spawner, Material.IRON_PICKAXE);

        verify(floorItemManager, times(1))
                .spawnCoinStack(any(Location.class), anyInt(), any(), any(), anyInt());
    }

    @Test
    void aFailureToDropCoinsDoesNotBlockTheBreak() {
        doThrow(new IllegalStateException("boom")).when(floorItemManager)
                .spawnCoinStack(any(), anyInt(), any(), any(), anyInt());
        Location spawner = at(100, 64, 100);
        armAt(spawner, 4);

        assertDoesNotThrow(() -> breakWith(spawner, Material.IRON_PICKAXE));
        assertEquals(0, mobManager.getActiveSpawnerCount(teamId),
                "the spawner is still gone even if the payout failed");
    }
}
