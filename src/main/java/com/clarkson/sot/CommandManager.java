package com.clarkson.sot;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CommandManager implements CommandExecutor {

    private final DungeonManager dungeonManager;
    private final Door door; // Door object needs to be final if it's initialized via constructor.
    private ArrayList<DoorClone> doorClones = new ArrayList<>();
    private Plugin plugin;

    public CommandManager(DungeonManager dungeonManager, Door door, Plugin plugin) {
        this.dungeonManager = dungeonManager;
        this.door = door;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("generatedungeon")) {
            try {
                dungeonManager.generateDungeon(player.getLocation());
                player.sendMessage("Dungeon generated successfully!");
            } catch (Exception e) {
                player.sendMessage("An error occurred while generating the dungeon.");
                e.printStackTrace();
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("cloneDoor")) {
            if (door == null) {
                player.sendMessage("Door has not been initialized.");
                return true;
            }

            System.out.println("Your location: " + player.getLocation());
            DoorClone newDoor = door.cloneByLocation(new Location(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
            doorClones.add(newDoor);
            plugin.getServer().getPluginManager().registerEvents(newDoor, plugin);
            player.sendMessage("Door cloned to your location!");

            return true;
        }

        return false; // Return false if no known commands were executed
    }
}
