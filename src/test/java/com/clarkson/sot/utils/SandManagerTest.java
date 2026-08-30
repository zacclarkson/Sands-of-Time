package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.DeathCage;
import com.clarkson.sot.events.BlockProtectionListener;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.scoring.ScoreManager;
import com.clarkson.sot.timer.TeamTimer;
import com.clarkson.sot.ui.SacrificeIndicatorManager;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Item;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
    private Plugin plugin;
    private World world;
    private GameManager gameManager;
    private PlayerStateManager stateManager;
    private SoTTeam team;
    private PlayerMock player;
    private SandManager sandManager;
    private SacrificeIndicatorManager indicators;
    private ScoreManager scoreManager;
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("sand_world");

        gameManager = mock(GameManager.class);
        stateManager = mock(PlayerStateManager.class);
        TeamManager teamManager = mock(TeamManager.class);
        team = mock(SoTTeam.class);

        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getActiveTeams()).thenReturn(Map.of(teamId, team));
        indicators = mock(SacrificeIndicatorManager.class);
        when(gameManager.getSacrificeIndicatorManager()).thenReturn(indicators);
        scoreManager = mock(ScoreManager.class);
        when(gameManager.getScoreManager()).thenReturn(scoreManager);
        when(team.getTeamName()).thenReturn("Red Rabbits");
        when(team.getRemainingSeconds()).thenReturn(60);

        player = server.addPlayer();
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        sandManager = new SandManager(gameManager, plugin);
        server.getPluginManager().registerEvents(sandManager, plugin);
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
        // The refusal used to live in SandManager itself. It moved to BlockProtectionListener, which
        // covers strictly more (every team's column, the whole live round), so this registers that
        // listener and keeps testing the property rather than the class that used to own it — which
        // also makes it a real check of the priority wiring: the listener cancels at LOW and
        // SandManager, at NORMAL with ignoreCancelled, must never see the event.
        server.getPluginManager().registerEvents(new BlockProtectionListener(gameManager), plugin);
        player.setGameMode(GameMode.SURVIVAL); // Creative deliberately bypasses the protection.

        Block columnBlock = dungeonSand(20, 70, 20);
        when(gameManager.isVisualTimerBlock(any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(0);
            return queried != null
                    && queried.getBlockX() == 20 && queried.getBlockY() == 70 && queried.getBlockZ() == 20;
        });

        BlockBreakEvent event = breakBlock(columnBlock);

        assertTrue(event.isCancelled(), "the timer column would otherwise be an unlimited sand mine");
        assertEquals(0, sandInInventory());
        assertTrue(event.isDropItems(), "a cancelled break must never reach SandManager's payout");
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

    // --- revive: sacrificing sand at a chest ---

    /** A cage holding {@code caged}, priced by {@code deaths}, with its chest at an arbitrary cell. */
    private DeathCage cagedTeammate(PlayerMock caged, int deaths) {
        DeathCage cage = new DeathCage(new Location(world, 30, 65, 4), new Location(world, 30, 65, 6));
        cage.assignPlayer(caged.getUniqueId());
        for (int i = 0; i < deaths; i++) cage.recordDeath();
        when(stateManager.getStatus(caged.getUniqueId())).thenReturn(PlayerStatus.DEAD_AWAITING_REVIVE);
        when(gameManager.getTeamHubLocation(teamId)).thenReturn(new Location(world, 21, 65, 18));
        return cage;
    }

    private int sandCount(PlayerMock p) {
        int total = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.SAND) total += stack.getAmount();
        }
        return total;
    }

    @Test
    void aSingleSandFreesATeammateOnTheirFirstDeath() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        assertTrue(sandManager.attemptRevive(player, cage));

        verify(stateManager).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
        assertEquals(2, sandCount(player), "exactly one sand is spent");
    }

    @Test
    void aPartialPaymentSpendsOneSandAndDoesNotRevive() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 3); // costs 3
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));

        assertFalse(sandManager.attemptRevive(player, cage), "3 sand owed, only 1 paid");

        assertEquals(4, sandCount(player), "one sand per click, not the whole price");
        assertEquals(2, cage.getRemainingSand());
        verify(stateManager, never()).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void theFinalSandOfAnEscalatedPriceCompletesTheRevive() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 3);
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));

        assertFalse(sandManager.attemptRevive(player, cage));
        assertFalse(sandManager.attemptRevive(player, cage));
        assertTrue(sandManager.attemptRevive(player, cage), "the third sand frees them");

        assertEquals(2, sandCount(player), "three sand spent in total");
        verify(stateManager).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void twoTeammatesCanEachChipInOnTheSameRevive() {
        PlayerMock caged = server.addPlayer();
        PlayerMock helper = server.addPlayer();
        when(gameManager.getTeamManager().getPlayerTeamId(helper)).thenReturn(teamId);
        DeathCage cage = cagedTeammate(caged, 2); // costs 2
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));
        helper.getInventory().addItem(new ItemStack(Material.SAND, 1));

        assertFalse(sandManager.attemptRevive(player, cage));
        assertTrue(sandManager.attemptRevive(helper, cage), "the second teammate finishes the job");

        assertEquals(0, sandCount(player));
        assertEquals(0, sandCount(helper));
        verify(stateManager).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void aReviverWithNoSandPaysNothingAndChangesNothing() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 2);

        assertFalse(sandManager.attemptRevive(player, cage));

        assertEquals(0, sandCount(player));
        assertEquals(2, cage.getRemainingSand(), "no progress is banked without sand");
        verify(stateManager, never()).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void aTeammateWhoIsNotAwaitingReviveCannotBeBoughtOut() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        when(stateManager.getStatus(caged.getUniqueId())).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        assertFalse(sandManager.attemptRevive(player, cage));

        assertEquals(3, sandCount(player), "sand is not taken for a revive that cannot happen");
    }

    @Test
    void anUnassignedCageTakesNoSand() {
        DeathCage cage = new DeathCage(new Location(world, 30, 65, 4), new Location(world, 30, 65, 6));
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        assertFalse(sandManager.attemptRevive(player, cage));

        assertEquals(3, sandCount(player));
    }

    @Test
    void aCompletedReviveClearsProgressSoTheNextDeathStartsFresh() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        assertTrue(sandManager.attemptRevive(player, cage));

        assertEquals(0, cage.getSandDeposited());
    }

    // --- revive: the chest itself ---

    /** Registers {@code cage}'s chest as this team's sacrifice point and builds the block. */
    private Block sacrificeChest(DeathCage cage) {
        Location loc = cage.getSacrificePointLocation();
        Block block = world.getBlockAt(loc);
        block.setType(Material.CHEST);
        when(gameManager.isAnySacrificePointAt(any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(0);
            return queried != null && cage.isSacrificePointAt(queried);
        });
        when(gameManager.getDeathCageAtSacrificePoint(eq(teamId), any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(1);
            return (queried != null && cage.isSacrificePointAt(queried)) ? cage : null;
        });
        return block;
    }

    private PlayerInteractEvent rightClick(PlayerMock who, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                who, Action.RIGHT_CLICK_BLOCK, who.getInventory().getItemInMainHand(),
                block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void rightClickingTheSacrificeChestPaysTowardTheRevive() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        Block chest = sacrificeChest(cage);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, chest);

        assertTrue(event.isCancelled(), "the vanilla chest UI must never open on a sacrifice point");
        assertEquals(1, sandCount(player), "one sand was sacrificed");
        verify(stateManager).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void anOrdinaryChestIsLeftAlone() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        sacrificeChest(cage);
        Block plainChest = world.getBlockAt(new Location(world, 40, 65, 40));
        plainChest.setType(Material.CHEST);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, plainChest);

        assertFalse(event.isCancelled(), "a chest that is not a sacrifice point still opens normally");
        assertEquals(2, sandCount(player));
    }

    @Test
    void someoneWithNoTeamCannotOpenASacrificeChest() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        Block chest = sacrificeChest(cage);
        PlayerMock outsider = server.addPlayer();
        when(gameManager.getTeamManager().getPlayerTeamId(outsider)).thenReturn(null);
        outsider.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(outsider, chest);

        assertTrue(event.isCancelled(), "cancelled before the team check, so the chest stays shut");
        assertEquals(2, sandCount(outsider), "and they pay nothing");
        verify(stateManager, never()).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
    }

    @Test
    void sacrificeChestsAreInertOutsideARunningGame() {
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        Block chest = sacrificeChest(cage);
        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, chest);

        assertFalse(event.isCancelled());
        assertEquals(2, sandCount(player));
    }

    @Test
    void theOffHandPassDoesNotChargeASecondSand() {
        // PlayerInteractEvent fires once per hand and cancelling does not stop the second pass, so a
        // single right-click must still cost exactly one sand.
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 3); // costs 3, so neither pass completes it
        Block chest = sacrificeChest(cage);
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));

        rightClick(player, chest);
        PlayerInteractEvent offHand = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInOffHand(),
                chest, BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(offHand);

        assertEquals(4, sandCount(player), "one right-click, one sand");
        assertEquals(2, cage.getRemainingSand());
        assertTrue(offHand.isCancelled(), "the off-hand pass must still not open the chest");
    }

    // --- sand trade: buying coins with sand out in the branches ---

    /**
     * Registers {@code cell} as this team's only sand trade chest, sitting at {@code depth}, and
     * builds the block.
     */
    private Block tradeChest(Location cell, int depth) {
        Block block = world.getBlockAt(cell);
        block.setType(Material.CHEST);
        when(gameManager.isAnySandTradePointAt(any(Location.class))).thenAnswer(invocation ->
                sameBlock(invocation.getArgument(0), cell));
        when(gameManager.isTeamSandTradePointAt(eq(teamId), any(Location.class))).thenAnswer(invocation ->
                sameBlock(invocation.getArgument(1), cell));
        when(gameManager.getTeamDepthAt(eq(teamId), any(Location.class))).thenReturn(depth);
        return block;
    }

    private static boolean sameBlock(Location queried, Location cell) {
        return queried != null
                && queried.getBlockX() == cell.getBlockX()
                && queried.getBlockY() == cell.getBlockY()
                && queried.getBlockZ() == cell.getBlockZ();
    }

    @Test
    void rightClickingATradeChestSpendsOneSandForDepthScaledCoins() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 8);
        player.getInventory().addItem(new ItemStack(Material.SAND, 3));

        PlayerInteractEvent event = rightClick(player, chest);

        assertTrue(event.isCancelled(), "the vanilla chest UI must never open on a trade point");
        assertEquals(2, sandCount(player), "exactly one sand is spent");
        verify(scoreManager).awardDepthScaledCoins(player, SandManager.TRADE_COINS_PER_SAND, 8);
    }

    @Test
    void aTradeAtTheHubPaysTheUnscaledRate() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 0);
        player.getInventory().addItem(new ItemStack(Material.SAND, 1));

        rightClick(player, chest);

        verify(scoreManager).awardDepthScaledCoins(player, SandManager.TRADE_COINS_PER_SAND, 0);
    }

    @Test
    void aTraderWithNoSandPaysNothingAndEarnsNothing() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 4);

        PlayerInteractEvent event = rightClick(player, chest);

        assertTrue(event.isCancelled(), "the chest still must not open");
        assertEquals(0, sandCount(player));
        verify(scoreManager, never()).awardDepthScaledCoins(any(), anyInt(), anyInt());
    }

    @Test
    void theOffHandPassDoesNotChargeASecondSandAtATradeChest() {
        // Same trap as the sacrifice chest: PlayerInteractEvent fires once per hand, and cancelling
        // the main-hand pass does not stop the off-hand one.
        Block chest = tradeChest(new Location(world, 60, 65, 60), 2);
        player.getInventory().addItem(new ItemStack(Material.SAND, 5));

        rightClick(player, chest);
        PlayerInteractEvent offHand = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInOffHand(),
                chest, BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(offHand);

        assertEquals(4, sandCount(player), "one right-click, one sand");
        verify(scoreManager, times(1)).awardDepthScaledCoins(any(), anyInt(), anyInt());
        assertTrue(offHand.isCancelled(), "the off-hand pass must still not open the chest");
    }

    @Test
    void someoneWithNoTeamCannotOpenATradeChest() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 3);
        PlayerMock outsider = server.addPlayer();
        when(gameManager.getTeamManager().getPlayerTeamId(outsider)).thenReturn(null);
        outsider.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(outsider, chest);

        assertTrue(event.isCancelled(), "cancelled before the team check, so the chest stays shut");
        assertEquals(2, sandCount(outsider), "and they pay nothing");
        verify(scoreManager, never()).awardDepthScaledCoins(any(), anyInt(), anyInt());
    }

    @Test
    void anotherTeamsTradeChestTakesNoSand() {
        // Cancelled for everyone (so it never opens), but only the owning team can actually trade.
        Block chest = tradeChest(new Location(world, 60, 65, 60), 3);
        when(gameManager.isTeamSandTradePointAt(eq(teamId), any(Location.class))).thenReturn(false);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, chest);

        assertTrue(event.isCancelled());
        assertEquals(2, sandCount(player));
        verify(scoreManager, never()).awardDepthScaledCoins(any(), anyInt(), anyInt());
    }

    @Test
    void anEscapedPlayerCannotTrade() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 3);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        rightClick(player, chest);

        assertEquals(2, sandCount(player), "the round is over for them");
        verify(scoreManager, never()).awardDepthScaledCoins(any(), anyInt(), anyInt());
    }

    @Test
    void tradeChestsAreInertOutsideARunningGame() {
        Block chest = tradeChest(new Location(world, 60, 65, 60), 3);
        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, chest);

        assertFalse(event.isCancelled());
        assertEquals(2, sandCount(player));
    }

    @Test
    void aChestThatIsBothWouldBeTreatedAsASacrificePoint() {
        // The two kinds are the same block, so the handler has to pick one meaning. Reviving a caged
        // teammate is the one that must win: it is the only one with a deadline.
        PlayerMock caged = server.addPlayer();
        DeathCage cage = cagedTeammate(caged, 1);
        Block chest = sacrificeChest(cage);
        tradeChest(cage.getSacrificePointLocation(), 5);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        rightClick(player, chest);

        verify(stateManager).updateStatus(caged, PlayerStatus.ALIVE_IN_DUNGEON);
        verify(scoreManager, never()).awardDepthScaledCoins(any(), anyInt(), anyInt());
    }

    @Test
    void anOrdinaryChestIsStillLeftAloneWhenTradePointsExist() {
        tradeChest(new Location(world, 60, 65, 60), 3);
        Block plainChest = world.getBlockAt(new Location(world, 40, 65, 40));
        plainChest.setType(Material.CHEST);
        player.getInventory().addItem(new ItemStack(Material.SAND, 2));

        PlayerInteractEvent event = rightClick(player, plainChest);

        assertFalse(event.isCancelled(), "a chest that is neither kind still opens normally");
        assertEquals(2, sandCount(player));
    }
}
