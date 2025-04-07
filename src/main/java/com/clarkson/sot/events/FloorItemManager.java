package com.clarkson.sot.events;

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

    public FloorItemManager(Plugin plugin) {

    }

    public void addFloorItem(FloorItem floorItem) {
        floorItems.put(floorItem.getUniqueId(), floorItem);
    }

    public void removeFloorItem(UUID uuid) {
        floorItems.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        
    }
}