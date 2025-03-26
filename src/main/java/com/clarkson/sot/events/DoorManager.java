package com.clarkson.sot.events;

import com.clarkson.sot.entities.Door;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoorManager implements Listener {

    private final Map<UUID, Door> doors = new HashMap<>();
    private final Plugin plugin;

    public DoorManager(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void addDoor(Door door) {
        doors.put(door.getDoorId(), door);
    }

    public void removeDoor(Door door) {
        doors.remove(door.getDoorId());
    }

    private Door getDoorByLock(Block block) {
        PersistentDataContainer dataContainer = block.getChunk().getPersistentDataContainer();
        String doorIdString = dataContainer.get(new NamespacedKey(plugin, "door_id"), PersistentDataType.STRING);
        if (doorIdString == null) return null;

        UUID doorId = UUID.fromString(doorIdString);
        return doors.get(doorId);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Find the door associated with the clicked block
        Door door = getDoorByLock(clickedBlock);
        if (door == null) return;

        // Delegate the interaction to the door
        Player player = event.getPlayer();
        door.onPlayerInteract(player);
    }
}