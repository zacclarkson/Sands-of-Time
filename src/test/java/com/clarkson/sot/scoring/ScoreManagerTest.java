package com.clarkson.sot.scoring;

import com.clarkson.sot.entities.CoinStack;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link ScoreManager}. No mock server is needed — the class holds its unbanked
 * scores in a plain map and only touches a mocked {@link Player}/{@link Plugin}. Depth scaling is
 * verified through the public {@code collectFloorItem} path.
 */
class ScoreManagerTest {

    private ScoreManager scoreManager;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ScoreManagerTest"));
        scoreManager = new ScoreManager(mock(TeamManager.class), mock(GameManager.class), plugin);
    }

    @Test
    void unbankedScoreAccumulates() {
        UUID id = UUID.randomUUID();
        scoreManager.updatePlayerUnbankedScore(id, 30);
        scoreManager.updatePlayerUnbankedScore(id, 12);
        assertEquals(42, scoreManager.getPlayerUnbankedScore(id));
    }

    @Test
    void setUnbankedScoreClampsNegativesToZero() {
        UUID id = UUID.randomUUID();
        scoreManager.setPlayerUnbankedScore(id, -100);
        assertEquals(0, scoreManager.getPlayerUnbankedScore(id));
    }

    @Test
    void deathPenaltyReturnsAndClearsUnbankedCoins() {
        UUID id = UUID.randomUUID();
        scoreManager.setPlayerUnbankedScore(id, 75);
        int lost = scoreManager.applyDeathPenalty(id);
        assertEquals(75, lost);
        assertEquals(0, scoreManager.getPlayerUnbankedScore(id));
    }

    @Test
    void unknownPlayerHasZeroScore() {
        assertEquals(0, scoreManager.getPlayerUnbankedScore(UUID.randomUUID()));
    }

    @Test
    void coinValueIsUnscaledAtDepthZero() {
        assertEquals(100, collect(100, 0));
    }

    @Test
    void coinValueScalesTo120PercentAtMaxDepth() {
        // 100% at depth 0 up to 120% at depth 10 (MAX_DUNGEON_DEPTH).
        assertEquals(120, collect(100, 10));
    }

    @Test
    void depthScalingIsCappedBeyondMaxDepth() {
        assertEquals(120, collect(100, 50), "depth beyond max should not exceed 120%");
    }

    /** Collects a single mocked coin of the given base value/depth and returns the resulting score. */
    private int collect(int baseValue, int depth) {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);

        CoinStack coin = mock(CoinStack.class);
        when(coin.getBaseValue()).thenReturn(baseValue);
        when(coin.getDepth()).thenReturn(depth);

        scoreManager.collectFloorItem(player, coin);
        return scoreManager.getPlayerUnbankedScore(id);
    }
}
