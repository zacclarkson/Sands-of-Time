package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;

import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dispatches real {@link FoodLevelChangeEvent}s at a MockBukkit server so {@link HungerListener}
 * runs as it would in game, with {@link GameManager} mocked the way {@link BlockProtectionListenerTest}
 * does. MockBukkit never fires this event on its own ({@code setFoodLevel} is a plain setter), so
 * every case builds the event by hand and calls it through the plugin manager.
 */
class HungerListenerTest {

    private ServerMock server;
    private Plugin plugin;
    private GameManager gameManager;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);

        player = server.addPlayer();
        player.setFoodLevel(20);
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(true);

        server.getPluginManager().registerEvents(new HungerListener(gameManager), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Fires a food-level change to {@code newLevel} for the test player and returns the event. */
    private FoodLevelChangeEvent changeTo(int newLevel) {
        // The two-argument constructor is deprecated for removal; the item is null for a natural drain.
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, newLevel, null);
        server.getPluginManager().callEvent(event);
        return event;
    }

    // --- The rule ---------------------------------------------------------------------------

    @Test
    void hungerDoesNotDropDuringARound() {
        assertTrue(changeTo(19).isCancelled(), "a participant's bar must never go down mid-round");
        assertTrue(changeTo(0).isCancelled());
    }

    @Test
    void theBarCanStillGoUp() {
        player.setFoodLevel(10);
        assertFalse(changeTo(14).isCancelled(), "eating something carried in must still work");
        assertFalse(changeTo(10).isCancelled(), "an unchanged level is not a drop");
    }

    @Test
    void nonParticipantsStillGetHungry() {
        when(gameManager.isParticipant(player.getUniqueId())).thenReturn(false);
        assertFalse(changeTo(19).isCancelled(), "staff standing outside the round are untouched");
    }

    // --- Which game states it applies in ----------------------------------------------------

    @Test
    void hungerIsFrozenFromTheCountdownAndWhilePaused() {
        when(gameManager.getCurrentState()).thenReturn(GameState.COUNTDOWN);
        assertTrue(changeTo(19).isCancelled(), "players are already in the dungeon during the countdown");

        when(gameManager.getCurrentState()).thenReturn(GameState.PAUSED);
        assertTrue(changeTo(19).isCancelled(), "a paused round is still a round");
    }

    @Test
    void nothingIsFrozenBetweenRounds() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);
        assertFalse(changeTo(19).isCancelled());
        verify(gameManager, never()).isParticipant(any());

        when(gameManager.getCurrentState()).thenReturn(GameState.ENDED);
        assertFalse(changeTo(19).isCancelled());
    }

    // --- The start-of-round top-up ----------------------------------------------------------

    @Test
    void fillHungerRestoresAFullBar() {
        player.setFoodLevel(3);
        player.setSaturation(0f);

        HungerListener.fillHunger(player);

        assertEquals(HungerListener.FULL_FOOD_LEVEL, player.getFoodLevel());
        // Saturation is clamped to the food level, so this also pins the food-before-saturation
        // ordering inside fillHunger: done the other way round it would clamp to 3.
        assertEquals(HungerListener.START_SATURATION, player.getSaturation(), 0.001f);
    }
}
