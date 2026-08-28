package com.clarkson.sot.player;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure arithmetic tests for the {@link SoTPlayerData} stat holder. Only a mocked {@link Player} is
 * needed to construct it (for UUID/name).
 */
class SoTPlayerDataTest {

    private SoTPlayerData data;

    @BeforeEach
    void setUp() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        data = new SoTPlayerData(player);
    }

    @Test
    void startsWithZeroedStats() {
        assertEquals(0, data.getUnbankedCoins());
        assertEquals(0, data.getTimesDied());
        assertEquals(0, data.getBankedScore());
    }

    @Test
    void addUnbankedCoinsAlsoTracksTotalCollected() {
        data.addUnbankedCoins(40);
        data.addUnbankedCoins(10);
        assertEquals(50, data.getUnbankedCoins());
        assertEquals(50, data.getTotalCoinsCollected());
    }

    @Test
    void addUnbankedCoinsIgnoresNonPositive() {
        data.addUnbankedCoins(-5);
        data.addUnbankedCoins(0);
        assertEquals(0, data.getUnbankedCoins());
    }

    @Test
    void takeUnbankedCoinsIsClampedToAvailable() {
        data.addUnbankedCoins(30);
        assertEquals(30, data.takeUnbankedCoins(100), "cannot take more than available");
        assertEquals(0, data.getUnbankedCoins());
    }

    @Test
    void setUnbankedCoinsClampsNegativeToZero() {
        data.setUnbankedCoins(-20);
        assertEquals(0, data.getUnbankedCoins());
    }

    @Test
    void incrementDeathsCounts() {
        data.incrementDeaths();
        data.incrementDeaths();
        assertEquals(2, data.getTimesDied());
    }

    @Test
    void addBankedCoinsAccumulates() {
        data.addBankedCoins(15);
        data.addBankedCoins(25);
        assertEquals(40, data.getBankedScore());
    }
}
