package com.clarkson.sot.timer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VisualTimerLayout}, the sand-column geometry behind each team's
 * visual timer. Pure constant maths; no server needed.
 */
class VisualTimerLayoutTest {

    @Test
    void columnHeightMatchesTheGameRules() {
        // 150 seconds of timer at 10 seconds per sand block = 15 blocks.
        assertEquals(15, VisualTimerLayout.COLUMN_HEIGHT_BLOCKS);
        assertEquals(TeamTimer.DEFAULT_MAX_TIMER_SECONDS,
                VisualTimerLayout.COLUMN_HEIGHT_BLOCKS * VisualSandTimerDisplay.SECONDS_PER_BLOCK);
    }
}
