package com.clarkson.sot.timer;

import com.clarkson.sot.utils.SoTTeam;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins the geometry of {@link VisualSandTimerDisplay#isColumnBlock} — the predicate that stops
 * players mining their own sand timer for free time.
 *
 * <p>An off-by-one here is not cosmetic: one cell too few leaves a minable block in the column, and
 * mining it credits +10s and is immediately replaced by {@code syncVisualState}, which is the
 * exploit this whole guard exists to close.
 *
 * <p>MockBukkit is needed only for a real {@link World} and locations. Constructing the display
 * never touches blocks and starts no scheduler — every write is behind the {@code armed} flag — so
 * there is nothing to tear down beyond unmocking.
 */
class VisualSandTimerDisplayColumnTest {

    private static final int BOTTOM_Y = 64;

    private ServerMock server;
    private World world;
    private VisualSandTimerDisplay display;
    private Location bottom;
    private Location top;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("timer_column_world");

        SoTTeam team = mock(SoTTeam.class);
        when(team.getTeamName()).thenReturn("Test Team");

        bottom = new Location(world, 21, BOTTOM_Y, 18);
        top = bottom.clone().add(0, VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, 0);
        display = new VisualSandTimerDisplay(plugin, team, bottom, top);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    @Test
    void theLowestSandCellIsPartOfTheColumn() {
        assertTrue(display.isColumnBlock(at(21, BOTTOM_Y + 1, 18)));
    }

    @Test
    void theHighestSandCellIsPartOfTheColumn() {
        assertTrue(display.isColumnBlock(at(21, top.getBlockY(), 18)));
    }

    @Test
    void everyCellOfAFullColumnIsProtected() {
        int protectedCells = 0;
        for (int y = BOTTOM_Y + 1; y <= top.getBlockY(); y++) {
            assertTrue(display.isColumnBlock(at(21, y, 18)), "y=" + y + " must be protected");
            protectedCells++;
        }
        assertEquals(VisualTimerLayout.COLUMN_HEIGHT_BLOCKS, protectedCells,
                "the protected range must be exactly a full column");
    }

    /**
     * The pedestal is the segment's TIMER marker block itself, not sand. It is protected by the
     * break whitelist rather than by this predicate, so including it here would be misleading.
     */
    @Test
    void thePedestalIsNotPartOfTheColumn() {
        assertFalse(display.isColumnBlock(at(21, BOTTOM_Y, 18)));
    }

    @Test
    void theCellAboveTheColumnIsNotPartOfIt() {
        assertFalse(display.isColumnBlock(at(21, top.getBlockY() + 1, 18)));
    }

    @Test
    void neighbouringColumnsAreNotPartOfIt() {
        int y = BOTTOM_Y + 1;
        assertFalse(display.isColumnBlock(at(20, y, 18)));
        assertFalse(display.isColumnBlock(at(22, y, 18)));
        assertFalse(display.isColumnBlock(at(21, y, 17)));
        assertFalse(display.isColumnBlock(at(21, y, 19)));
    }

    @Test
    void theSameCoordinatesInAnotherWorldAreNotPartOfIt() {
        World other = server.addSimpleWorld("somewhere_else");
        assertFalse(display.isColumnBlock(new Location(other, 21, BOTTOM_Y + 1, 18)));
    }

    @Test
    void nullIsNotPartOfIt() {
        assertFalse(display.isColumnBlock(null));
    }

    /**
     * Deliberately not gated on the display's {@code armed} flag. The column is anchored by
     * {@code GameManager.startGame} but only armed at {@code startVisualUpdates()}, and the gap
     * between the two is the countdown — exactly when the hub segment's baked sand shaft is standing
     * in the world waiting to be mined. This test exists so that "tidying up" by adding an
     * {@code armed} check fails loudly.
     */
    @Test
    void theColumnIsProtectedBeforeTheDisplayIsArmed() {
        assertTrue(display.isColumnBlock(at(21, BOTTOM_Y + 1, 18)),
                "the column must be protected during the countdown, before visual updates start");
    }
}
