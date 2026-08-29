package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link Key} as a floor item — the class vault keys now spawn as, instead of the dropped
 * Item entity {@code VaultManager} used to create.
 *
 * <p>The mock server cannot spawn display entities, so the presentation is asserted through the
 * {@link Key#applyFloorItemStyle} / {@link Key#displayLocationFor} seams against a mock display,
 * rather than by reading back a spawned one.
 */
class KeyFloorItemTest {

    private ServerMock server;
    private Plugin plugin;
    private World world;

    private final UUID teamId = UUID.randomUUID();
    private final UUID segmentInstanceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("key-floor-item-world");
        ItemManager.initializeKeys(plugin);
        Key.initializeKeys(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private Key vaultKeyAt(VaultColor color, Location loc) {
        return new Key(plugin, loc, color, teamId, segmentInstanceId, 0);
    }

    /**
     * The point of the whole key pipeline: what lands in the inventory must be what the vault and the
     * doors accept. {@code VaultManager.consumeKeyItem} and {@code VaultDoor.isCorrectKey} both match
     * on the ItemManager PDC tags.
     */
    @ParameterizedTest
    @EnumSource(VaultColor.class)
    void aPickedUpVaultKeyIsTheItemTheVaultAccepts(VaultColor color) {
        Key key = vaultKeyAt(color, at(10, 64, 10));
        Player player = server.addPlayer();

        key.handlePickup(player);

        ItemStack received = player.getInventory().getItem(0);
        assertNotNull(received, "the key should land in the player's inventory");
        assertTrue(ItemManager.isVaultKey(received), "a picked-up vault key must be tagged as one");
        assertEquals(color, ItemManager.getVaultKeyColor(received), "the colour must survive pickup");
        assertTrue(key.isPickedUp());
        assertFalse(key.isRustyKey());
        assertEquals(color, key.getVaultColor());
    }

    @Test
    void aRustyKeyIsTaggedRusty() {
        Key key = Key.createRustyKey(plugin, at(4, 64, 4), teamId, segmentInstanceId, 0);
        Player player = server.addPlayer();

        key.handlePickup(player);

        ItemStack received = player.getInventory().getItem(0);
        assertNotNull(received, "the key should land in the player's inventory");
        assertTrue(ItemManager.isRustyKey(received), "a picked-up rusty key must be tagged as one");
        assertFalse(ItemManager.isVaultKey(received), "a rusty key is not a vault key");
        assertTrue(key.isRustyKey());
        assertNull(key.getVaultColor());
    }

    /**
     * {@code FloorItemManager.onPlayerMove} measures proximity from {@code getLocation()}, so this is
     * the block the key is collected at. It must stay the block the builder marked, not the offset
     * position the display is drawn at.
     */
    @Test
    void getLocationReportsTheMarkedBlockAndIsADefensiveCopy() {
        Location marked = at(7, 64, 3);
        Key key = vaultKeyAt(VaultColor.GOLD, marked);

        assertEquals(marked, key.getLocation());

        key.getLocation().add(100, 100, 100);
        assertEquals(marked, key.getLocation(), "getLocation() must hand out a copy");

        marked.add(50, 0, 0);
        assertEquals(at(7, 64, 3), key.getLocation(), "the constructor must copy the location it is given");
    }

    @Test
    void pickupIsIdempotent() {
        Key key = vaultKeyAt(VaultColor.RED, at(1, 64, 1));
        Player player = server.addPlayer();

        key.handlePickup(player);
        key.handlePickup(player);

        int keysHeld = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (ItemManager.isVaultKey(stack)) keysHeld += stack.getAmount();
        }
        assertEquals(1, keysHeld, "picking a key up twice must not duplicate it");
    }

    @Test
    void getItemStackHandsOutACopy() {
        Key key = vaultKeyAt(VaultColor.GREEN, at(2, 64, 2));

        ItemStack first = key.getItemStack();
        first.setAmount(64);

        assertEquals(1, key.getItemStack().getAmount(), "getItemStack() must not expose internal state");
    }

    /**
     * The display is laid flat on the marked block, scaled to match {@link CoinStack} and
     * {@link FloorLoot} so every floor pickup reads the same way, and billboarded FIXED so that flat
     * rotation is not cancelled by the display turning to face the camera.
     *
     * <p>Asserted against a mock display rather than a spawned one: the mock server cannot spawn
     * display entities, so {@link Key#applyFloorItemStyle} is the seam.
     */
    @Test
    void theVisualMatchesTheOtherFloorItems() {
        ItemDisplay display = mock(ItemDisplay.class);

        Key.applyFloorItemStyle(display);

        verify(display).setGravity(false);
        verify(display).setPersistent(false);
        verify(display).setInvulnerable(true);
        verify(display).setBillboard(Display.Billboard.FIXED);

        ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
        verify(display).setTransformation(captor.capture());
        Transformation transformation = captor.getValue();

        assertEquals(Key.DISPLAY_SCALE, transformation.getScale().x(), 1e-6f,
                "keys use the same scale as coins and loot");
        assertEquals(Key.DISPLAY_SCALE, transformation.getScale().y(), 1e-6f);
        assertEquals(Key.DISPLAY_SCALE, transformation.getScale().z(), 1e-6f);
        assertEquals((float) Math.toRadians(90), transformation.getLeftRotation().angle(), 1e-4f,
                "keys lie flat, like coins and loot");
        assertEquals(0f, transformation.getTranslation().y(), 1e-6f,
                "a downward translation would sink the key into the floor (see #78)");
    }

    /**
     * {@code FloorItemManager} measures pickup range to the visual, so where the visual sits relative
     * to the marked block is a shared contract, not a private detail of this class.
     */
    @Test
    void theVisualSitsCentredOnTheMarkedBlock() {
        Location block = at(3, 64, 9);

        Location visual = Key.displayLocationFor(block);

        assertEquals(world, visual.getWorld());
        assertEquals(3.5, visual.getX(), 1e-9, "centred in the block on X");
        assertEquals(64.1, visual.getY(), 1e-9, "resting just above the floor surface");
        assertEquals(9.5, visual.getZ(), 1e-9, "centred in the block on Z");
        assertEquals(at(3, 64, 9), block, "displayLocationFor must not mutate its argument");
    }

    /**
     * A key gates a vault, so a key whose visual failed to spawn must stay collectable — an
     * uncollectable one would softlock that vault for the whole round.
     */
    @Test
    void aKeyIsCollectableEvenIfItsVisualFailedToSpawn() {
        Key key = vaultKeyAt(VaultColor.GOLD, at(6, 64, 6));

        assertFalse(key.isPickedUp(), "a freshly spawned key must be collectable");
    }
}
