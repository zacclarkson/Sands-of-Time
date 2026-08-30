package com.clarkson.sot.events;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the set of blocks a player may break during a round.
 *
 * <p>No mock server: {@link BreakableBlocks} is a pure lookup over the {@link Material} enum, in the
 * style of {@code VisualTimerLayout} and the static predicates on {@code GameManager}.
 *
 * <p>The whitelist is what stops a team dismantling the dungeon around itself — tunnelling past a
 * vault instead of finding its key, mining out of a death cage instead of paying a teammate's sand
 * sacrifice — so widening it is a game-design decision, and this test is the thing that makes
 * someone widening it do so on purpose.
 */
class BreakableBlocksTest {

    /** Breaking dungeon sand is the only way to add time to a team's clock. */
    @Test
    void sandIsBreakableSoTheTimerCanBeFed() {
        assertTrue(BreakableBlocks.isBreakableDuringRound(Material.SAND));
    }

    @Test
    void mobSpawnersAreBreakable() {
        assertTrue(BreakableBlocks.isBreakableDuringRound(Material.SPAWNER));
    }

    /**
     * Every material is either on the whitelist or protected — nothing may fall through both. A new
     * entry in {@link BreakableBlocks#BREAKABLE} therefore has to be added deliberately.
     */
    @Test
    void everythingElseIsProtected() {
        for (Material material : Material.values()) {
            assertEquals(BreakableBlocks.BREAKABLE.contains(material),
                    BreakableBlocks.isBreakableDuringRound(material),
                    "unclassified material: " + material);
        }
    }

    /**
     * A readable roll-call of the blocks the game itself places, so the intent survives a refactor
     * that rewrites the lookup.
     */
    @Test
    void theBlocksTheGameOwnsAreProtected() {
        Material[] gameOwned = {
                Material.GOLD_BLOCK,        // vault marker
                Material.GLASS,             // an opened vault
                Material.LODESTONE,         // sand sacrifice point
                Material.BROWN_TERRACOTTA,  // the timer column's pedestal (the TIMER marker block)
                Material.BROWN_CONCRETE,    // hub floor and walls
                Material.END_PORTAL_FRAME,  // legacy safe exit
                Material.STONE,
        };
        for (Material material : gameOwned) {
            assertFalse(BreakableBlocks.isBreakableDuringRound(material),
                    material + " belongs to the dungeon and must not be breakable");
        }
    }

    /**
     * "Blocks with money inside that you break" is a planned feature that does not exist yet. Until
     * it does, no material is a money block; when it lands, its material joins the whitelist here.
     */
    @Test
    void moneyBlocksAreNotImplementedYet() {
        assertEquals(2, BreakableBlocks.BREAKABLE.size(),
                "only sand and spawners are breakable until money blocks are implemented");
    }

    @Test
    void nullMaterialIsNotBreakable() {
        assertFalse(BreakableBlocks.isBreakableDuringRound(null));
    }

    @Test
    void theWhitelistCannotBeWidenedAtRuntime() {
        assertThrows(UnsupportedOperationException.class,
                () -> BreakableBlocks.BREAKABLE.add(Material.STONE));
    }
}
