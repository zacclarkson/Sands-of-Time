package com.clarkson.sot.utils;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.entities.Area;
import com.clarkson.sot.entities.Key;
import com.clarkson.sot.entities.SegmentDoor;
import com.clarkson.sot.entities.VaultDoor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the single key-item tagging scheme (issue #43).
 *
 * <p>Keys used to be tagged three different ways: {@code VaultManager} wrote {@code sot_vault_key},
 * {@link Key} wrote {@code sot_vault_key}/{@code sot_rusty_key}, and {@link ItemManager} wrote
 * {@code sot_key_type} — which is the only one the doors ever read. A key that actually spawned in
 * the dungeon therefore opened a vault but was rejected by the matching {@link VaultDoor}. These
 * tests assert that every producer now goes through {@link ItemManager} and that both door types
 * accept what it makes.
 */
class KeyItemTaggingTest {

    private ServerMock server;
    private Plugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("key-test-world");
        ItemManager.initializeKeys(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** A 1x1x1 door whose lock sits at the given block. */
    private Area boundsAt(Location loc) {
        return new Area(loc.clone(), loc.clone());
    }

    private SegmentDoor segmentDoor() {
        Location lock = at(0, 64, 0);
        return new SegmentDoor(plugin, UUID.randomUUID(), boundsAt(lock), lock, Material.DARK_OAK_PLANKS);
    }

    private VaultDoor vaultDoor(VaultColor color) {
        Location lock = at(10, 64, 10);
        return new VaultDoor(plugin, UUID.randomUUID(), boundsAt(lock), lock, color);
    }

    private PersistentDataContainer pdcOf(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer();
    }

    @Test
    void rustyKeyIsAcceptedBySegmentDoor() {
        ItemStack rusty = ItemManager.createRustyKey();

        assertTrue(ItemManager.isRustyKey(rusty), "ItemManager should recognise its own rusty key");
        assertTrue(segmentDoor().isCorrectKey(rusty), "A SegmentDoor should open with a rusty key");
        assertFalse(ItemManager.isVaultKey(rusty), "A rusty key is not a vault key");
        assertNull(ItemManager.getVaultKeyColor(rusty), "A rusty key has no vault colour");
    }

    @ParameterizedTest
    @EnumSource(VaultColor.class)
    void vaultKeyIsAcceptedOnlyByItsOwnVaultDoor(VaultColor color) {
        ItemStack key = ItemManager.createVaultKey(color);

        assertTrue(ItemManager.isVaultKey(key), "ItemManager should recognise its own vault key");
        assertEquals(color, ItemManager.getVaultKeyColor(key));
        assertTrue(vaultDoor(color).isCorrectKey(key), "The " + color + " vault should open with the " + color + " key");
        assertFalse(ItemManager.isRustyKey(key), "A vault key is not a rusty key");
        assertFalse(segmentDoor().isCorrectKey(key), "A segment door should not open with a vault key");

        for (VaultColor other : VaultColor.values()) {
            if (other == color) continue;
            assertFalse(vaultDoor(other).isCorrectKey(key),
                    "The " + other + " vault should not open with the " + color + " key");
        }
    }

    /**
     * The regression itself: the {@code Key} floor item is what a player actually picks up, and it
     * must carry the same tag the doors read.
     */
    @Test
    void floorItemKeyCheckAgreesWithItemManager() {
        ItemStack rusty = ItemManager.createRustyKey();
        ItemStack vaultKey = ItemManager.createVaultKey(VaultColor.BLUE);

        assertTrue(Key.isRustyKeyItem(rusty), "Key should recognise the rusty key ItemManager builds");
        assertFalse(Key.isRustyKeyItem(vaultKey), "A vault key is not a rusty key");
    }

    /**
     * Guards against a fourth scheme creeping back in: keys carry {@code sot_key_type} and none of
     * the retired byte tags.
     */
    @Test
    void keysCarryOnlyTheUnifiedTags() {
        NamespacedKey legacyVaultTag = new NamespacedKey(plugin, "sot_vault_key");
        NamespacedKey legacyRustyTag = new NamespacedKey(plugin, "sot_rusty_key");

        for (ItemStack key : new ItemStack[]{ItemManager.createRustyKey(), ItemManager.createVaultKey(VaultColor.GOLD)}) {
            PersistentDataContainer pdc = pdcOf(key);
            assertTrue(pdc.has(ItemManager.KEY_TYPE, PersistentDataType.STRING),
                    "Every key should carry the unified sot_key_type tag");
            assertFalse(pdc.has(legacyVaultTag, PersistentDataType.BYTE),
                    "The retired sot_vault_key tag should no longer be written");
            assertFalse(pdc.has(legacyRustyTag, PersistentDataType.BYTE),
                    "The retired sot_rusty_key tag should no longer be written");
        }
    }

    @Test
    void nonKeyItemsAreRejectedByBothDoors() {
        ItemStack plainHook = new ItemStack(Material.TRIPWIRE_HOOK);

        assertFalse(segmentDoor().isCorrectKey(plainHook), "An untagged item should not open a segment door");
        assertFalse(vaultDoor(VaultColor.RED).isCorrectKey(plainHook), "An untagged item should not open a vault");
        assertFalse(segmentDoor().isCorrectKey(null), "A null item should not open a segment door");
        assertFalse(vaultDoor(VaultColor.RED).isCorrectKey(null), "A null item should not open a vault");
    }
}
