package com.clarkson.sot.dungeon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The population half of seeded generation (issue #49).
 *
 * <p>Reproducing the rooms is only half the promise: a seed has to reproduce what is on the floor
 * too. Floor population and vault rewards therefore derive their RNGs from the round seed, and both
 * derivations are pure functions pinned here — the code around them needs a pasted dungeon, a live
 * world and WorldEdit, but the seeding rules that make the whole feature work do not.
 */
class PopulationSeedTest {

    /** The first few draws of an RNG, as a comparable fingerprint of its stream. */
    private static String stream(Random random) {
        return IntStream.range(0, 8).mapToObj(i -> String.valueOf(random.nextDouble())).reduce("", String::concat);
    }

    // --- Floor population (DungeonManager) ---

    @Test
    void theSameRoundSeedPopulatesIdentically() {
        assertEquals(stream(DungeonManager.populationRandom(42L)),
                stream(DungeonManager.populationRandom(42L)),
                "the same seed must place the same rusty keys and sand");
    }

    /**
     * Every team is populated from the same sub-seed and walks the same blueprint-ordered spawn
     * lists, so every team's dungeon ends up identical — which is also the fairer behaviour for a
     * race, where one team finding a rusty key another team's identical room lacks is a real
     * advantage. Nothing team-specific goes into the derivation, and this pins that.
     */
    @Test
    void everyTeamPopulatesFromTheSameStream() {
        Random teamA = DungeonManager.populationRandom(2026L);
        Random teamB = DungeonManager.populationRandom(2026L);

        assertEquals(stream(teamA), stream(teamB), "both teams draw the same rolls");
    }

    @Test
    void differentRoundSeedsPopulateDifferently() {
        assertNotEquals(stream(DungeonManager.populationRandom(1L)),
                stream(DungeonManager.populationRandom(2L)));
    }

    /**
     * Population is salted rather than continuing the generator's own stream, so it cannot be
     * perturbed by how many draws layout generation happened to consume — a validation retry changes
     * that count. A seed of N must therefore not simply mean "new Random(N)".
     */
    @Test
    void populationIsSaltedAwayFromTheRawSeed() {
        assertNotEquals(stream(new Random(7L)), stream(DungeonManager.populationRandom(7L)));
    }

    @Test
    void anUnseededRoundStillGetsAUsableRng() {
        // Constructing a DungeonManager outside GameManager.startGame leaves no round seed; that
        // must degrade to random population rather than throwing.
        assertNotNull(DungeonManager.populationRandom(null));
        assertDoesNotThrow(() -> DungeonManager.populationRandom(null).nextDouble());
    }

    // --- Vault rewards (VaultManager) ---

    @Test
    void vaultRewardsAreTheSameForEveryTeam() {
        // spawnVaultRewards runs when a player opens a vault, so the derivation must depend only on
        // the seed and the colour: anything carried between calls would make the reward depend on
        // which team got there first.
        assertEquals(VaultManager.vaultRewardSeed(99L, VaultColor.GOLD),
                VaultManager.vaultRewardSeed(99L, VaultColor.GOLD));
    }

    @Test
    void eachVaultColourDrawsFromItsOwnStream() {
        Set<Long> seeds = new HashSet<>();
        for (VaultColor color : VaultColor.values()) {
            seeds.add(VaultManager.vaultRewardSeed(99L, color));
        }

        assertEquals(VaultColor.values().length, seeds.size(),
                "adjacent colours must not collide or correlate, got: " + seeds);
    }

    @Test
    void differentRoundSeedsGiveDifferentVaultRewards() {
        assertNotEquals(VaultManager.vaultRewardSeed(1L, VaultColor.RED),
                VaultManager.vaultRewardSeed(2L, VaultColor.RED));
    }

    @Test
    void vaultRewardsDoNotShareFloorPopulationsStream() {
        // Two sub-seeds derived from one round seed must not be the same stream, or the vault reward
        // would be a replay of the floor rolls.
        long seed = 12345L;
        assertNotEquals(stream(DungeonManager.populationRandom(seed)),
                stream(new Random(VaultManager.vaultRewardSeed(seed, VaultColor.BLUE))));
    }

    @Test
    void vaultRewardsSurviveAnUnseededRound() {
        assertDoesNotThrow(() -> VaultManager.vaultRewardSeed(null, VaultColor.BLUE));
        assertNotEquals(VaultManager.vaultRewardSeed(null, VaultColor.BLUE),
                VaultManager.vaultRewardSeed(null, VaultColor.GOLD),
                "colours stay distinct even with no seeded round in progress");
    }
}
