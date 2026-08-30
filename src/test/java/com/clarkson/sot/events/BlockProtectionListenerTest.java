package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SandManager;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Dispatches real block events at a MockBukkit server so {@link BlockProtectionListener} runs as it
 * would in game, with its collaborators mocked the way {@link DeathListenerTest} does.
 *
 * <p>The tests at the bottom register a real {@link SandManager} alongside the listener, because the
 * fix depends on the two running in the right order: the protection listener is at
 * {@code LOW} and {@code SandManager} at {@code NORMAL, ignoreCancelled = true}, so a cancelled
 * break never reaches the payout.
 */
class BlockProtectionListenerTest {

    private ServerMock server;
    private Plugin plugin;
    private World world;
    private GameManager gameManager;
    private TeamManager teamManager;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("block_protection_world");

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        // GameManager builds this in its constructor, so it is never null in production. Stubbed for
        // every test because the placement path resolves the player's team before it can decide
        // whether a placement is a sand deposit.
        teamManager = mock(TeamManager.class);
        when(gameManager.getTeamManager()).thenReturn(teamManager);

        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(true);

        server.getPluginManager().registerEvents(new BlockProtectionListener(gameManager), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block blockAt(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material);
        return block;
    }

    private BlockBreakEvent breakBlock(Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private BlockPlaceEvent placeBlock(Block placed, Block against) {
        BlockPlaceEvent event = new BlockPlaceEvent(placed, placed.getState(), against,
                new ItemStack(Material.DIRT), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Marks a single block location as one of the team's TIMER_DEPOSIT cells. */
    private void depositPointAt(Block block) {
        UUID teamId = UUID.randomUUID();
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);
        when(gameManager.isTeamSandTimerDepositAt(eq(teamId), any(Location.class)))
                .thenAnswer(invocation -> {
                    Location queried = invocation.getArgument(1);
                    return queried != null
                            && queried.getBlockX() == block.getX()
                            && queried.getBlockY() == block.getY()
                            && queried.getBlockZ() == block.getZ();
                });
    }

    /** Marks a single block location as belonging to a live timer column. */
    private void timerColumnAt(Block block) {
        when(gameManager.isVisualTimerBlock(any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(0);
            return queried != null
                    && queried.getBlockX() == block.getX()
                    && queried.getBlockY() == block.getY()
                    && queried.getBlockZ() == block.getZ();
        });
    }

    // --- The whitelist ---------------------------------------------------------------------

    @Test
    void dungeonSandCanStillBeMined() {
        BlockBreakEvent event = breakBlock(blockAt(0, 64, 0, Material.SAND));
        assertFalse(event.isCancelled(), "sand is the timer currency and must stay breakable");
    }

    @Test
    void spawnersCanStillBeBroken() {
        BlockBreakEvent event = breakBlock(blockAt(0, 64, 0, Material.SPAWNER));
        assertFalse(event.isCancelled());
    }

    @Test
    void theDungeonItselfCannotBeMined() {
        assertTrue(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
        assertTrue(breakBlock(blockAt(1, 64, 0, Material.GOLD_BLOCK)).isCancelled(),
                "a vault marker must not be minable, or its key can be skipped");
        assertTrue(breakBlock(blockAt(2, 64, 0, Material.IRON_BARS)).isCancelled(),
                "a death cage must not be minable, or the sand sacrifice can be skipped");
    }

    @Test
    void playersCannotPlaceOrdinaryBlocksDuringARound() {
        Block against = blockAt(0, 64, 0, Material.STONE);
        Block placed = blockAt(0, 65, 0, Material.DIRT);
        assertTrue(placeBlock(placed, against).isCancelled());
    }

    /**
     * Sand is the one block players carry, so letting them place it anywhere would let them pillar
     * over dungeon walls or block the timer column's refill path.
     */
    @Test
    void sandCannotBePlacedJustAnywhere() {
        Block against = blockAt(0, 64, 0, Material.STONE);
        Block placed = blockAt(0, 65, 0, Material.SAND);
        assertTrue(placeBlock(placed, against).isCancelled());
    }

    /**
     * The one placement the game needs. {@code SandManager.onBlockPlace} converts it to time at
     * {@code NORMAL}, so this listener must not cancel it first — that would silently kill the
     * deposit mechanic.
     */
    @Test
    void sandCanStillBeDepositedOnTheTimer() {
        Block deposit = blockAt(21, 102, 18, Material.SAND);
        depositPointAt(deposit);

        Block against = blockAt(21, 101, 18, Material.STONE);
        assertFalse(placeBlock(deposit, against).isCancelled(),
                "depositing sand on a TIMER_DEPOSIT cell is how sand becomes time");
    }

    /**
     * {@code SandManager.onBlockPlace} only runs while RUNNING. Letting a placement through during
     * the countdown would leave a real sand block standing on the deposit cell.
     */
    @Test
    void sandCannotBeDepositedBeforeTheRoundStarts() {
        when(gameManager.getCurrentState()).thenReturn(GameState.COUNTDOWN);
        Block deposit = blockAt(21, 102, 18, Material.SAND);
        depositPointAt(deposit);

        Block against = blockAt(21, 101, 18, Material.STONE);
        assertTrue(placeBlock(deposit, against).isCancelled());
    }

    // --- The timer column ------------------------------------------------------------------

    /**
     * The regression pin for the sand-timer exploit. Mining a column block used to really break it,
     * credit the player +1 sand and +10s, and then have {@code TeamTimer.addSeconds} →
     * {@code syncVisualState} → {@code addSandToTop} put the block straight back — so a player at
     * their own hub column could hold the timer at its maximum forever and bank unlimited revives.
     */
    @Test
    void theTimerColumnCannotBeMined() {
        Block column = blockAt(21, 102, 18, Material.SAND);
        timerColumnAt(column);

        assertTrue(breakBlock(column).isCancelled(),
                "the team's own sand timer must not be minable even though it is made of sand");
    }

    /** The baked hub shaft is standing in the world before the round starts, so it needs cover. */
    @Test
    void theTimerColumnIsProtectedDuringTheCountdownToo() {
        when(gameManager.getCurrentState()).thenReturn(GameState.COUNTDOWN);
        Block column = blockAt(21, 102, 18, Material.SAND);
        timerColumnAt(column);

        assertTrue(breakBlock(column).isCancelled());
    }

    /**
     * The timer check runs before the participant check on purpose, so an operator who teleported
     * into a dungeon cannot mine a team's clock away either.
     */
    @Test
    void theTimerColumnIsProtectedFromNonParticipants() {
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(false);
        Block column = blockAt(21, 102, 18, Material.SAND);
        timerColumnAt(column);

        assertTrue(breakBlock(column).isCancelled());
    }

    // --- Who the whitelist applies to ------------------------------------------------------

    /** Staff building elsewhere in the world while a round runs are not participants. */
    @Test
    void nonParticipantsAreUnaffected() {
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(false);

        assertFalse(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
        Block against = blockAt(0, 64, 0, Material.STONE);
        assertFalse(placeBlock(blockAt(0, 65, 0, Material.DIRT), against).isCancelled());
    }

    @Test
    void creativeModeBypassesTheRestriction() {
        player.setGameMode(GameMode.CREATIVE);
        Block column = blockAt(21, 102, 18, Material.SAND);
        timerColumnAt(column);

        assertFalse(breakBlock(column).isCancelled(), "creative is the admin escape hatch");
        assertFalse(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
        Block against = blockAt(0, 64, 0, Material.STONE);
        assertFalse(placeBlock(blockAt(0, 65, 0, Material.DIRT), against).isCancelled());
    }

    @Test
    void spectatorModeBypassesTheRestriction() {
        player.setGameMode(GameMode.SPECTATOR);
        assertFalse(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
    }

    // --- Which game states it applies in ----------------------------------------------------

    @Test
    void nothingIsRestrictedBeforeARoundStarts() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);

        assertFalse(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled(),
                "builders must be able to work between rounds");
        verify(gameManager, never()).isVisualTimerBlock(any());
    }

    @Test
    void nothingIsRestrictedOnceTheRoundHasEnded() {
        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        assertFalse(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
    }

    @Test
    void aPausedRoundIsStillProtected() {
        when(gameManager.getCurrentState()).thenReturn(GameState.PAUSED);
        assertTrue(breakBlock(blockAt(0, 64, 0, Material.STONE)).isCancelled());
    }

    // --- Ordering against SandManager -------------------------------------------------------

    /**
     * The cross-listener pin. {@link BlockProtectionListener} sits at {@code LOW} specifically so
     * that cancelling makes Bukkit skip {@code SandManager} at {@code NORMAL,
     * ignoreCancelled = true}. If someone raises this listener's priority, or drops
     * {@code ignoreCancelled} from {@code SandManager}, the break is paid out anyway — this test is
     * what catches that.
     */
    @Test
    void miningTheTimerColumnHandsOutNoSand() {
        SandManager sandManager = registerRealSandManager();

        Block column = blockAt(21, 102, 18, Material.SAND);
        timerColumnAt(column);

        BlockBreakEvent event = breakBlock(column);

        assertTrue(event.isCancelled());
        assertEquals(0, sandManager.getPlayerSandCount(player),
                "a denied break must not hand the player any sand");
        // SandManager's first act on a break it accepts is setDropItems(false), so an untouched
        // drop flag is the proof that the cancel at LOW kept the event away from it entirely.
        assertTrue(event.isDropItems(),
                "a cancelled break must never reach SandManager's payout");
    }

    /** The counterpart, so the fix cannot degenerate into "cancel every break". */
    @Test
    void miningDungeonSandStillHandsOutSand() {
        SandManager sandManager = registerRealSandManager();

        BlockBreakEvent event = breakBlock(blockAt(0, 64, 0, Material.SAND));

        assertFalse(event.isCancelled());
        assertFalse(event.isDropItems(), "SandManager suppresses the vanilla drop and hands the sand over");
        assertEquals(1, sandManager.getPlayerSandCount(player),
                "breaking dungeon sand must still hand the player the sand item");
    }

    /** {@code SandManager} needs the player alive in the dungeon before it hands anything out. */
    private SandManager registerRealSandManager() {
        PlayerStateManager stateManager = mock(PlayerStateManager.class);
        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);

        SandManager sandManager = new SandManager(gameManager, plugin);
        server.getPluginManager().registerEvents(sandManager, plugin);
        return sandManager;
    }
}
