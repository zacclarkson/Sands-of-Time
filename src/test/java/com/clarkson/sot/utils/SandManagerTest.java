package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.timer.TeamTimer;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Dispatches real block events at a MockBukkit server so {@link SandManager} runs as it would in
 * game, with its collaborators mocked the way {@link com.clarkson.sot.events.EscapeListenerTest} does.
 *
 * <p>The headline behaviour under test is the split that gives this change its reason to exist:
 * breaking sand yields an <em>item</em> and no time, and time is only added when that sand is placed
 * on a timer deposit point.
 */
class SandManagerTest {

    private ServerMock server;
    private World world;
    private GameManager gameManager;
    private PlayerStateManager stateManager;
    private SoTTeam team;
    private PlayerMock player;
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("sand_world");

        gameManager = mock(GameManager.class);
        stateManager = mock(PlayerStateManager.class);
        TeamManager teamManager = mock(TeamManager.class);
        team = mock(SoTTeam.class);

        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getActiveTeams()).thenReturn(Map.of(teamId, team));
        when(team.getTeamName()).thenReturn("Red Rabbits");
        when(team.getRemainingSeconds()).thenReturn(60);

        player = server.addPlayer();
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        server.getPluginManager().registerEvents(new SandManager(gameManager, plugin), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- helpers ---

    /** Marks a single cell as the team's only sand deposit point. */
    private void depositAt(Location deposit) {
        when(gameManager.isTeamSandTimerDepositAt(eq(teamId), any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(1);
            return queried != null
                    && queried.getBlockX() == deposit.getBlockX()
                    && queried.getBlockY() == deposit.getBlockY()
                    && queried.getBlockZ() == deposit.getBlockZ();
        });
    }

    private BlockBreakEvent breakBlock(Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /**
     * Fires a sand placement at {@code cell}, held in the given hand. The block is set to SAND before
     * the event is dispatched, as CraftBukkit does: the block is provisionally placed and only rolled
     * back if a listener cancels, so {@code getBlockPlaced().getType()} is already SAND in a handler.
     */
    private BlockPlaceEvent placeSand(Location cell, EquipmentSlot hand) {
        Block placed = world.getBlockAt(cell);
        BlockState replaced = placed.getState();
        placed.setType(Material.SAND);
        Block against = world.getBlockAt(cell.getBlockX(), cell.getBlockY() - 1, cell.getBlockZ());
        BlockPlaceEvent event = new BlockPlaceEvent(
                placed, replaced, against, new ItemStack(Material.SAND), player, true, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private int sandInInventory() {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.SAND) total += item.getAmount();
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SAND) total += offHand.getAmount();
        return total;
    }

    /**
     * Builds a real death event for the test player. The trailing boolean is required from Paper 26 —
     * see the same note in {@link com.clarkson.sot.events.DeathListenerTest}.
     */
    private PlayerDeathEvent deathEvent(boolean keepInventory) {
        PlayerDeathEvent event = new PlayerDeathEvent(player, mock(DamageSource.class),
                new ArrayList<ItemStack>(), 0, 0, 0, 0, Component.text("died"), true);
        event.setKeepInventory(keepInventory);
        return event;
    }

    /** Every sand stack lying on the ground in the test world. */
    private List<ItemStack> droppedSandStacks() {
        return world.getEntitiesByClass(Item.class).stream()
                .map(Item::getItemStack)
                .filter(item -> item.getType() == Material.SAND)
                .toList();
    }

    private static int totalOf(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private Block dungeonSand(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.SAND);
        return block;
    }

    // --- break path ---

    @Test
    void breakingSandGivesAnItemAndNoTime() {
        BlockBreakEvent event = breakBlock(dungeonSand(5, 64, 5));

        assertEquals(1, sandInInventory(), "the sand must end up in the inventory");
        assertFalse(event.isDropItems(), "the normal drop is suppressed; the item is handed over directly");
        verify(team, never()).addSeconds(anyInt());
    }

    @Test
    void breakingSandOutsideARunningGameDoesNothing() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);

        breakBlock(dungeonSand(5, 64, 5));

        assertEquals(0, sandInInventory());
        verify(team, never()).addSeconds(anyInt());
    }

    @Test
    void breakingSandAfterEscapingDoesNothing() {
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);

        breakBlock(dungeonSand(5, 64, 5));

        assertEquals(0, sandInInventory());
    }

    @Test
    void breakingANonSandBlockDoesNothing() {
        Block stone = world.getBlockAt(5, 64, 5);
        stone.setType(Material.STONE);

        breakBlock(stone);

        assertEquals(0, sandInInventory());
    }

    @Test
    void miningTheTeamsOwnTimerColumnIsBlocked() {
        Block columnBlock = dungeonSand(20, 70, 20);
        when(team.isVisualTimerBlock(any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(0);
            return queried.getBlockX() == 20 && queried.getBlockY() == 70 && queried.getBlockZ() == 20;
        });

        BlockBreakEvent event = breakBlock(columnBlock);

        assertTrue(event.isCancelled(), "the timer column would otherwise be an unlimited sand mine");
        assertEquals(0, sandInInventory());
    }

    // --- deposit path ---

    @Test
    void placingSandOnADepositPointBanksTenSeconds() {
        Location deposit = new Location(world, 10, 64, 10);
        depositAt(deposit);
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        BlockPlaceEvent event = placeSand(deposit, EquipmentSlot.HAND);

        assertTrue(event.isCancelled(), "the deposit cell must never hold a sand block");
        verify(team).addSeconds(SandManager.SECONDS_PER_SAND);
        assertEquals(2, sandInInventory(), "exactly one sand is spent");
    }

    @Test
    void placingSandAwayFromADepositPointBanksNothing() {
        depositAt(new Location(world, 10, 64, 10));
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));

        BlockPlaceEvent event = placeSand(new Location(world, 11, 64, 10), EquipmentSlot.HAND);

        assertFalse(event.isCancelled(), "ordinary building elsewhere is untouched");
        verify(team, never()).addSeconds(anyInt());
        assertEquals(1, sandInInventory());
    }

    @Test
    void aDepositAtTheTimerCapIsRefusedAndKeepsTheSand() {
        Location deposit = new Location(world, 10, 64, 10);
        depositAt(deposit);
        when(team.getRemainingSeconds()).thenReturn(TeamTimer.DEFAULT_MAX_TIMER_SECONDS);
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));

        BlockPlaceEvent event = placeSand(deposit, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        verify(team, never()).addSeconds(anyInt());
        assertEquals(1, sandInInventory(),
                "addSeconds clamps at the cap, so accepting the deposit would destroy the sand for nothing");
    }

    @Test
    void depositingFromTheOffHandWorks() {
        Location deposit = new Location(world, 10, 64, 10);
        depositAt(deposit);
        player.getInventory().setItemInOffHand(new ItemStack(Material.SAND, 2));

        placeSand(deposit, EquipmentSlot.OFF_HAND);

        verify(team).addSeconds(SandManager.SECONDS_PER_SAND);
        assertEquals(1, sandInInventory());
    }

    @Test
    void anEscapedPlayerCannotDeposit() {
        Location deposit = new Location(world, 10, 64, 10);
        depositAt(deposit);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));

        placeSand(deposit, EquipmentSlot.HAND);

        verify(team, never()).addSeconds(anyInt());
        assertEquals(1, sandInInventory());
    }

    @Test
    void depositingOutsideARunningGameDoesNothing() {
        Location deposit = new Location(world, 10, 64, 10);
        depositAt(deposit);
        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));

        BlockPlaceEvent event = placeSand(deposit, EquipmentSlot.HAND);

        assertFalse(event.isCancelled());
        verify(team, never()).addSeconds(anyInt());
        assertEquals(1, sandInInventory());
    }

    // --- carried sand accounting ---

    @Test
    void sandCountSeesBothStorageAndTheOffHand() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        player.getInventory().addItem(new ItemStack(Material.SAND, 4));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SAND, 2));

        assertEquals(6, sandManager.getPlayerSandCount(player));
    }

    @Test
    void clearingSandLeavesOtherItemsAlone() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 2));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SAND, 3));

        sandManager.clearSandItems(Set.of(player.getUniqueId()));

        assertEquals(0, sandManager.getPlayerSandCount(player), "undeposited sand must not cross rounds");
        assertTrue(player.getInventory().contains(Material.DIAMOND),
                "/sot end can fire mid-round, so only sand is taken");
    }

    @Test
    void clearingSandSkipsOfflinePlayers() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());

        assertDoesNotThrow(() -> sandManager.clearSandItems(Set.of(UUID.randomUUID())));
    }

    // --- death ---

    @Test
    void aDeathThatKeepsTheInventoryStillDropsTheSand() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        Location deathSpot = new Location(world, 5, 64, 5);
        player.teleport(deathSpot);
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SAND, 2));

        int dropped = sandManager.dropCarriedSandOnDeath(deathEvent(true));

        assertEquals(7, dropped);
        assertEquals(0, sandInInventory(), "keepInventory must not keep the sand — that is a game rule");
        assertEquals(7, totalOf(droppedSandStacks()), "all of it lands on the floor instead");
        assertTrue(world.getEntitiesByClass(Item.class).stream()
                        .allMatch(item -> item.getLocation().distance(deathSpot) < 2),
                "the pile belongs at the death location, where the corpse run goes");
    }

    @Test
    void anOrdinaryDeathLeavesTheSandToVanilla() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        player.teleport(new Location(world, 5, 64, 5));
        player.getInventory().addItem(new ItemStack(Material.SAND, 4));

        int dropped = sandManager.dropCarriedSandOnDeath(deathEvent(false));

        assertEquals(0, dropped, "the server is already about to drop the whole inventory");
        assertEquals(4, sandInInventory(),
                "taking it here as well would drop each sand twice — the server empties the inventory itself");
        assertTrue(droppedSandStacks().isEmpty());
    }

    @Test
    void aDeathCarryingNoSandDropsNothing() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        player.teleport(new Location(world, 5, 64, 5));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));

        assertEquals(0, sandManager.dropCarriedSandOnDeath(deathEvent(true)));

        assertTrue(droppedSandStacks().isEmpty());
        assertTrue(player.getInventory().contains(Material.DIAMOND),
                "the rest of the inventory is the gamerule's business, not ours");
    }

    @Test
    void moreSandThanOneStackDropsAsSeveralStacks() {
        SandManager sandManager = new SandManager(gameManager, MockBukkit.createMockPlugin());
        player.teleport(new Location(world, 5, 64, 5));
        player.getInventory().addItem(new ItemStack(Material.SAND, 64));
        player.getInventory().addItem(new ItemStack(Material.SAND, 30));

        int dropped = sandManager.dropCarriedSandOnDeath(deathEvent(true));

        assertEquals(94, dropped);
        List<ItemStack> stacks = droppedSandStacks();
        assertEquals(94, totalOf(stacks));
        assertTrue(stacks.stream().allMatch(stack -> stack.getAmount() <= Material.SAND.getMaxStackSize()),
                "an over-sized ItemStack is not something the world will accept");
    }
}
