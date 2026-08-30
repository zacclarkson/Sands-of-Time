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
 * Pins the SAND_TRADE marker's trip through the blueprint and the per-team {@link Dungeon}, and the
 * {@link Dungeon#isSandTradePointAt} lookup the interact handler resolves a click against.
 *
 * <p>No server needed — {@code World} is a plain mock and Location/Vector are value types.
 */
class DungeonSandTradeTest {

    private final World world = mock(World.class);

    private DungeonBlueprint blueprint(List<Vector> sandTrades) {
        Area bounds = new Area(new Location(null, 0, 0, 0), new Location(null, 15, 7, 15));
        return new DungeonBlueprint(List.of(), new Vector(0, 0, 0), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), bounds, null, null, null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), sandTrades);
    }

    private Dungeon dungeon(List<Location> sandTrades) {
        Location origin = new Location(world, 100, 64, 100);
        return new Dungeon(UUID.randomUUID(), world, origin, blueprint(List.of()),
                origin.clone(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), List.of(), List.of(), sandTrades, List.of(), List.of());
    }

    @Test
    void blueprintCarriesSandTradeOffsets() {
        DungeonBlueprint blueprint = blueprint(List.of(new Vector(4, 1, 7), new Vector(20, 1, 3)));

        assertEquals(List.of(new Vector(4, 1, 7), new Vector(20, 1, 3)),
                blueprint.getSandTradeRelativeLocations());
    }

    @Test
    void blueprintAcceptsTemplatesWithNoTradePoints() {
        // Every template on disk predates the marker, and the bundled hub declares none.
        assertTrue(blueprint(List.of()).getSandTradeRelativeLocations().isEmpty());
    }

    @Test
    void blueprintDoesNotExposeAMutableList() {
        DungeonBlueprint blueprint = blueprint(List.of(new Vector(4, 1, 7)));

        assertThrows(UnsupportedOperationException.class,
                () -> blueprint.getSandTradeRelativeLocations().add(new Vector(0, 0, 0)));
    }

    @Test
    void blueprintCopiesTheCallersList() {
        List<Vector> source = new java.util.ArrayList<>(List.of(new Vector(4, 1, 7)));
        DungeonBlueprint blueprint = blueprint(source);

        source.add(new Vector(99, 99, 99));

        assertEquals(1, blueprint.getSandTradeRelativeLocations().size(),
                "the blueprint must not alias the list it was handed");
    }

    @Test
    void dungeonCarriesAbsoluteSandTradeLocations() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 104, 65, 107)));

        assertEquals(List.of(new Location(world, 104, 65, 107)), dungeon.getSandTradeLocations());
    }

    @Test
    void dungeonDoesNotExposeAMutableList() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 104, 65, 107)));

        assertThrows(UnsupportedOperationException.class,
                () -> dungeon.getSandTradeLocations().add(new Location(world, 0, 0, 0)));
    }

    @Test
    void matchesAnywhereInsideTheTradeBlock() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertTrue(dungeon.isSandTradePointAt(new Location(world, 110.7, 66.4, 118.2)),
                "a click resolves to a block, so any point inside the cell must match");
    }

    @Test
    void matchesEveryTradePointNotJustTheFirst() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118), new Location(world, 130, 70, 90)));

        assertTrue(dungeon.isSandTradePointAt(new Location(world, 130, 70, 90)));
    }

    @Test
    void doesNotMatchANeighbouringBlock() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertFalse(dungeon.isSandTradePointAt(new Location(world, 110, 66, 119)),
                "the marker records the cell the chest occupies, so this is an exact match");
        assertFalse(dungeon.isSandTradePointAt(new Location(world, 110, 67, 118)),
                "and needs no +/-1 Y fudge, unlike the safe exit");
    }

    @Test
    void doesNotMatchAnotherWorld() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 110, 66, 118)));

        assertFalse(dungeon.isSandTradePointAt(new Location(mock(World.class), 110, 66, 118)));
    }

    @Test
    void anInstanceWithNoTradePointsMatchesNothing() {
        Dungeon dungeon = dungeon(List.of());

        assertTrue(dungeon.getSandTradeLocations().isEmpty());
        assertFalse(dungeon.isSandTradePointAt(new Location(world, 110, 66, 118)));
    }
}
