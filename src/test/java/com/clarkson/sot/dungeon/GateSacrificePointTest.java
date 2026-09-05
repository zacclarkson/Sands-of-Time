package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Payment bookkeeping for a gate sacrifice chest; pure state, so a mocked World is enough. */
class GateSacrificePointTest {

    private final World world = mock(World.class);

    private GateSacrificePoint point(int cost) {
        return new GateSacrificePoint(new Location(world, 10, 64, 10), cost, List.of(),
                new Location(world, 12, 65, 10), "paywall");
    }

    @Test
    void opensOnTheDepositThatCompletesThePrice() {
        GateSacrificePoint point = point(3);

        assertFalse(point.depositSand());
        assertEquals(2, point.getRemainingSand());
        assertFalse(point.depositSand());
        assertEquals(1, point.getRemainingSand());
        assertTrue(point.depositSand(), "the third sand completes the price");
        assertEquals(0, point.getRemainingSand());
    }

    @Test
    void aLeverOpeningTheGatesSpendsThePartPayment() {
        GateSacrificePoint point = point(3);
        point.depositSand();

        point.markOpened();

        assertTrue(point.isOpen());
        assertEquals(0, point.getRemainingSand(), "nothing is owed on an open gate");
        assertEquals(1, point.getSandDeposited(), "and nothing is refunded");
    }

    @Test
    void matchesItsOwnBlockOnly() {
        GateSacrificePoint point = point(1);

        assertTrue(point.isAt(new Location(world, 10.7, 64.2, 10.9)), "any point inside the block");
        assertFalse(point.isAt(new Location(world, 11, 64, 10)));
        assertFalse(point.isAt(new Location(mock(World.class), 10, 64, 10)), "another world's same coordinates");
    }

    @Test
    void knowsWhichLeverOpensItsGates() {
        GateSacrificePoint point = point(1);

        assertTrue(point.isOpenedByLeverAt(new Location(world, 12, 65, 10)));
        assertFalse(point.isOpenedByLeverAt(new Location(world, 13, 65, 10)));
        assertFalse(new GateSacrificePoint(new Location(world, 1, 1, 1), 1, List.of(), null, "no_lever")
                .isOpenedByLeverAt(new Location(world, 12, 65, 10)), "a sacrifice-only chest has no lever");
    }

    @Test
    void rejectsAFreeChest() {
        assertThrows(IllegalArgumentException.class, () ->
                new GateSacrificePoint(new Location(world, 1, 1, 1), 0, List.of(), null, "free"));
    }
}
