package com.clarkson.sot.timer;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VisualTimerLayout}, the sand-column geometry behind each team's
 * visual timer. Pure coordinate maths; MockBukkit supplies a world only for its build limits.
 */
class VisualTimerLayoutTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("lobby");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location anchor(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    @Test
    void columnHeightMatchesTheGameRules() {
        // 150 seconds of timer at 10 seconds per sand block = 15 blocks.
        assertEquals(15, VisualTimerLayout.COLUMN_HEIGHT_BLOCKS);
        assertEquals(TeamTimer.DEFAULT_MAX_TIMER_SECONDS,
                VisualTimerLayout.COLUMN_HEIGHT_BLOCKS * VisualSandTimerDisplay.SECONDS_PER_BLOCK);
    }

    @Test
    void topSitsAFullColumnAboveBottom() {
        Location bottom = VisualTimerLayout.bottomLocation(anchor(10, 64, -20), 0);
        Location top = VisualTimerLayout.topLocation(anchor(10, 64, -20), 0);

        assertEquals(VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, top.getBlockY() - bottom.getBlockY());
        assertEquals(64, bottom.getBlockY());
        assertEquals(79, top.getBlockY());
    }

    @Test
    void columnIsVerticalAndAnchoredAtTheLobby() {
        Location bottom = VisualTimerLayout.bottomLocation(anchor(10, 64, -20), 0);
        Location top = VisualTimerLayout.topLocation(anchor(10, 64, -20), 0);

        assertEquals(bottom.getBlockX(), top.getBlockX(), "Column must be vertical");
        assertEquals(bottom.getBlockZ(), top.getBlockZ(), "Column must be vertical");
        assertEquals(10, bottom.getBlockX());
        assertEquals(-20, bottom.getBlockZ());
        assertSame(world, bottom.getWorld());
        assertSame(world, top.getWorld());
    }

    @Test
    void eachTeamGetsItsOwnColumn() {
        Location first = VisualTimerLayout.bottomLocation(anchor(0, 64, 0), 0);
        Location second = VisualTimerLayout.bottomLocation(anchor(0, 64, 0), 1);
        Location fourth = VisualTimerLayout.bottomLocation(anchor(0, 64, 0), 3);

        assertEquals(VisualTimerLayout.COLUMN_SPACING_BLOCKS, second.getBlockX() - first.getBlockX());
        assertEquals(3 * VisualTimerLayout.COLUMN_SPACING_BLOCKS, fourth.getBlockX() - first.getBlockX());
        assertEquals(first.getBlockZ(), second.getBlockZ());
        assertEquals(first.getBlockY(), second.getBlockY());
    }

    @Test
    void columnsStayWithinBuildLimits() {
        Location highBottom = VisualTimerLayout.bottomLocation(anchor(0, world.getMaxHeight() + 50, 0), 0);
        Location highTop = VisualTimerLayout.topLocation(anchor(0, world.getMaxHeight() + 50, 0), 0);
        assertTrue(highTop.getBlockY() <= world.getMaxHeight() - 1,
                "Top sand block must stay below the build limit");
        assertEquals(VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, highTop.getBlockY() - highBottom.getBlockY());

        Location lowBottom = VisualTimerLayout.bottomLocation(anchor(0, world.getMinHeight() - 50, 0), 0);
        Location lowTop = VisualTimerLayout.topLocation(anchor(0, world.getMinHeight() - 50, 0), 0);
        assertEquals(world.getMinHeight(), lowBottom.getBlockY());
        assertEquals(VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, lowTop.getBlockY() - lowBottom.getBlockY());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(NullPointerException.class, () -> VisualTimerLayout.bottomLocation(null, 0));
        assertThrows(NullPointerException.class,
                () -> VisualTimerLayout.topLocation(new Location(null, 0, 64, 0), 0));
        assertThrows(IllegalArgumentException.class, () -> VisualTimerLayout.bottomLocation(anchor(0, 64, 0), -1));
    }

    @Test
    void endpointsDriveALiveDisplay() {
        // The pair must satisfy VisualSandTimerDisplay's own validation (same X/Z, bottom below top).
        Location bottom = VisualTimerLayout.bottomLocation(anchor(5, 64, 5), 2);
        Location top = VisualTimerLayout.topLocation(anchor(5, 64, 5), 2);
        assertTrue(bottom.getBlockY() < top.getBlockY());
        assertEquals(bottom.getBlockX(), top.getBlockX());
        assertEquals(bottom.getBlockZ(), top.getBlockZ());
    }
}
