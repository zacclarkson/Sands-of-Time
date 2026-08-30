package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.PlayerStateManager;
import com.clarkson.sot.utils.PlayerStatus;
import com.clarkson.sot.utils.SandManager;

import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.InOrder;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Demonstrates simulating a player interaction end-to-end: a real {@link PlayerMock} is created on a
 * MockBukkit server, {@link DeathListener} is registered, and a real {@link PlayerDeathEvent} is
 * dispatched through the plugin manager so the listener runs exactly as it would in game.
 *
 * <p>The listener's collaborators ({@link GameManager}/{@link PlayerStateManager}/{@link SandManager})
 * are Mockito mocks since the listener only reads game state and delegates. What
 * {@code dropCarriedSandOnDeath} actually does with the sand is covered by
 * {@link com.clarkson.sot.utils.SandManagerTest}; here it is only the wiring under test.
 */
class DeathListenerTest {

    private ServerMock server;
    private Plugin plugin;
    private GameManager gameManager;
    private PlayerStateManager stateManager;
    private SandManager sandManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        gameManager = mock(GameManager.class);
        stateManager = mock(PlayerStateManager.class);
        sandManager = mock(SandManager.class);
        when(gameManager.getPlayerStateManager()).thenReturn(stateManager);
        when(gameManager.getSandManager()).thenReturn(sandManager);
        server.getPluginManager().registerEvents(new DeathListener(gameManager), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Builds a real death event for the given player with a non-zero dropped-XP value. */
    private PlayerDeathEvent deathEventFor(PlayerMock player) {
        // Args: player, damageSource, drops, droppedExp, newExp, newTotalExp, newLevel,
        // deathMessage, showDeathMessages.
        // The trailing boolean is required from Paper 26: the eight-argument form that took a
        // Component is gone, and the remaining eight-argument constructor takes a String and is
        // deprecated, so dropping the boolean silently selects the wrong overload.
        return new PlayerDeathEvent(player, mock(DamageSource.class), new ArrayList<ItemStack>(), 10, 0, 0,
                0, Component.text("died"), true);
    }

    @Test
    void handlesDeathWhenRunningAndAliveInDungeon() {
        PlayerMock player = server.addPlayer();
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ALIVE_IN_DUNGEON);

        PlayerDeathEvent event = deathEventFor(player);
        server.getPluginManager().callEvent(event);

        // Ordered: handlePlayerDeath queues the teleport to the death cage, so the sand has to be
        // taken while the player is still standing where they fell.
        InOrder order = inOrder(sandManager, gameManager);
        order.verify(sandManager).dropCarriedSandOnDeath(event);
        order.verify(gameManager).handlePlayerDeath(player);
        assertNull(event.deathMessage(), "default death message should be suppressed");
        assertEquals(0, event.getDroppedExp(), "dropped XP should be zeroed");
    }

    @Test
    void ignoresDeathWhenGameNotRunning() {
        PlayerMock player = server.addPlayer();
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);

        PlayerDeathEvent event = deathEventFor(player);
        server.getPluginManager().callEvent(event);

        verify(gameManager, never()).handlePlayerDeath(any());
        verify(sandManager, never()).dropCarriedSandOnDeath(any());
        assertEquals(10, event.getDroppedExp(), "event should be left untouched when not running");
    }

    @Test
    void ignoresDeathWhenPlayerNotAliveInDungeon() {
        PlayerMock player = server.addPlayer();
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(stateManager.getStatus(player)).thenReturn(PlayerStatus.ESCAPED_SAFE);

        PlayerDeathEvent event = deathEventFor(player);
        server.getPluginManager().callEvent(event);

        verify(gameManager, never()).handlePlayerDeath(any());
        verify(sandManager, never()).dropCarriedSandOnDeath(any());
    }
}
