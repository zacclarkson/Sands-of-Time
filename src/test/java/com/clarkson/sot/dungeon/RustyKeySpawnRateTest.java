package com.clarkson.sot.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the rusty-key spawn rate.
 *
 * <p>Rusty keys are the only way through a segment door, and nothing spawned them at all until
 * {@link DungeonManager} started rolling for them at ITEM_SPAWN locations —
 * {@code FloorItemManager.spawnRustyKey} had no production caller, so every door in the dungeon
 * was permanently locked.
 *
 * <p>The rest of {@code populateFloorItems} needs a pasted dungeon, a live world and WorldEdit, so
 * only the roll itself is unit-testable — the same split {@code GameManagerTest} uses.
 */
class RustyKeySpawnRateTest {

    @Test
    void lowRollsSpawnAKeyAndHighRollsFallThroughToLoot() {
        assertTrue(DungeonManager.spawnsRustyKey(0.0), "the lowest roll always spawns a key");
        assertFalse(DungeonManager.spawnsRustyKey(0.999), "the highest roll never does");
    }

    @Test
    void theRateIsHighEnoughToKeepDoorsOpenable() {
        // Keys are placed by chance rather than one per room, so the rate has to stay generous
        // enough that a branch is unlikely to contain no key at all. Deliberately a loose bound:
        // this guards against someone quietly turning keys into a rarity, not the exact value.
        int keys = 0;
        int samples = 1000;
        for (int i = 0; i < samples; i++) {
            if (DungeonManager.spawnsRustyKey(i / (double) samples)) keys++;
        }
        assertTrue(keys >= samples / 10,
                "at least one in ten item spawns should be a rusty key, was " + keys + "/" + samples);
        assertTrue(keys <= samples / 2,
                "rusty keys should not crowd out the loot table, was " + keys + "/" + samples);
    }
}
