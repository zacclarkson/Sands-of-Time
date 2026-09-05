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
 * Pins the MOB_SPAWNER marker's trip through the blueprint and the per-team {@link Dungeon}.
 *
 * <p>The markers were fully placeable and fully saved long before anything read them back (#46):
 * {@code Segment.getMobSpawnerLocations()} existed, but {@link DungeonBlueprint} and {@link Dungeon}
 * had no field for them at all, so no mob could ever spawn. These tests hold that channel open.
 *
 * <p>No server needed — {@code World} is a plain mock and Location/Vector are value types.
 */
class DungeonMobSpawnerTest {

    private final World world = mock(World.class);

    private DungeonBlueprint blueprint(List<Vector> mobSpawners) {
        Area bounds = new Area(new Location(null, 0, 0, 0), new Location(null, 15, 7, 15));
        return new DungeonBlueprint(List.of(), new Vector(0, 0, 0), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), bounds, null, null, null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), mobSpawners, List.of());
    }

    private Dungeon dungeon(List<Location> mobSpawners) {
        Location origin = new Location(world, 100, 64, 100);
        return new Dungeon(UUID.randomUUID(), world, origin, blueprint(List.of()),
                origin.clone(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), List.of(), mobSpawners, List.of(), List.of());
    }

    @Test
    void blueprintCarriesMobSpawnerOffsets() {
        DungeonBlueprint blueprint = blueprint(List.of(new Vector(4, 1, 7), new Vector(20, 1, 3)));

        assertEquals(List.of(new Vector(4, 1, 7), new Vector(20, 1, 3)),
                blueprint.getMobSpawnerRelativeLocations());
    }

    @Test
    void blueprintAcceptsTemplatesWithNoMobSpawners() {
        // The bundled hub declares "mobSpawnerLocations": [], and older templates predate the
        // marker entirely — neither may fail blueprint construction.
        assertTrue(blueprint(List.of()).getMobSpawnerRelativeLocations().isEmpty());
    }

    @Test
    void blueprintDoesNotExposeAMutableList() {
        DungeonBlueprint blueprint = blueprint(List.of(new Vector(4, 1, 7)));

        assertThrows(UnsupportedOperationException.class,
                () -> blueprint.getMobSpawnerRelativeLocations().add(new Vector(0, 0, 0)));
    }

    @Test
    void blueprintCopiesTheCallersList() {
        List<Vector> source = new java.util.ArrayList<>(List.of(new Vector(4, 1, 7)));
        DungeonBlueprint blueprint = blueprint(source);

        source.add(new Vector(99, 99, 99));

        assertEquals(1, blueprint.getMobSpawnerRelativeLocations().size(),
                "the blueprint must not alias the list it was handed");
    }

    @Test
    void dungeonCarriesAbsoluteMobSpawnerLocations() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 104, 65, 107)));

        assertEquals(List.of(new Location(world, 104, 65, 107)), dungeon.getMobSpawnerLocations());
    }

    @Test
    void dungeonDoesNotExposeAMutableList() {
        Dungeon dungeon = dungeon(List.of(new Location(world, 104, 65, 107)));

        assertThrows(UnsupportedOperationException.class,
                () -> dungeon.getMobSpawnerLocations().add(new Location(world, 0, 0, 0)));
    }

    @Test
    void dungeonAcceptsAnInstanceWithNoMobSpawners() {
        assertTrue(dungeon(List.of()).getMobSpawnerLocations().isEmpty());
    }
}
