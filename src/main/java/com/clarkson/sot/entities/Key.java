package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.VaultColor;
import com.clarkson.sot.utils.ItemManager;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

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

    // Static PDC keys for the ItemDisplay entity (shared with CoinStack pattern).
    // The key ItemStack itself is tagged by ItemManager, which owns the single key-item scheme.
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;

    public static void initializeKeys(Plugin pluginInstance) {
        if (UUID_KEY == null) {
            UUID_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_uuid");
            TEAM_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_team");
        }
    }

    /**
     * Creates a vault key floor item.
     */
    public Key(Plugin plugin, Location location, VaultColor vaultColor, UUID teamId, UUID segmentInstanceId, int depth) {
        this(plugin, location, vaultColor, teamId, segmentInstanceId, depth, false);
    }

    /**
     * Creates a rusty key floor item (pass null for vaultColor).
     */
    private Key(Plugin plugin, Location location, VaultColor vaultColor, UUID teamId, UUID segmentInstanceId, int depth, boolean unused) {
        if (UUID_KEY == null) {
            throw new IllegalStateException("Key NamespacedKeys not initialized! Call Key.initializeKeys() in onEnable.");
        }
        this.plugin = Objects.requireNonNull(plugin);
        this.location = Objects.requireNonNull(location);
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
        return new Key(plugin, location, null, teamId, segmentInstanceId, depth, false);
    }

    /**
     * Builds the key ItemStack via {@link ItemManager} so a picked-up key carries the same tags
     * the doors and the vault check for.
     */
    private ItemStack createKeyItemStack() {
        return (vaultColor != null) ? ItemManager.createVaultKey(vaultColor) : ItemManager.createRustyKey();
    }

    private void spawnRepresentation() {
        ItemStack displayStack = keyItemStack.clone();
        Location spawnLocation = this.location.clone().add(0.5, 0.1, 0.5);

        try {
            this.itemDisplay = this.location.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
                display.setItemStack(displayStack);
                display.setGravity(false);
                display.setPersistent(false);
                display.setInvulnerable(true);

                PersistentDataContainer pdc = display.getPersistentDataContainer();
                pdc.set(UUID_KEY, PersistentDataType.STRING, this.uniqueId.toString());
                pdc.set(TEAM_KEY, PersistentDataType.STRING, this.teamId.toString());
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn ItemDisplay for Key " + uniqueId, e);
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

        String keyName = (vaultColor != null) ? vaultColor.name() + " Vault Key" : "Rusty Key";
        player.sendActionBar(Component.text("Picked up " + keyName + "!", NamedTextColor.YELLOW));
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

    public static boolean isRustyKeyItem(ItemStack item) {
        return ItemManager.isRustyKey(item);
    }
}
