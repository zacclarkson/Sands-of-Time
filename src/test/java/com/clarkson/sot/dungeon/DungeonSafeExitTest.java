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
 * Covers the absolute safe-exit location a team's {@link Dungeon} carries, and the block-coordinate
 * match {@code EscapeListener} relies on. {@link World} is a Mockito mock, so no server is needed.
 */
class DungeonSafeExitTest {

    private final World world = mock(World.class);

    private DungeonBlueprint blueprint(Vector safeExitRelative) {
        Area bounds = new Area(new Location(null, 0, 0, 0), new Location(null, 15, 7, 15));
        return new DungeonBlueprint(List.of(), new Vector(0, 0, 0), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), bounds, safeExitRelative);
    }

    private Dungeon dungeon(Location safeExit) {
        Location origin = new Location(world, 100, 64, 100);
        return new Dungeon(UUID.randomUUID(), world, origin, blueprint(null),
                origin.clone(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                safeExit);
    }

    @Test
    void blueprintAcceptsAnUndefinedSafeExit() {
        assertNull(blueprint(null).getSafeExitRelativeLocation(),
                "templates predating the SAFE_EXIT marker must not fail blueprint construction");
    }

    @Test
    void blueprintReturnsACopyOfTheSafeExit() {
        DungeonBlueprint blueprint = blueprint(new Vector(4, 1, 7));
        Vector first = blueprint.getSafeExitRelativeLocation();

        first.add(new Vector(50, 50, 50));

        assertEquals(new Vector(4, 1, 7), blueprint.getSafeExitRelativeLocation(),
                "the stored vector must not be mutable through the getter");
    }

    @Test
    void reportsNoSafeExitWhenUndefined() {
        Dungeon dungeon = dungeon(null);

        assertNull(dungeon.getSafeExitLocation());
        assertFalse(dungeon.isSafeExitAt(new Location(world, 100, 64, 100)));
    }

    @Test
    void matchesAnyPositionInsideTheSafeExitBlock() {
        Dungeon dungeon = dungeon(new Location(world, 104, 65, 107));

        assertTrue(dungeon.isSafeExitAt(new Location(world, 104.7, 65.2, 107.9)),
                "a fractional position inside the block should match");
        assertFalse(dungeon.isSafeExitAt(new Location(world, 105, 65, 107)));
        assertFalse(dungeon.isSafeExitAt(new Location(mock(World.class), 104, 65, 107)),
                "the same coordinates in another world are not the exit");
    }

    @Test
    void returnsACopyOfTheSafeExitLocation() {
        Dungeon dungeon = dungeon(new Location(world, 104, 65, 107));

        Location first = dungeon.getSafeExitLocation();
        first.add(0, 100, 0);

        assertEquals(65, dungeon.getSafeExitLocation().getBlockY(),
                "the stored location must not be mutable through the getter");
    }
}
