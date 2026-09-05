package com.clarkson.sot.scoring;

import com.clarkson.sot.events.BlockProtectionListener;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SoTTeam;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Dispatches real events at a MockBukkit server so {@link BankingManager} runs the way it does in
 * game, with its collaborators mocked the way {@link com.clarkson.sot.events.EscapeListenerTest} does.
 *
 * <p>Two things here are easy to regress. The right-click must be <em>cancelled</em>, or the vanilla
 * ender chest inventory opens over the bank; and the handler must ignore the off-hand pass of
 * {@link PlayerInteractEvent}, or every click banks twice and the second pass overwrites the
 * confirmation with "You have no coins to bank!".
 *
 * <p>A real {@link BlockProtectionListener} is registered alongside, because keeping the bank in the
 * hub is <em>its</em> rule now — {@code ENDER_CHEST} is simply absent from the
 * {@code BreakableBlocks} whitelist. {@link BankingManager} carries no break handler of its own, and
 * the tests at the bottom are what stop one growing back.
 */
class BankingManagerTest {

    private ServerMock server;
    private World world;
    private GameManager gameManager;
    private ScoreManager scoreManager;
    private SoTTeam team;
    private PlayerMock player;
    private Block bankBlock;
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("bank_world");

        gameManager = mock(GameManager.class);
        scoreManager = mock(ScoreManager.class);
        team = mock(SoTTeam.class);
        PlayerStateManager stateManager = mock(PlayerStateManager.class);
        TeamManager teamManager = mock(TeamManager.class);

        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getActiveTeams()).thenReturn(Map.of(teamId, team));

        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(true);

        // The block, not just the registered cell: the whitelist decides by material.
        bankBlock = world.getBlockAt(21, 65, 4);
        bankBlock.setType(Material.ENDER_CHEST);
        when(gameManager.isTeamBankAt(eq(teamId), any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(1);
            return queried != null
                    && queried.getBlockX() == bankBlock.getX()
                    && queried.getBlockY() == bankBlock.getY()
                    && queried.getBlockZ() == bankBlock.getZ();
        });

        server.getPluginManager().registerEvents(new BankingManager(scoreManager, gameManager, plugin), plugin);
        server.getPluginManager().registerEvents(new BlockProtectionListener(gameManager), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void carrying(int coins) {
        when(scoreManager.getPlayerUnbankedScore(player.getUniqueId())).thenReturn(coins);
    }

    private BlockBreakEvent breakBlock(Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent rightClick(Block block, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, (ItemStack) null, block, BlockFace.UP, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    // --- The banking rule itself ---

    @Test
    void banksEightyPercentAndClearsTheUnbankedScore() {
        carrying(100);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(team).addBankedScore(80);
        verify(scoreManager).clearPlayerUnbankedScore(player.getUniqueId());
    }

    @Test
    void roundsTheTaxRatherThanTruncatingIt() {
        // 7 coins: tax rounds to 1 (1.4 -> 1), so 6 are banked.
        carrying(7);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(team).addBankedScore(6);
    }

    @Test
    void banksNothingWhenThePlayerIsCarryingNoCoins() {
        carrying(0);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(team, never()).addBankedScore(anyInt());
        verify(scoreManager, never()).clearPlayerUnbankedScore(any());
    }

    @Test
    void keepsTheCoinsWhenThePlayerHasNoTeamAssignment() {
        // Resolving the team before clearing is what stops the coins vanishing into nothing.
        when(gameManager.getActiveTeams()).thenReturn(Map.of());
        carrying(50);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(scoreManager, never()).clearPlayerUnbankedScore(any());
    }

    // --- Reaching the handler at all ---

    @Test
    void cancelsTheClickSoTheEnderChestNeverOpens() {
        carrying(100);

        PlayerInteractEvent event = rightClick(bankBlock, EquipmentSlot.HAND);

        assertTrue(event.isCancelled(), "the chest is a button, not storage");
    }

    @Test
    void ignoresTheOffHandPassOfTheSameClick() {
        carrying(100);

        rightClick(bankBlock, EquipmentSlot.OFF_HAND);

        verify(team, never()).addBankedScore(anyInt());
    }

    @Test
    void ignoresABlockNextToTheBank() {
        carrying(100);

        PlayerInteractEvent event = rightClick(world.getBlockAt(22, 65, 4), EquipmentSlot.HAND);

        verify(team, never()).addBankedScore(anyInt());
        assertFalse(event.isCancelled());
    }

    @Test
    void doesNothingOutsideARunningGame() {
        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        carrying(100);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(team, never()).addBankedScore(anyInt());
    }

    @Test
    void refusesToBankForAnEscapedPlayer() {
        // Escaping ends the round for that player: coins had to be banked before they left.
        when(gameManager.getPlayerStateManager().getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);
        carrying(100);

        rightClick(bankBlock, EquipmentSlot.HAND);

        verify(team, never()).addBankedScore(anyInt());
    }

    // --- Keeping the bank in the hub ---

    /**
     * The property, pinned where it is now enforced: the whitelist refuses the break, because an
     * ender chest mined without silk touch drops 8 obsidian and takes the team's only banking point
     * out of the round with it. {@link BankingManager} does nothing here — it used to cancel this
     * event itself, which added a chat line on top of the listener's action bar.
     */
    @Test
    void breakingTheBankIsRefusedByTheWhitelist() {
        BlockBreakEvent event = breakBlock(bankBlock);

        assertTrue(event.isCancelled(),
                "an ender chest mined without silk touch drops obsidian and takes the bank with it");
    }

    /**
     * The counterpart to the rule above, and the reason the duplicate guard had to go: Creative is
     * the admin escape hatch out of every block rule, so a bank-specific cancel in
     * {@link BankingManager} contradicted it. The same inconsistency is what removed
     * {@code SandManager}'s timer-column guard.
     */
    @Test
    void anAdminInCreativeCanStillMineTheBank() {
        player.setGameMode(GameMode.CREATIVE);

        assertFalse(breakBlock(bankBlock).isCancelled(),
                "no bank-specific guard may survive the Creative bypass");
    }

    /** Outside a live round the world belongs to the builders, bank block included. */
    @Test
    void theBankIsBreakableBetweenRounds() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);

        assertFalse(breakBlock(bankBlock).isCancelled());
    }

    /** So the protection cannot degenerate into "cancel every break near the bank". */
    @Test
    void leavesDungeonSandNextToTheBankBreakable() {
        Block sand = world.getBlockAt(22, 65, 4);
        sand.setType(Material.SAND);

        assertFalse(breakBlock(sand).isCancelled());
    }
}
