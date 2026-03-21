package com.clarkson.sot.entities;

import com.clarkson.sot.dungeon.VaultColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
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

    // Static PDC keys (shared with CoinStack pattern)
    private static NamespacedKey UUID_KEY;
    private static NamespacedKey TEAM_KEY;
    private static NamespacedKey VAULT_KEY_TAG;
    private static NamespacedKey VAULT_COLOR_TAG;
    private static NamespacedKey RUSTY_KEY_TAG;

    public static void initializeKeys(Plugin pluginInstance) {
        if (UUID_KEY == null) {
            UUID_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_uuid");
            TEAM_KEY = new NamespacedKey(pluginInstance, "sot_floor_item_team");
            VAULT_KEY_TAG = new NamespacedKey(pluginInstance, "sot_vault_key");
            VAULT_COLOR_TAG = new NamespacedKey(pluginInstance, "sot_vault_color");
            RUSTY_KEY_TAG = new NamespacedKey(pluginInstance, "sot_rusty_key");
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

    private ItemStack createKeyItemStack() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            if (vaultColor != null) {
                // Vault key
                TextColor textColor = getTextColor(vaultColor);
                meta.displayName(Component.text(vaultColor.name() + " Vault Key", textColor, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("Used to unlock the " + vaultColor.name().toLowerCase() + " vault", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Sands of Time Item", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(VAULT_KEY_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(VAULT_COLOR_TAG, PersistentDataType.STRING, vaultColor.name());
            } else {
                // Rusty key
                meta.displayName(Component.text("Rusty Key", NamedTextColor.GRAY, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("Opens a locked door in the dungeon", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Sands of Time Item", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(RUSTY_KEY_TAG, PersistentDataType.BYTE, (byte) 1);
            }
            key.setItemMeta(meta);
        }
        return key;
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
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(RUSTY_KEY_TAG, PersistentDataType.BYTE);
    }

    public static NamespacedKey getRustyKeyTag() { return RUSTY_KEY_TAG; }

    private static TextColor getTextColor(VaultColor color) {
        switch (color) {
            case BLUE: return NamedTextColor.BLUE;
            case RED: return NamedTextColor.RED;
            case GREEN: return NamedTextColor.GREEN;
            case GOLD: return NamedTextColor.GOLD;
            default: return NamedTextColor.WHITE;
        }
    }
}
