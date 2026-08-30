package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers {@link DungeonManager#toAbsoluteBounds} and {@link DungeonManager#regionContains}, the
 * coordinate maths and membership test behind an instance's
 * {@code absoluteBounds} — the region {@code cleanupInstance()} air-fills and the region
 * {@code GameManager.getTeamIdForLocation} resolves a team from (issue #98).
 *
 * <p>Both callers depend on it naming the same box, so an off-by-one here would either leave blocks
 * standing between rounds or hand a location to the wrong team. Pure maths: no server, only a
 * mocked World so the resulting Locations have one to carry.
 */
class DungeonManagerBoundsTest {

    private final World world = mock(World.class);
    private final World otherWorld = mock(World.class);

    /** A blueprint-relative box, world-less exactly as {@code DungeonBlueprint} stores it. */
    private static Area relativeBounds(int maxX, int maxY, int maxZ) {
        return new Area(new Location(null, 0, 0, 0), new Location(null, maxX, maxY, maxZ));
    }

    @Test
    void blueprintBoundsAreTranslatedByTheDungeonOrigin() {
        Location origin = new Location(world, 100, 64, -20);
        Area absolute = DungeonManager.toAbsoluteBounds(origin, relativeBounds(41, 16, 36));

        assertEquals(100, absolute.getMinPoint().getBlockX(), "min x");
        assertEquals(64, absolute.getMinPoint().getBlockY(), "min y");
        assertEquals(-20, absolute.getMinPoint().getBlockZ(), "min z");
        assertEquals(141, absolute.getMaxPoint().getBlockX(), "max x");
        assertEquals(80, absolute.getMaxPoint().getBlockY(), "max y");
        assertEquals(16, absolute.getMaxPoint().getBlockZ(), "max z");
    }

    /** The relative points carry a null world; the absolute ones must carry the dungeon's. */
    @Test
    void absoluteBoundsCarryTheDungeonWorld() {
        Area absolute = DungeonManager.toAbsoluteBounds(new Location(world, 0, 0, 0), relativeBounds(4, 4, 4));
        assertSame(world, absolute.getMinPoint().getWorld());
        assertSame(world, absolute.getMaxPoint().getWorld());
    }

    /** The origin passed in is the instance's own field, reused for every feature it places. */
    @Test
    void translatingDoesNotMoveTheOrigin() {
        Location origin = new Location(world, 5000, 64, 0);
        DungeonManager.toAbsoluteBounds(origin, relativeBounds(10, 10, 10));
        assertEquals(5000, origin.getBlockX());
        assertEquals(64, origin.getBlockY());
        assertEquals(0, origin.getBlockZ());
    }

    /**
     * The bounds are inclusive of the far corner: the blueprint's max point is a real block of the
     * dungeon, and excluding it would leave a face of every region unowned and uncleared.
     */
    @Test
    void bothCornersAreInsideTheRegion() {
        Area absolute = DungeonManager.toAbsoluteBounds(new Location(world, 0, 64, 0), relativeBounds(41, 16, 36));

        assertTrue(absolute.contains(new Location(world, 0, 64, 0)), "min corner");
        assertTrue(absolute.contains(new Location(world, 41, 80, 36)), "max corner");
        assertFalse(absolute.contains(new Location(world, 42, 80, 36)), "one block past the max corner");
        assertFalse(absolute.contains(new Location(world, 0, 63, 0)), "one block below the min corner");
    }

    /**
     * Teams are spaced {@code TEAM_DUNGEON_SPACING} (5000 blocks on X) apart, which is what makes a
     * region-level team lookup unambiguous. A hub is tens of blocks wide, so the regions are nowhere
     * near touching — pinned here because the lookup's correctness rests on it.
     */
    @Test
    void adjacentTeamRegionsDoNotOverlap() {
        Area first = DungeonManager.toAbsoluteBounds(new Location(world, 0, 64, 0), relativeBounds(41, 16, 36));
        Area second = DungeonManager.toAbsoluteBounds(new Location(world, 5000, 64, 0), relativeBounds(41, 16, 36));

        assertFalse(first.intersects(second));
        assertFalse(first.contains(second.getMinPoint()));
        assertFalse(second.contains(first.getMaxPoint()));
    }

    /**
     * {@link com.clarkson.sot.entities.Area} compares coordinates only, and every world shares a
     * coordinate space — so without the world test a location standing at the same numbers in
     * another world would be claimed by the team, which is exactly where an operator ends up.
     */
    @Test
    void matchingCoordinatesInAnotherWorldAreOutsideTheRegion() {
        Area region = DungeonManager.toAbsoluteBounds(new Location(world, 0, 64, 0), relativeBounds(41, 16, 36));

        assertTrue(DungeonManager.regionContains(world, region, new Location(world, 20, 70, 20)));
        assertFalse(DungeonManager.regionContains(world, region, new Location(otherWorld, 20, 70, 20)));
    }

    /** Callers hand this raw event data, so a null location must not blow up an event handler. */
    @Test
    void aNullLocationIsOutsideTheRegion() {
        Area region = DungeonManager.toAbsoluteBounds(new Location(world, 0, 64, 0), relativeBounds(41, 16, 36));
        assertFalse(DungeonManager.regionContains(world, region, null));
    }

    /**
     * Membership is the whole region, not the placed segments in it: the gap between two rooms is
     * still this team's dungeon, and is cleared as such at teardown.
     */
    @Test
    void theSpaceBetweenRoomsStillBelongsToTheRegion() {
        Area region = DungeonManager.toAbsoluteBounds(new Location(world, 0, 64, 0), relativeBounds(41, 16, 36));
        assertTrue(DungeonManager.regionContains(world, region, new Location(world, 20, 79, 18)));
    }
}
