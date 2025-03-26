package com.clarkson.sot.entities;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class FloorItem {

    private final Location location;
    private final ItemStack item;
    private ItemDisplay itemDisplay;
    private boolean isPickedUp;
    private final UUID uniqueId; // Unique identifier for this FloorItem
    private final Plugin plugin;

    public FloorItem(Location location, ItemStack item, Plugin plugin) {
        this.location = location;
        this.item = item;
        this.plugin = plugin;
        this.uniqueId = UUID.randomUUID(); // Generate a unique ID for this item
        spawnItemRepresentation();
    }

    private void spawnItemRepresentation() {
        Location spawnLocation = this.location.clone().add(0.5, -0.8, 0.5);

        // Spawn the ItemDisplay entity
        this.itemDisplay = (ItemDisplay) spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class, display -> {
            display.setItemStack(item); // Set the item to display
            display.setGravity(false);  // Prevent it from falling
            display.setPersistent(false); // Ensure it doesn't persist when the chunk unloads
            display.setInvulnerable(true);

            // Store the UUID in the persistent data container
            NamespacedKey key = new NamespacedKey(plugin, "floor_item_uuid");
            PersistentDataContainer dataContainer = display.getPersistentDataContainer();
            dataContainer.set(key, PersistentDataType.STRING, uniqueId.toString());
        });
    }

    public Location getLocation() {
        return location;
    }

    public ItemStack getItem() {
        return item;
    }

    public boolean isPickedUp() {
        return isPickedUp;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public ItemDisplay getItemDisplay() {
        return itemDisplay;
    }

    public void markAsPickedUp() {
        this.isPickedUp = true;
        if (itemDisplay != null) {
            itemDisplay.remove();
        }
    }

    /**
     * Handles the pickup mechanic. Can be overridden by subclasses.
     */
    public void handlePickup(Player player) {
        // Default behavior: add the item to the player's inventory
        player.getInventory().addItem(item);
        player.sendMessage("You picked up " + item.getAmount() + "x " + item.getType().name() + "!");
        markAsPickedUp();
    }
}