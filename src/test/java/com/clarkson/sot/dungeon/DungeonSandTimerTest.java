package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers {@link Dungeon#isSandTimerDepositAt}, the block-coordinate match that decides whether a
 * placed sand block counts as a deposit onto the team's timer.
 *
 * <p>Deliberately exact: the TIMER_DEPOSIT marker records the same air cell a placed block occupies,
 * so unlike the safe exit there is no +/-1 tolerance to allow for.
 */
class DungeonSandTimerTest {

    private final World world = mock(World.class);
    private final World otherWorld = mock(World.class);

    private Dungeon dungeon(List<Location> deposits) {
        Area bounds = new Area(new Location(null, 0, 0, 0), new Location(null, 15, 7, 15));
        DungeonBlueprint blueprint = new DungeonBlueprint(List.of(), new Vector(0, 0, 0), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), bounds, null, null, null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Location origin = new Location(world, 100, 64, 100);
        return new Dungeon(UUID.randomUUID(), world, origin, blueprint,
                origin.clone(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), deposits, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void matchesAnywhereInsideTheDepositBlock() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertTrue(dungeon.isSandTimerDepositAt(new Location(world, 110, 66, 118)));
        assertTrue(dungeon.isSandTimerDepositAt(new Location(world, 110.9, 66.4, 118.5)),
                "block coordinates, not exact doubles");
    }

    @Test
    void doesNotMatchTheNeighbouringBlock() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertFalse(dungeon.isSandTimerDepositAt(new Location(world, 111, 66, 118)));
        assertFalse(dungeon.isSandTimerDepositAt(new Location(world, 110, 65, 118)),
                "no +/-1 Y tolerance: the marker cell is the cell the block lands in");
    }

    @Test
    void doesNotMatchTheSameCoordinatesInAnotherWorld() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertFalse(dungeon.isSandTimerDepositAt(new Location(otherWorld, 110, 66, 118)),
                "every team's dungeon shares a coordinate shape; only the world tells them apart");
    }

    @Test
    void matchesAnyOfSeveralDepositCells() {
        Dungeon dungeon = dungeon(List.of(
                new Location(world, 110, 66, 118),
                new Location(world, 112, 66, 118)));

        assertTrue(dungeon.isSandTimerDepositAt(new Location(world, 112, 66, 118)));
    }

    @Test
    void matchesNothingWhenNoTemplateDefinedADeposit() {
        Dungeon dungeon = dungeon(List.of());

        assertTrue(dungeon.getSandTimerLocations().isEmpty());
        assertFalse(dungeon.isSandTimerDepositAt(new Location(world, 110, 66, 118)),
                "templates predating the TIMER_DEPOSIT marker simply have nowhere to deposit");
    }
}
