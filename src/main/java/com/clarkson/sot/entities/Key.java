package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Represents a vault key or rusty key on the dungeon floor.
 * Players pick it up by walking near it; it goes into their inventory.
 * Uses an ItemDisplay entity for visual representation.
 */
public class Key implements FloorItem {

    private final Plugin plugin;
    private final UUID uniqueId;
    private final Location location;
    private ItemDisplay itemDisplay;

    private final UUID teamId;
    private final UUID segmentInstanceId;
    private final int depth;

    /** Null for rusty keys, set for vault keys. */
    private final VaultColor vaultColor;
    private final ItemStack keyItemStack;
    private boolean isPickedUp;

    // Static PDC keys for the ItemDisplay entity (shared with the CoinStack/FloorLoot pattern).
    // The key ItemStack itself is tagged by ItemManager, which owns the single key-item scheme.
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;
    private static NamespacedKey TYPE_KEY;

    public static void initializeKeys(Plugin pluginInstance) {
        if (UUID_KEY == null) {
            UUID_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_uuid");
            TEAM_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_team");
            TYPE_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_type");
        }
    }

    /**
     * Creates a key floor item. Pass a {@code vaultColor} for a coloured vault key, or null for a
     * rusty key (see {@link #createRustyKey}).
     */
    public Key(Plugin plugin, Location location, @Nullable VaultColor vaultColor, UUID teamId,
               UUID segmentInstanceId, int depth) {
        if (UUID_KEY == null) {
            throw new IllegalStateException("Key NamespacedKeys not initialized! Call Key.initializeKeys() in onEnable.");
        }
        this.plugin = Objects.requireNonNull(plugin);
        this.location = Objects.requireNonNull(location).clone();
        this.vaultColor = vaultColor; // null = rusty key
        this.teamId = Objects.requireNonNull(teamId);
        this.segmentInstanceId = Objects.requireNonNull(segmentInstanceId);
        this.depth = depth;
        this.uniqueId = UUID.randomUUID();
        this.isPickedUp = false;
        this.keyItemStack = createKeyItemStack();
        spawnRepresentation();
    }

    /**
     * Factory method for creating a rusty key.
     */
    public static Key createRustyKey(Plugin plugin, Location location, UUID teamId, UUID segmentInstanceId, int depth) {
        return new Key(plugin, location, null, teamId, segmentInstanceId, depth);
    }

    /**
     * Builds the key ItemStack via {@link ItemManager} so a picked-up key carries the same tags
     * the doors and the vault check for.
     */
    private ItemStack createKeyItemStack() {
        return (vaultColor != null) ? ItemManager.createVaultKey(vaultColor) : ItemManager.createRustyKey();
    }

    /** Scale shared with {@link CoinStack} and {@link FloorLoot}, so all floor pickups read alike. */
    static final float DISPLAY_SCALE = 0.7f;

    /**
     * Where the visual sits relative to the block the key was placed on: centred horizontally and
     * just above the floor surface, the same offset {@link CoinStack} and {@link FloorLoot} use.
     * {@code FloorItemManager} measures pickup range to this point, so the two must agree.
     *
     * <p>Package-visible so the offset can be asserted without a live server.
     */
    static Location displayLocationFor(Location blockLocation) {
        return blockLocation.clone().add(0.5, 0.1, 0.5);
    }

    /**
     * Applies the presentation shared by every floor item: no gravity, not saved across restarts,
     * invulnerable, and laid flat on the block at {@link #DISPLAY_SCALE}.
     *
     * <p>Package-visible so the presentation can be asserted against a mock display: the mock server
     * the unit tests run on cannot spawn real display entities.
     */
    static void applyFloorItemStyle(ItemDisplay display) {
        display.setGravity(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        // FIXED (the default) so the flat 90-deg rotation below is honored; a CENTER
        // billboard would always face the camera and cancel the transform's rotation.
        display.setBillboard(Display.Billboard.FIXED);

        // Lie flat on the ground: 90 deg around X tips the upright item face-down.
        // No downward translation: the spawn offset already rests it on the surface.
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f),
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    /**
     * Spawns the ItemDisplay representing this key. Presentation matches {@link CoinStack} and
     * {@link FloorLoot} so every floor pickup reads the same way: laid flat on the marked block,
     * scaled down, and tagged for identification.
     */
    private void spawnRepresentation() {
        ItemStack displayStack = keyItemStack.clone();
        Location spawnLocation = displayLocationFor(this.location);

        try {
            this.itemDisplay = this.location.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                applyFloorItemStyle(display);

                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(UUID_KEY, PersistentDataType.STRING, this.uniqueId.toString());
                pdc.set(TEAM_KEY, PersistentDataType.STRING, this.teamId.toString());
                pdc.set(TYPE_KEY, PersistentDataType.STRING, "KEY");
            });
            plugin.getLogger().finer("Spawned Key visual " + uniqueId + " (" + describe() + ") at " + location.toVector());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay for Key " + uniqueId, e);
            // Deliberately NOT marking this picked up the way FloorLoot does. A key gates a vault:
            // an invisible but still collectable key is recoverable, an uncollectable one softlocks
            // that vault for the whole round.
        }
    }

    // --- FloorItem Interface ---

    @Override public UUID getUniqueId() { return this.uniqueId; }
    @Override public Location getLocation() { return this.location.clone(); }
    @Override public ItemStack getItemStack() { return this.keyItemStack.clone(); }
    @Override public Entity getVisualEntity() {
        return (this.itemDisplay != null && this.itemDisplay.isValid()) ? this.itemDisplay : null;
    }
    @Override public int getDepth() { return this.depth; }
    @Override public UUID getTeamId() { return this.teamId; }
    @Override public UUID getSegmentInstanceId() { return this.segmentInstanceId; }
    @Override public boolean isPickedUp() { return this.isPickedUp; }

    @Override
    public void handlePickup(Player player) {
        if (this.isPickedUp) return;
        this.isPickedUp = true;
        removeRepresentation();

        // Add the key to the player's inventory
        var leftover = player.getInventory().addItem(keyItemStack.clone());
        if (!leftover.isEmpty()) {
            // Drop at player's feet if inventory is full
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        player.sendActionBar(Component.text("Picked up " + describe() + "!", NamedTextColor.YELLOW));
        plugin.getLogger().fine("Key " + uniqueId + " picked up by " + player.getName());
    }

    @Override
    public void removeRepresentation() {
        if (this.itemDisplay != null) {
            if (this.itemDisplay.isValid()) {
                this.itemDisplay.remove();
            }
            this.itemDisplay = null;
        }
    }

    // --- Key-specific methods ---

    public VaultColor getVaultColor() { return this.vaultColor; }
    public boolean isRustyKey() { return this.vaultColor == null; }

    /** Human-readable name used in log lines and the pickup action bar. */
    private String describe() {
        return (vaultColor != null) ? vaultColor.name() + " Vault Key" : "Rusty Key";
    }

    public static boolean isRustyKeyItem(ItemStack item) {
        return ItemManager.isRustyKey(item);
    }
}
