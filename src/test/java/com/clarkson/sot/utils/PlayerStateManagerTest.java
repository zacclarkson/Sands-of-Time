package com.clarkson.sot.utils;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link PlayerStateManager}. It has a zero-arg constructor and stores state in a
 * plain map, so no mock server is required — the {@link Player} overloads only need a stubbed UUID.
 */
class PlayerStateManagerTest {

    private PlayerStateManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlayerStateManager();
    }

    private Player playerWithId(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    @Test
    void untrackedPlayerReportsNotInGame() {
        assertEquals(PlayerStatus.NOT_IN_GAME, manager.getStatus(UUID.randomUUID()));
    }

    @Test
    void initializePlayerStartsAliveInDungeon() {
        Player player = playerWithId(UUID.randomUUID());
        manager.initializePlayer(player);
        assertEquals(PlayerStatus.ALIVE_IN_DUNGEON, manager.getStatus(player));
    }

    @Test
    void updateStatusChangesTrackedPlayer() {
        Player player = playerWithId(UUID.randomUUID());
        manager.initializePlayer(player);
        manager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE);
        assertEquals(PlayerStatus.DEAD_AWAITING_REVIVE, manager.getStatus(player));
    }

    @Test
    void updateStatusByUuidIgnoresUntrackedPlayers() {
        // The UUID overload uses computeIfPresent, so untracked players are not added.
        UUID id = UUID.randomUUID();
        manager.updateStatus(id, PlayerStatus.ESCAPED_SAFE);
        assertEquals(PlayerStatus.NOT_IN_GAME, manager.getStatus(id));
    }

    @Test
    void removePlayerStopsTracking() {
        Player player = playerWithId(UUID.randomUUID());
        manager.initializePlayer(player);
        manager.removePlayer(player);
        assertEquals(PlayerStatus.NOT_IN_GAME, manager.getStatus(player));
    }

    @Test
    void getPlayerUUIDsWithStatusFiltersByStatus() {
        UUID alive = UUID.randomUUID();
        UUID dead = UUID.randomUUID();
        manager.initializePlayer(playerWithId(alive));
        manager.initializePlayer(playerWithId(dead));
        manager.updateStatus(dead, PlayerStatus.DEAD_AWAITING_REVIVE);

        assertEquals(java.util.List.of(alive), manager.getPlayerUUIDsWithStatus(PlayerStatus.ALIVE_IN_DUNGEON));
        assertEquals(java.util.List.of(dead), manager.getPlayerUUIDsWithStatus(PlayerStatus.DEAD_AWAITING_REVIVE));
    }

    @Test
    void isPlayerInDungeonTrueForAliveAndAwaitingRevive() {
        Player player = playerWithId(UUID.randomUUID());
        manager.initializePlayer(player);
        assertTrue(manager.isPlayerInDungeon(player));
        manager.updateStatus(player, PlayerStatus.DEAD_AWAITING_REVIVE);
        assertTrue(manager.isPlayerInDungeon(player));
        manager.updateStatus(player, PlayerStatus.ESCAPED_SAFE);
        assertFalse(manager.isPlayerInDungeon(player));
    }

    @Test
    void clearAllStatesEmptiesTracking() {
        manager.initializePlayer(playerWithId(UUID.randomUUID()));
        manager.clearAllStates();
        assertTrue(manager.getAllPlayerStates().isEmpty());
    }
}
