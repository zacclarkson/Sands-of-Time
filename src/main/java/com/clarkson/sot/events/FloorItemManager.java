package com.clarkson.sot.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import com.clarkson.sot.entities.FloorItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FloorItemManager implements Listener {

    private final Map<UUID, FloorItem> floorItems = new HashMap<>();
    private final Plugin plugin;

    public FloorItemManager(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void addFloorItem(FloorItem floorItem) {
        floorItems.put(floorItem.getUniqueId(), floorItem);
    }

    public void removeFloorItem(UUID uuid) {
        floorItems.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        for (FloorItem floorItem : floorItems.values()) {
            if (floorItem.isPickedUp()) continue;

            ItemDisplay itemDisplay = floorItem.getItemDisplay();
            if (itemDisplay == null) continue;

            double distance = playerLocation.distance(itemDisplay.getLocation());
            if (distance <= 1.5) {
                // Delegate the pickup behavior to the FloorItem instance
                floorItem.handlePickup(player);

                // Remove the item from the manager
                removeFloorItem(floorItem.getUniqueId());
                break; // Exit the loop since the player can only pick up one item at a time
            }
        }
    }
}