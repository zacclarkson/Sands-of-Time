package com.clarkson.sot.events;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the generic loot table: it holds no food (hunger is frozen all round, see
 * {@link HungerListener}), the slots bread used to hold are splash potions of healing, and the
 * roll still consumes exactly the RNG draws it always did, so a dungeon seed keeps reproducing the
 * same loot.
 *
 * <p>MockBukkit is needed for {@code new ItemStack}, item meta and {@code Material.isEdible()},
 * all of which go through the server's item registry; no world is required.
 */
class FloorItemManagerLootTableTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLootTableHoldsNoFood() {
        // Sanity check that keeps the loop below from passing vacuously if the mock's item data
        // ever stopped marking anything as edible.
        assertTrue(Material.BREAD.isEdible(), "bread must register as edible for this test to mean anything");

        for (Material material : FloorItemManager.LOOT_TABLE) {
            assertFalse(material.isEdible(), material + " is food; hunger is frozen, healing is potions");
        }
    }

    @Test
    void healingPotionsReplacedBreadInTheTable() {
        long potions = 0;
        for (Material material : FloorItemManager.LOOT_TABLE) {
            if (material == Material.SPLASH_POTION) potions++;
        }
        assertEquals(2, potions, "the two bread slots became splash potions, same weight");
        assertEquals(10, FloorItemManager.LOOT_TABLE.length,
                "the roll is nextInt(LOOT_TABLE.length): a longer or shorter table silently changes"
                        + " what every seed yields");
    }

    @Test
    void splashPotionsAreHealing() {
        ItemStack stack = FloorItemManager.createLootStack(Material.SPLASH_POTION, 1);

        assertEquals(Material.SPLASH_POTION, stack.getType());
        assertEquals(1, stack.getAmount());
        assertInstanceOf(PotionMeta.class, stack.getItemMeta());
        assertEquals(PotionType.HEALING, ((PotionMeta) stack.getItemMeta()).getBasePotionType(),
                "a splash potion with no base type is an empty bottle");
    }

    @Test
    void otherMaterialsAreLeftAsPlainStacks() {
        ItemStack torches = FloorItemManager.createLootStack(Material.TORCH, 3);
        assertEquals(Material.TORCH, torches.getType());
        assertEquals(3, torches.getAmount());
    }

    /** Covers the rollLoot -> createLootStack wiring, not just the helper on its own. */
    @Test
    void rolledPotionsCarryTheHealingType() {
        boolean sawPotion = false;
        for (long seed = 0; seed < 200 && !sawPotion; seed++) {
            ItemStack rolled = FloorItemManager.rollLoot(new Random(seed));
            if (rolled.getType() != Material.SPLASH_POTION) continue;
            sawPotion = true;
            assertEquals(1, rolled.getAmount());
            assertEquals(PotionType.HEALING, ((PotionMeta) rolled.getItemMeta()).getBasePotionType());
        }
        assertTrue(sawPotion, "two slots in ten should turn up a potion well within 200 seeds");
    }

    /**
     * The population RNG is seeded and shared with the coin and sand rolls, so the number of draws
     * a loot roll consumes is part of the seed contract: one for the slot, plus one more only when a
     * torch or arrow needs a stack size.
     */
    @Test
    void everyRollConsumesTheSameDrawsAsBefore() {
        for (long seed = 0; seed < 100; seed++) {
            Random rolled = new Random(seed);
            ItemStack stack = FloorItemManager.rollLoot(rolled);

            Random expected = new Random(seed);
            expected.nextInt(FloorItemManager.LOOT_TABLE.length);
            if (stack.getType() == Material.TORCH || stack.getType() == Material.ARROW) {
                expected.nextInt(3);
            }

            assertEquals(expected.nextLong(), rolled.nextLong(),
                    "seed " + seed + " (" + stack.getType() + ") left the RNG in a different place");
        }
    }
}
