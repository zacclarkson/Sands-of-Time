package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * Dispatches real {@link PlayerInteractEvent}s at a MockBukkit server so {@link EscapeListener} runs
 * as it would in game, with its collaborators mocked the way {@link DeathListenerTest} does.
 *
 * <p>The two paths under test are the SAFE_EXIT marker path and the legacy fallback used by segment
 * templates that predate the marker.
 */
class EscapeListenerTest {

    private ServerMock server;
    private World world;
    private GameManager gameManager;
    private PlayerStateManager stateManager;
    private PlayerMock player;
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("escape_world");

        gameManager = mock(GameManager.class);
        stateManager = mock(PlayerStateManager.class);
        TeamManager teamManager = mock(TeamManager.class);
        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(gameManager.getTeamManager()).thenReturn(teamManager);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);

        player = server.addPlayer();
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        server.getPluginManager().registerEvents(new EscapeListener(gameManager), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Marks a single block as the team's safe exit; every other block is not the exit. */
    private void safeExitAt(Location exit) {
        when(gameManager.hasTeamSafeExit(teamId)).thenReturn(true);
        when(gameManager.isTeamSafeExitAt(eq(teamId), any(Location.class))).thenAnswer(invocation -> {
            Location queried = invocation.getArgument(1);
            return queried != null
                    && queried.getBlockX() == exit.getBlockX()
                    && queried.getBlockY() == exit.getBlockY()
                    && queried.getBlockZ() == exit.getBlockZ();
        });
    }

    private void rightClick(Block block) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, (ItemStack) null, block, BlockFace.UP));
    }

    @Test
    void escapesWhenClickingTheMarkedBlock() {
        Block exitBlock = world.getBlockAt(10, 64, 10);
        safeExitAt(exitBlock.getLocation());

        rightClick(exitBlock);

        verify(gameManager).handlePlayerLeave(player);
    }

    @Test
    void escapesWhenClickingTheBlockBeneathTheMarker() {
        // The builder tool records the air block in front of the clicked face, so a marker placed on
        // the exit block's top face sits one block above the block a player actually clicks.
        Block exitBlock = world.getBlockAt(10, 64, 10);
        safeExitAt(world.getBlockAt(10, 65, 10).getLocation());

        rightClick(exitBlock);

        verify(gameManager).handlePlayerLeave(player);
    }

    @Test
    void ignoresABlockNextToTheSafeExit() {
        safeExitAt(world.getBlockAt(10, 64, 10).getLocation());

        rightClick(world.getBlockAt(11, 64, 10));

        verify(gameManager, never()).handlePlayerLeave(any());
    }

    @Test
    void ignoresADecorativePortalFrameOnceASafeExitIsDefined() {
        // The old behaviour escaped on any End Portal Frame near the hub, so a decorative one in the
        // hub schematic permanently ejected whoever right-clicked it.
        safeExitAt(world.getBlockAt(10, 64, 10).getLocation());
        Block decorative = world.getBlockAt(20, 64, 20);
        decorative.setType(Material.END_PORTAL_FRAME);

        rightClick(decorative);

        verify(gameManager, never()).handlePlayerLeave(any());
    }

    @Test
    void fallsBackToThePortalFrameNearTheHubWhenNoSafeExitIsDefined() {
        when(gameManager.hasTeamSafeExit(teamId)).thenReturn(false);
        when(gameManager.getTeamHubLocation(teamId)).thenReturn(new Location(world, 10, 64, 10));
        Block frame = world.getBlockAt(15, 64, 10);
        frame.setType(Material.END_PORTAL_FRAME);

        rightClick(frame);

        verify(gameManager).handlePlayerLeave(player);
    }

    @Test
    void fallbackIgnoresAPortalFrameFarFromTheHub() {
        when(gameManager.hasTeamSafeExit(teamId)).thenReturn(false);
        when(gameManager.getTeamHubLocation(teamId)).thenReturn(new Location(world, 10, 64, 10));
        Block frame = world.getBlockAt(200, 64, 200);
        frame.setType(Material.END_PORTAL_FRAME);

        rightClick(frame);

        verify(gameManager, never()).handlePlayerLeave(any());
    }

    @Test
    void ignoresInteractionWhenTheGameIsNotRunning() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);
        Block exitBlock = world.getBlockAt(10, 64, 10);
        safeExitAt(exitBlock.getLocation());

        rightClick(exitBlock);

        verify(gameManager, never()).handlePlayerLeave(any());
    }

    @Test
    void ignoresPlayersWhoAreNotAliveInTheDungeon() {
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);
        Block exitBlock = world.getBlockAt(10, 64, 10);
        safeExitAt(exitBlock.getLocation());

        rightClick(exitBlock);

        verify(gameManager, never()).handlePlayerLeave(any());
    }

    @Test
    void cancelsTheInteractionOnEscape() {
        Block exitBlock = world.getBlockAt(10, 64, 10);
        safeExitAt(exitBlock.getLocation());

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, (ItemStack) null, exitBlock, BlockFace.UP);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "the block interaction should not also fire in the world");
    }
}
