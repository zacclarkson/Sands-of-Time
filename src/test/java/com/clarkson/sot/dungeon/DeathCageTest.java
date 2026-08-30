package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers {@link DeathCage}: the block match that decides whether a clicked chest is this cage's
 * sacrifice point, and the escalating, part-payable price of getting its occupant out.
 *
 * <p>Pure state, so none of this needs a server.
 */
class DeathCageTest {

    private final World world = mock(World.class);
    private final World otherWorld = mock(World.class);

    private DeathCage cage() {
        return new DeathCage(new Location(world, 30, 65, 4), new Location(world, 30, 65, 6));
    }

    // --- sacrifice point matching ---

    @Test
    void matchesAnywhereInsideTheSacrificeBlock() {
        DeathCage cage = cage();

        assertTrue(cage.isSacrificePointAt(new Location(world, 30, 65, 6)));
        assertTrue(cage.isSacrificePointAt(new Location(world, 30.8, 65.2, 6.5)),
                "block coordinates, not exact doubles");
    }

    @Test
    void doesNotMatchTheNeighbouringBlock() {
        DeathCage cage = cage();

        assertFalse(cage.isSacrificePointAt(new Location(world, 31, 65, 6)));
        assertFalse(cage.isSacrificePointAt(new Location(world, 30, 65, 5)));
    }

    @Test
    void doesNotMatchTheSameCoordinatesInAnotherWorld() {
        assertFalse(cage().isSacrificePointAt(new Location(otherWorld, 30, 65, 6)));
    }

    @Test
    void doesNotMatchTheCageItself() {
        assertFalse(cage().isSacrificePointAt(new Location(world, 30, 65, 4)),
                "the cage and its chest are different blocks");
    }

    // --- escalating price ---

    @Test
    void aFreshCageOwesNothing() {
        DeathCage cage = cage();

        assertEquals(0, cage.getRequiredSand());
        assertEquals(0, cage.getRemainingSand(), "nobody is caged, so there is nothing to pay");
    }

    @Test
    void thePriceIsTheDeathCount() {
        DeathCage cage = cage();

        cage.recordDeath();
        assertEquals(1, cage.getRequiredSand(), "first death costs 1");

        cage.recordDeath();
        assertEquals(2, cage.getRequiredSand());

        cage.recordDeath();
        assertEquals(3, cage.getRequiredSand());
    }

    @Test
    void thePriceStopsClimbingAtTheCap() {
        DeathCage cage = cage();
        for (int i = 0; i < 9; i++) cage.recordDeath();

        assertEquals(9, cage.getDeathCount());
        assertEquals(DeathCage.MAX_REVIVE_COST, cage.getRequiredSand(), "capped at 5");
    }

    // --- part payment ---

    @Test
    void oneSandFreesThemOnTheFirstDeath() {
        DeathCage cage = cage();
        cage.recordDeath();

        assertTrue(cage.depositSand(), "a 1-sand revive completes on the first sand");
        assertEquals(0, cage.getRemainingSand());
    }

    @Test
    void aDearerRevivePaysDownOneSandAtATime() {
        DeathCage cage = cage();
        cage.recordDeath();
        cage.recordDeath();
        cage.recordDeath(); // costs 3

        assertFalse(cage.depositSand());
        assertEquals(2, cage.getRemainingSand());

        assertFalse(cage.depositSand());
        assertEquals(1, cage.getRemainingSand());

        assertTrue(cage.depositSand(), "the third sand completes it");
        assertEquals(0, cage.getRemainingSand());
    }

    @Test
    void remainingNeverGoesNegative() {
        DeathCage cage = cage();
        cage.recordDeath();
        cage.depositSand();
        cage.depositSand(); // an extra beyond the price

        assertEquals(0, cage.getRemainingSand());
    }

    @Test
    void aSecondDeathDoesNotInheritTheFirstsPayments() {
        DeathCage cage = cage();
        cage.recordDeath();
        cage.recordDeath(); // costs 2
        cage.depositSand(); // 1 paid, 1 owed
        assertEquals(1, cage.getRemainingSand());

        cage.recordDeath(); // died again before being freed: now costs 3, from scratch

        assertEquals(0, cage.getSandDeposited(), "part-payment is discarded on a new death");
        assertEquals(3, cage.getRemainingSand());
    }

    @Test
    void clearProgressResetsPartPaymentButNotThePrice() {
        DeathCage cage = cage();
        cage.recordDeath();
        cage.recordDeath();
        cage.depositSand();

        cage.clearProgress();

        assertEquals(0, cage.getSandDeposited());
        assertEquals(2, cage.getRequiredSand(), "the death count still stands");
    }

    // --- assignment ---

    @Test
    void assignmentRoundTrips() {
        DeathCage cage = cage();
        UUID player = UUID.randomUUID();

        assertFalse(cage.isAssigned());
        cage.assignPlayer(player);
        assertTrue(cage.isAssigned());
        assertEquals(player, cage.getAssignedPlayerUUID());

        cage.clearAssignment();
        assertFalse(cage.isAssigned());
        assertNull(cage.getAssignedPlayerUUID());
    }
}
