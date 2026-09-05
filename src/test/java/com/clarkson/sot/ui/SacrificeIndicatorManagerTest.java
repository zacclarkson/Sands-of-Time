package com.clarkson.sot.ui;

import com.clarkson.sot.dungeon.DeathCage;
import com.clarkson.sot.dungeon.GateSacrificePoint;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The floating sand-and-count above a sacrifice chest, for both a cage chest (shown only while
 * someone is caged) and a gate chest (shown from instance init as the price, until the gates open).
 *
 * <p>Keyed on the chest block, so a cage and a gate point over the same cell share one indicator.
 */
class SacrificeIndicatorManagerTest {

    private ServerMock server;
    private World world;
    private SacrificeIndicatorManager indicators;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("indicator_world");
        indicators = new SacrificeIndicatorManager(Logger.getLogger("SacrificeIndicatorManagerTest"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit applies entity spawns and removals on the next tick, so settle before counting.
    private long sandDisplays() { server.getScheduler().performOneTick(); return world.getEntitiesByClass(BlockDisplay.class).size(); }
    private long textDisplays() { server.getScheduler().performOneTick(); return world.getEntitiesByClass(TextDisplay.class).size(); }

    private Location chest() { return new Location(world, 10, 64, 10); }

    @Test
    void aPriceTagIsOneSandBlockAndOneLabel() {
        indicators.update(chest(), 3);

        assertEquals(1, sandDisplays());
        assertEquals(1, textDisplays());
    }

    @Test
    void refreshingKeepsASingleIndicator() {
        indicators.update(chest(), 3);
        indicators.update(chest(), 2);

        assertEquals(1, sandDisplays(), "the label is rewritten, not duplicated");
        assertEquals(1, textDisplays());
    }

    @Test
    void nothingOwedMeansNothingShown() {
        indicators.update(chest(), 3);
        indicators.update(chest(), 0);

        assertEquals(0, sandDisplays());
        assertEquals(0, textDisplays());
    }

    @Test
    void hideRemovesTheDisplaysAndIsSafeWhenThereAreNone() {
        indicators.update(chest(), 3);
        indicators.hide(chest());
        indicators.hide(chest());

        assertEquals(0, sandDisplays());
        assertEquals(0, textDisplays());
    }

    @Test
    void aGatePointShowsItsRemainingPriceUntilItOpens() {
        GateSacrificePoint point = new GateSacrificePoint(chest(), 2, List.of(), null, "paywall");

        indicators.update(point);
        assertEquals(1, textDisplays(), "the asking price is visible before anyone pays");

        point.depositSand();
        indicators.update(point);
        assertEquals(1, textDisplays());

        point.markOpened();
        indicators.update(point);
        assertEquals(0, textDisplays(), "an open gate has nothing left to sell");
    }

    @Test
    void aCageAndAGatePointOverTheSameChestShareOneIndicator() {
        DeathCage cage = new DeathCage(new Location(world, 5, 64, 5), chest());
        cage.recordDeath();
        GateSacrificePoint point = new GateSacrificePoint(chest(), 2, List.of(), null, "paywall");

        indicators.update(cage);
        indicators.update(point);
        assertEquals(1, sandDisplays(), "keyed on the block, not on the object");

        indicators.hide(cage);
        assertEquals(0, sandDisplays(), "hiding through either handle clears the one indicator");
    }

    @Test
    void clearAllEmptiesEveryChest() {
        indicators.update(chest(), 3);
        indicators.update(new Location(world, 20, 64, 20), 1);

        indicators.clearAll();

        assertEquals(0, sandDisplays());
        assertEquals(0, textDisplays());
    }
}
