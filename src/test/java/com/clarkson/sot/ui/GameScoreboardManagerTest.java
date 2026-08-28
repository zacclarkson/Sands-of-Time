package com.clarkson.sot.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the sidebar row entries {@link GameScoreboardManager} reserves.
 *
 * <p>No mock server: {@code lineEntry} is a pure string, and it is the one part of the Bukkit
 * plumbing with a correctness requirement worth pinning down — Minecraft collapses duplicate score
 * entries, so two rows sharing one would silently cost a line of the sidebar.
 */
class GameScoreboardManagerTest {

    @Test
    void everySidebarRowGetsItsOwnEntry() {
        Set<String> entries = new HashSet<>();
        for (int line = 0; line < ScoreboardLayout.MAX_SIDEBAR_LINES; line++) {
            assertTrue(entries.add(GameScoreboardManager.lineEntry(line)),
                    "line " + line + " reuses an entry already taken by an earlier row");
        }
        assertEquals(ScoreboardLayout.MAX_SIDEBAR_LINES, entries.size());
    }

    @Test
    void entriesAreFormattingCodesOnlySoNothingIsDrawnBesideThePrefix() {
        for (int line = 0; line < ScoreboardLayout.MAX_SIDEBAR_LINES; line++) {
            assertTrue(GameScoreboardManager.lineEntry(line).matches("(\u00A7[0-9a-fr])+"),
                    "entries must render as nothing: " + GameScoreboardManager.lineEntry(line));
        }
    }

    @Test
    void rejectsRowsOutsideTheSidebar() {
        assertThrows(IllegalArgumentException.class, () -> GameScoreboardManager.lineEntry(-1));
        assertThrows(IllegalArgumentException.class,
                () -> GameScoreboardManager.lineEntry(ScoreboardLayout.MAX_SIDEBAR_LINES));
    }
}
