package com.clarkson.sot.main;

import com.clarkson.sot.dungeon.DungeonManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link GameManager#getTeamIdForLocation}, which was a stub returning null for every location
 * — a silent "no team" that no caller could distinguish from a genuine miss (issue #98).
 *
 * <p>This is the dispatch half: which team a matching region belongs to. Get it wrong and a location
 * resolves to whichever team the map happened to iterate first, which a single-team test would never
 * show. The region maths it dispatches on is pinned separately by {@code DungeonManagerBoundsTest};
 * the instances here are mocked, so a bug in one cannot mask a bug in the other.
 */
class TeamIdForLocationTest {

    private final World dungeonWorld = mock(World.class);

    private static DungeonManager instanceContaining(Location... locations) {
        DungeonManager manager = mock(DungeonManager.class);
        for (Location location : locations) {
            when(manager.containsLocation(location)).thenReturn(true);
        }
        return manager;
    }

    @Test
    void aLocationResolvesToTheTeamWhoseRegionHoldsIt() {
        Location inRed = new Location(dungeonWorld, 20, 64, 20);
        Location inBlue = new Location(dungeonWorld, 5020, 64, 20);
        UUID red = UUID.randomUUID();
        UUID blue = UUID.randomUUID();

        Map<UUID, DungeonManager> managers = new LinkedHashMap<>();
        managers.put(red, instanceContaining(inRed));
        managers.put(blue, instanceContaining(inBlue));

        assertEquals(red, GameManager.teamIdForLocation(managers, inRed));
        // The second entry, so a loop that returned the first match unconditionally would fail here.
        assertEquals(blue, GameManager.teamIdForLocation(managers, inBlue));
    }

    /** The lobby and the trapped box sit outside every generated region; null is the honest answer. */
    @Test
    void aLocationOutsideEveryDungeonHasNoTeam() {
        Map<UUID, DungeonManager> managers = new LinkedHashMap<>();
        managers.put(UUID.randomUUID(), instanceContaining());
        managers.put(UUID.randomUUID(), instanceContaining());

        assertNull(GameManager.teamIdForLocation(managers, new Location(dungeonWorld, -900, 64, -900)));
    }

    @Test
    void noTeamsMeansNoTeam() {
        assertNull(GameManager.teamIdForLocation(Map.of(), new Location(dungeonWorld, 0, 64, 0)));
    }

    /** Callers hand this raw event data; a null location must not blow up an event handler. */
    @Test
    void aNullLocationHasNoTeam() {
        Map<UUID, DungeonManager> managers = new LinkedHashMap<>();
        managers.put(UUID.randomUUID(), instanceContaining());
        assertNull(GameManager.teamIdForLocation(managers, null));
    }
}
