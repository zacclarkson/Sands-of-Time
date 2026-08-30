package com.clarkson.sot.dungeon;

import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the depth-to-difficulty curve of {@link MobManager#mobsForDepth(int, Random)}.
 *
 * <p>GAME_RULES asks for mobs that get more dangerous "especially in deeper segments", which is the
 * one part of mob spawning that is worth testing exhaustively and the only part that needs no
 * server — everything else in {@code MobManager} wants a live world and a pasted dungeon. Same
 * split as {@code DungeonManager.spawnsRustyKey} / {@code RustyKeySpawnRateTest}.
 */
class MobSpawnScalingTest {

    /** Dungeon depth runs 0 (hub) to 10; sample past the end to prove the deep band is open-ended. */
    private static final int MAX_DEPTH = 12;

    private static final long SEED = 20260830L;

    @Test
    void everyDepthProducesAtLeastOneMob() {
        Random random = new Random(SEED);
        for (int depth = 0; depth <= MAX_DEPTH; depth++) {
            List<Class<? extends Mob>> mobs = MobManager.mobsForDepth(depth, random);
            assertNotNull(mobs, "depth " + depth + " returned null");
            assertFalse(mobs.isEmpty(), "depth " + depth + " returned no mobs");
            assertTrue(mobs.stream().allMatch(java.util.Objects::nonNull),
                    "depth " + depth + " returned a null mob class");
        }
    }

    @Test
    void groupSizeGrowsWithDepthAndNeverShrinks() {
        Random random = new Random(SEED);
        int previous = 0;
        for (int depth = 0; depth <= MAX_DEPTH; depth++) {
            int size = MobManager.mobsForDepth(depth, random).size();
            assertTrue(size >= previous,
                    "group size dropped from " + previous + " to " + size + " at depth " + depth);
            previous = size;
        }
    }

    @Test
    void groupSizeMatchesTheThreeBands() {
        Random random = new Random(SEED);
        assertEquals(1, MobManager.mobsForDepth(0, random).size(), "hub");
        assertEquals(1, MobManager.mobsForDepth(2, random).size(), "last shallow depth");
        assertEquals(2, MobManager.mobsForDepth(3, random).size(), "first mid depth");
        assertEquals(2, MobManager.mobsForDepth(5, random).size(), "last mid depth");
        assertEquals(3, MobManager.mobsForDepth(6, random).size(), "first deep depth");
        assertEquals(3, MobManager.mobsForDepth(10, random).size(), "max depth");
    }

    @Test
    void shallowDepthsOnlyDrawFromTheEasyPool() {
        Set<Class<? extends Mob>> seen = typesSeenAcross(0, 2);

        assertEquals(Set.of(Zombie.class, Skeleton.class), seen,
                "segments near the hub must not contain spiders");
    }

    @Test
    void spidersAppearOnlyFromTheMidBand() {
        assertFalse(typesSeenAcross(0, 2).contains(Spider.class), "shallow");
        assertTrue(typesSeenAcross(3, 5).contains(Spider.class), "mid");
    }

    @Test
    void caveSpidersAreReservedForTheDeepestSegments() {
        assertFalse(typesSeenAcross(0, 5).contains(CaveSpider.class),
                "cave spiders must not appear above the deep band");
        assertTrue(typesSeenAcross(6, MAX_DEPTH).contains(CaveSpider.class),
                "the deep band should be able to roll a cave spider");
    }

    @Test
    void negativeDepthIsTreatedAsTheHub() {
        Random random = new Random(SEED);
        // getDepthAtLocation returns 0 for anything it cannot place, but guard the band arithmetic
        // anyway so an unexpected negative cannot select an empty pool and throw.
        assertEquals(1, MobManager.mobsForDepth(-1, random).size());
    }

    @Test
    void isDeterministicForAGivenSeed() {
        List<Class<? extends Mob>> first = MobManager.mobsForDepth(7, new Random(SEED));
        List<Class<? extends Mob>> second = MobManager.mobsForDepth(7, new Random(SEED));

        assertEquals(first, second, "same seed must produce the same group");
    }

    @Test
    void theReturnedListIsImmutable() {
        List<Class<? extends Mob>> mobs = MobManager.mobsForDepth(0, new Random(SEED));

        assertThrows(UnsupportedOperationException.class, () -> mobs.add(Zombie.class));
    }

    /** Every mob class that turns up across a depth range, sampled enough to cover the pool. */
    private Set<Class<? extends Mob>> typesSeenAcross(int minDepth, int maxDepth) {
        Random random = new Random(SEED);
        Set<Class<? extends Mob>> seen = new HashSet<>();
        for (int depth = minDepth; depth <= maxDepth; depth++) {
            for (int i = 0; i < 200; i++) {
                seen.addAll(MobManager.mobsForDepth(depth, random));
            }
        }
        return seen;
    }
}
