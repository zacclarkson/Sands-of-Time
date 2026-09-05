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
 * Covers {@link Dungeon#isBankAt}, the block-coordinate match that decides whether a right-clicked
 * block is the team's coin bank.
 *
 * <p>Deliberately exact, for the same reason as {@link Dungeon#isSandTimerDepositAt}: the BANK
 * marker records the air cell the bank block is written into, so there is no +/-1 tolerance to
 * allow for.
 */
class DungeonBankTest {

    private final World world = mock(World.class);
    private final World otherWorld = mock(World.class);

    private Dungeon dungeon(Location bank) {
        Area bounds = new Area(new Location(null, 0, 0, 0), new Location(null, 15, 7, 15));
        DungeonBlueprint blueprint = new DungeonBlueprint(List.of(), new Vector(0, 0, 0), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), bounds, null, null, null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Location origin = new Location(world, 100, 64, 100);
        return new Dungeon(UUID.randomUUID(), world, origin, blueprint,
                origin.clone(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                null, bank, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void matchesAnywhereInsideTheBankBlock() {
        Dungeon dungeon = dungeon(new Location(world, 121, 65, 104));

        assertTrue(dungeon.isBankAt(new Location(world, 121, 65, 104)));
        assertTrue(dungeon.isBankAt(new Location(world, 121.9, 65.4, 104.5)),
                "block coordinates, not exact doubles");
    }

    @Test
    void doesNotMatchTheNeighbouringBlock() {
        Dungeon dungeon = dungeon(new Location(world, 121, 65, 104));

        assertFalse(dungeon.isBankAt(new Location(world, 122, 65, 104)));
        assertFalse(dungeon.isBankAt(new Location(world, 121, 64, 104)),
                "no +/-1 Y tolerance: the marker cell is the cell the chest is written into");
    }

    @Test
    void doesNotMatchTheSameCoordinatesInAnotherWorld() {
        Dungeon dungeon = dungeon(new Location(world, 121, 65, 104));

        assertFalse(dungeon.isBankAt(new Location(otherWorld, 121, 65, 104)),
                "every team's dungeon shares a coordinate shape; only the world tells them apart");
    }

    @Test
    void matchesNothingWhenNoTemplateDefinedABank() {
        Dungeon dungeon = dungeon(null);

        assertNull(dungeon.getBankLocation());
        assertFalse(dungeon.isBankAt(new Location(world, 121, 65, 104)),
                "templates predating the BANK marker simply have nowhere to bank");
    }

    @Test
    void handsOutACopyOfTheBankLocation() {
        Dungeon dungeon = dungeon(new Location(world, 121, 65, 104));

        Location returned = dungeon.getBankLocation();
        assertNotNull(returned);
        returned.add(50, 0, 0);

        assertTrue(dungeon.isBankAt(new Location(world, 121, 65, 104)),
                "Location is mutable; a caller must not be able to move the bank");
    }
}
