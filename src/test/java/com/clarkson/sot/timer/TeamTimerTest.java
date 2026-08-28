package com.clarkson.sot.timer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TeamTimer}.
 *
 * <p>Demonstrates two patterns:
 * <ul>
 *   <li>Pure state/clamping logic (constructor, {@code addSeconds}, {@code reset}) which touches no
 *       Bukkit statics when the visual notifier is {@code null}.</li>
 *   <li>Scheduler-driven behaviour (countdown + expiry callback) driven by advancing MockBukkit's
 *       mock scheduler.</li>
 * </ul>
 */
class TeamTimerTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TeamTimer newTimer(int startSeconds, int maxSeconds, TeamTimer.TimerCallback cb) {
        // null visual notifier keeps the timer free of visual-display side effects.
        return new TeamTimer(plugin, cb, null, startSeconds, maxSeconds, 20L);
    }

    // --- Pure clamping logic ---

    @Test
    void constructorClampsStartSecondsIntoRange() {
        assertEquals(0, newTimer(-50, 100, () -> {}).getSecondsLeft(), "negative start clamps to 0");
        assertEquals(100, newTimer(9999, 100, () -> {}).getSecondsLeft(), "over-max start clamps to max");
        assertEquals(30, newTimer(30, 100, () -> {}).getSecondsLeft(), "in-range start preserved");
    }

    @Test
    void addSecondsCapsAtMax() {
        TeamTimer timer = newTimer(90, 100, () -> {});
        timer.addSeconds(5);
        assertEquals(95, timer.getSecondsLeft());
        timer.addSeconds(50); // would be 145, capped to 100
        assertEquals(100, timer.getSecondsLeft());
    }

    @Test
    void addSecondsIgnoresNonPositiveAmounts() {
        TeamTimer timer = newTimer(50, 100, () -> {});
        timer.addSeconds(0);
        timer.addSeconds(-10);
        assertEquals(50, timer.getSecondsLeft());
    }

    @Test
    void resetClampsAndStops() {
        TeamTimer timer = newTimer(50, 100, () -> {});
        timer.reset(9999);
        assertEquals(100, timer.getSecondsLeft());
        timer.reset(-5);
        assertEquals(0, timer.getSecondsLeft());
        assertFalse(timer.isRunning());
    }

    // --- Scheduler-driven countdown / expiry ---

    @Test
    void countsDownOneSecondPerIntervalWhenScheduled() {
        TeamTimer timer = newTimer(3, 100, () -> {});
        timer.start();
        assertTrue(timer.isRunning());

        server.getScheduler().performTicks(20L); // one interval
        assertEquals(2, timer.getSecondsLeft());

        server.getScheduler().performTicks(20L);
        assertEquals(1, timer.getSecondsLeft());
    }

    @Test
    void firesExpiryCallbackAndStopsAtZero() {
        AtomicBoolean expired = new AtomicBoolean(false);
        TeamTimer timer = newTimer(1, 100, () -> expired.set(true));
        timer.start();

        server.getScheduler().performTicks(20L); // 1 -> 0 -> expiry

        assertTrue(expired.get(), "expiry callback should fire at zero");
        assertEquals(0, timer.getSecondsLeft());
        assertFalse(timer.isRunning(), "timer stops itself on expiry");
    }
}
